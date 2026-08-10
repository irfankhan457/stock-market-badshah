package com.stockbadshah.stock_data_service.service;

import com.stockbadshah.stock_data_service.dto.LiveRefreshResult;
import com.stockbadshah.stock_data_service.dto.UniverseRefreshResult;
import com.stockbadshah.stock_data_service.entity.StockEntity;
import com.stockbadshah.stock_data_service.repository.StockRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockServiceUniverseLookupTests {

    private static final String NIFTY_50_CSV = """
            Company Name,Industry,Symbol,Series,ISIN Code
            Reliance Industries Ltd.,Oil Gas & Consumable Fuels,RELIANCE,EQ,INE002A01018
            Tata Consultancy Services Ltd.,Information Technology,TCS,EQ,INE467B01029
            """;

    private static final String NIFTY_NEXT_50_CSV = """
            Company Name,Industry,Symbol,Series,ISIN Code
            ABB India Ltd.,Capital Goods,ABB,EQ,INE117A01022
            Adani Energy Solutions Ltd.,Power,ADANIENSOL,EQ,INE931S01010
            """;

    private HttpServer server;
    private StockRepository repository;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        repository = mock(StockRepository.class);
        when(repository.findDistinctSymbols()).thenReturn(List.of());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void defaultNseUrlsUseTheWorkingArchiveHost() {
        assertThat(StockService.DEFAULT_NIFTY_50_LIST_URL)
                .isEqualTo("https://nsearchives.nseindia.com/content/indices/ind_nifty50list.csv");
        assertThat(StockService.DEFAULT_NIFTY_NEXT_50_LIST_URL)
                .isEqualTo("https://nsearchives.nseindia.com/content/indices/ind_niftynext50list.csv");
        assertThat(StockService.DEFAULT_NIFTY_500_LIST_URL)
                .isEqualTo("https://nsearchives.nseindia.com/content/indices/ind_nifty500list.csv");
    }

    @Test
    void triesOfficialAlternateAndSendsBrowserUserAgent() {
        AtomicReference<String> userAgent = new AtomicReference<>();
        respond("/primary-50", 503, "temporarily unavailable", null);
        respond("/alternate-50", 200, NIFTY_50_CSV, userAgent);
        respond("/primary-next-50", 200, NIFTY_NEXT_50_CSV, null);

        StockService service = service(
                url("/primary-50"),
                url("/primary-next-50"),
                url("/unused-500"),
                url("/alternate-50"),
                url("/unused-next-50"),
                url("/unused-500-alternate")
        );

        assertThat(service.getNifty100Symbols())
                .containsExactly("RELIANCE", "TCS", "ABB", "ADANIENSOL");
        assertThat(userAgent.get()).startsWith("Mozilla/5.0");
    }

    @Test
    void fallsBackToDistinctSavedSymbolsWhenBothNseSourcesFail() {
        respond("/unavailable", 503, "temporarily unavailable", null);
        when(repository.findDistinctSymbols()).thenReturn(List.of("TCS", "RELIANCE", "TCS"));

        StockService service = serviceWithEveryIndexUrl(url("/unavailable"));

        assertThat(service.getNifty500Symbols()).containsExactly("TCS", "RELIANCE");
    }

    @Test
    void keepsLastKnownUniverseAcrossATransientNseFailure() {
        AtomicBoolean available = new AtomicBoolean(true);
        server.createContext("/changing", exchange -> {
            byte[] body = (available.get() ? NIFTY_50_CSV : "temporarily unavailable").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(available.get() ? 200 : 503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        StockService service = serviceWithEveryIndexUrl(url("/changing"));
        assertThat(service.getNifty500Symbols()).containsExactly("RELIANCE", "TCS");

        available.set(false);

        assertThat(service.getNifty500Symbols()).containsExactly("RELIANCE", "TCS");
    }

    @Test
    void returnsServiceUnavailableWhenRemoteAndSavedFallbacksAreEmpty() {
        respond("/unavailable", 503, "temporarily unavailable", null);
        StockService service = serviceWithEveryIndexUrl(url("/unavailable"));

        assertThatThrownBy(service::getNifty500Symbols)
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getReason()).contains("temporarily unavailable")
                            .contains("no saved market symbols");
                });
    }

    @Test
    void getsSavedUniverseStatusWithOneGroupedCountQuery() {
        respond("/nifty-500", 200, NIFTY_50_CSV, null);
        StockRepository.SymbolSnapshot relianceCount = symbolSnapshot("RELIANCE", 252, LocalDate.now());
        when(repository.findSymbolSnapshots(List.of("RELIANCE", "TCS")))
                .thenReturn(List.of(relianceCount));

        UniverseRefreshResult status = serviceWithEveryIndexUrl(url("/nifty-500"))
                .getSavedUniverseStatus("nifty500");

        assertThat(status.universe()).isEqualTo("nifty500");
        assertThat(status.total()).isEqualTo(2);
        assertThat(status.loaded()).isEqualTo(1);
        assertThat(status.failed()).isEqualTo(1);
        assertThat(status.results()).containsExactly(
                new LiveRefreshResult(
                        "RELIANCE", true, 252, "Saved market data is available."
                ),
                new LiveRefreshResult(
                        "TCS", false, 0, "No saved market data found. Update prices from the Prices page."
                )
        );
        verify(repository).findSymbolSnapshots(List.of("RELIANCE", "TCS"));
    }

    @Test
    void normalizesSavedUniverseFallbackBeforeTheGroupedCountQuery() {
        respond("/unavailable-status", 503, "temporarily unavailable", null);
        when(repository.findDistinctSymbols()).thenReturn(List.of("reliance.ns"));
        StockRepository.SymbolSnapshot relianceCount = symbolSnapshot("RELIANCE", 12, LocalDate.now());
        when(repository.findSymbolSnapshots(List.of("RELIANCE")))
                .thenReturn(List.of(relianceCount));

        UniverseRefreshResult status = serviceWithEveryIndexUrl(url("/unavailable-status"))
                .getSavedUniverseStatus("nifty500");

        assertThat(status.total()).isEqualTo(1);
        assertThat(status.loaded()).isEqualTo(1);
        assertThat(status.failed()).isZero();
        assertThat(status.results()).containsExactly(
                new LiveRefreshResult("RELIANCE", true, 12, "Saved market data is available.")
        );
        verify(repository).findSymbolSnapshots(List.of("RELIANCE"));
    }

    @Test
    void reusesCurrentUniverseWithOneSnapshotQueryAndNoDownloads() {
        respond("/nifty-500-current", 200, NIFTY_50_CSV, null);
        LocalDate safelyCurrentDate = LocalDate.of(2099, 1, 1);
        StockRepository.SymbolSnapshot reliance = symbolSnapshot("RELIANCE", 252, safelyCurrentDate);
        StockRepository.SymbolSnapshot tcs = symbolSnapshot("TCS", 251, safelyCurrentDate);
        when(repository.findSymbolSnapshots(List.of("RELIANCE", "TCS"))).thenReturn(List.of(
                reliance,
                tcs
        ));

        UniverseRefreshResult result = serviceWithEveryIndexUrl(url("/nifty-500-current"))
                .refreshUniverse("nifty500", false);

        assertThat(result.loaded()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        assertThat(result.results()).containsExactly(
                new LiveRefreshResult("RELIANCE", true, 252, "Recent market data is already loaded."),
                new LiveRefreshResult("TCS", true, 251, "Recent market data is already loaded.")
        );
        verify(repository).findSymbolSnapshots(List.of("RELIANCE", "TCS"));
        verify(repository, never()).deleteBySymbol(anyString());
    }

    @Test
    void usesTheLastCompletedTradingDayBeforeTheMarketDataCutoff() {
        ZoneId marketZone = ZoneId.of("Asia/Kolkata");

        assertThat(StockService.latestExpectedMarketDate(
                ZonedDateTime.of(2026, 7, 20, 4, 37, 0, 0, marketZone)
        )).isEqualTo(LocalDate.of(2026, 7, 17));
        assertThat(StockService.latestExpectedMarketDate(
                ZonedDateTime.of(2026, 7, 20, 16, 1, 0, 0, marketZone)
        )).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(StockService.latestExpectedMarketDate(
                ZonedDateTime.of(2026, 7, 19, 18, 0, 0, 0, marketZone)
        )).isEqualTo(LocalDate.of(2026, 7, 17));
    }

    @Test
    void clampsConfiguredRefreshConcurrency() {
        assertThat(StockService.clampRefreshThreads(0)).isEqualTo(1);
        assertThat(StockService.clampRefreshThreads(16)).isEqualTo(16);
        assertThat(StockService.clampRefreshThreads(500)).isEqualTo(32);
    }

    @Test
    void normalizesSymbolsForIndexedWritesAndReads() {
        StockService service = serviceWithEveryIndexUrl(url("/unused-index"));
        StockEntity stock = StockEntity.builder().symbol(" reliance.ns ").build();

        service.save(stock);
        service.getBySymbol(" reliance.bo ");

        assertThat(stock.getSymbol()).isEqualTo("RELIANCE");
        verify(repository).save(stock);
        verify(repository).findBySymbolOrderByStockDateAsc("RELIANCE");
    }

    private StockService serviceWithEveryIndexUrl(String indexUrl) {
        return service(indexUrl, indexUrl, indexUrl, indexUrl, indexUrl, indexUrl);
    }

    private StockService service(
            String nifty50Url,
            String niftyNext50Url,
            String nifty500Url,
            String nifty50FallbackUrl,
            String niftyNext50FallbackUrl,
            String nifty500FallbackUrl
    ) {
        return new StockService(
                repository,
                RestClient.builder(),
                mock(TransactionTemplate.class),
                url("/unused-yahoo"),
                nifty50Url,
                niftyNext50Url,
                nifty500Url,
                nifty50FallbackUrl,
                niftyNext50FallbackUrl,
                nifty500FallbackUrl,
                16
        );
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private StockRepository.SymbolSnapshot symbolSnapshot(String symbol, long rowCount, LocalDate latestDate) {
        StockRepository.SymbolSnapshot count = mock(StockRepository.SymbolSnapshot.class);
        when(count.getSymbol()).thenReturn(symbol);
        when(count.getRowCount()).thenReturn(rowCount);
        when(count.getLatestDate()).thenReturn(latestDate);
        return count;
    }

    private void respond(String path, int status, String response, AtomicReference<String> userAgent) {
        server.createContext(path, exchange -> {
            if (userAgent != null) {
                userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            }
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/csv");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
    }
}
