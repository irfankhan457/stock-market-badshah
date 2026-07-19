package com.stockbadshah.stock_data_service.service;

import com.stockbadshah.stock_data_service.dto.CandleResponse;
import com.stockbadshah.stock_data_service.dto.LiveRefreshResult;
import com.stockbadshah.stock_data_service.dto.UniverseRefreshResult;
import com.stockbadshah.stock_data_service.entity.StockEntity;
import com.stockbadshah.stock_data_service.repository.StockRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class StockService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private static final int LIVE_REFRESH_THREADS = 10;
    private static final int RECENT_SAVED_DATA_DAYS = 5;
    private static final String LIVE_HISTORY_RANGE = "1y";
    private static final String MARKET_DATA_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0 Safari/537.36";

    static final String DEFAULT_NIFTY_50_LIST_URL = "https://nsearchives.nseindia.com/content/indices/ind_nifty50list.csv";
    static final String DEFAULT_NIFTY_NEXT_50_LIST_URL = "https://nsearchives.nseindia.com/content/indices/ind_niftynext50list.csv";
    static final String DEFAULT_NIFTY_500_LIST_URL = "https://nsearchives.nseindia.com/content/indices/ind_nifty500list.csv";
    static final String DEFAULT_NIFTY_50_FALLBACK_LIST_URL = "https://www.niftyindices.com/IndexConstituent/ind_nifty50list.csv";
    static final String DEFAULT_NIFTY_NEXT_50_FALLBACK_LIST_URL = "https://www.niftyindices.com/IndexConstituent/ind_niftynext50list.csv";
    static final String DEFAULT_NIFTY_500_FALLBACK_LIST_URL = "https://www.niftyindices.com/IndexConstituent/ind_nifty500list.csv";

    private final StockRepository repository;
    private final RestClient restClient;
    private final TransactionTemplate transactionTemplate;
    private final String yahooChartUrl;
    private final String nifty50ListUrl;
    private final String niftyNext50ListUrl;
    private final String nifty500ListUrl;
    private final String nifty50FallbackListUrl;
    private final String niftyNext50FallbackListUrl;
    private final String nifty500FallbackListUrl;
    private volatile List<String> lastKnownNifty100Symbols = List.of();
    private volatile List<String> lastKnownNifty500Symbols = List.of();

    public StockService(
            StockRepository repository,
            RestClient.Builder restClientBuilder,
            TransactionTemplate transactionTemplate,
            @Value("${market-data.yahoo-chart-url:https://query1.finance.yahoo.com/v8/finance/chart}") String yahooChartUrl,
            @Value("${market-data.nifty50-list-url:" + DEFAULT_NIFTY_50_LIST_URL + "}") String nifty50ListUrl,
            @Value("${market-data.nifty-next50-list-url:" + DEFAULT_NIFTY_NEXT_50_LIST_URL + "}") String niftyNext50ListUrl,
            @Value("${market-data.nifty500-list-url:" + DEFAULT_NIFTY_500_LIST_URL + "}") String nifty500ListUrl,
            @Value("${market-data.nifty50-fallback-list-url:" + DEFAULT_NIFTY_50_FALLBACK_LIST_URL + "}") String nifty50FallbackListUrl,
            @Value("${market-data.nifty-next50-fallback-list-url:" + DEFAULT_NIFTY_NEXT_50_FALLBACK_LIST_URL + "}") String niftyNext50FallbackListUrl,
            @Value("${market-data.nifty500-fallback-list-url:" + DEFAULT_NIFTY_500_FALLBACK_LIST_URL + "}") String nifty500FallbackListUrl
    ) {
        this.repository = repository;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(4));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.transactionTemplate = transactionTemplate;
        this.yahooChartUrl = yahooChartUrl;
        this.nifty50ListUrl = nifty50ListUrl;
        this.niftyNext50ListUrl = niftyNext50ListUrl;
        this.nifty500ListUrl = nifty500ListUrl;
        this.nifty50FallbackListUrl = nifty50FallbackListUrl;
        this.niftyNext50FallbackListUrl = niftyNext50FallbackListUrl;
        this.nifty500FallbackListUrl = nifty500FallbackListUrl;
    }

    public StockEntity save(StockEntity stock) {
        return repository.save(stock);
    }

    public List<StockEntity> saveAll(List<StockEntity> stocks) {
        return repository.saveAll(stocks);
    }

    public List<StockEntity> getAllStocks() {
        return repository.findAll();
    }

    public Page<StockEntity> getStocksPage(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public Page<StockEntity> getStocksPage(int page, int size, String symbolSearch, String sortBy, String direction) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(10, size));
        String safeSortBy = switch (sortBy == null ? "" : sortBy.trim()) {
            case "symbol" -> "symbol";
            case "stockDate" -> "stockDate";
            case "volume" -> "volume";
            default -> "symbol";
        };
        Sort.Direction safeDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(safeDirection, safeSortBy));
        String search = symbolSearch == null ? "" : symbolSearch.trim();
        if (search.isBlank()) {
            return repository.findAll(pageable);
        }
        return repository.findBySymbolContainingIgnoreCase(search, pageable);
    }

    public List<String> getSymbols() {
        return repository.findDistinctSymbols();
    }

    public List<StockEntity> getBySymbol(String symbol) {
        return repository.findBySymbolIgnoreCaseOrderByStockDateAsc(symbol);
    }

    public List<CandleResponse> getCandles(String symbol) {
        return getBySymbol(symbol).stream()
                .filter(stock -> stock.getStockDate() != null && stock.getCurrentPrice() != null)
                .map(stock -> new CandleResponse(stock.getStockDate(), stock.getCurrentPrice()))
                .toList();
    }

    public List<String> getNifty100Symbols() {
        return resolveUniverseSymbols("nifty100", 100, () -> {
            Set<String> symbols = new LinkedHashSet<>();
            symbols.addAll(fetchIndexSymbols(nifty50ListUrl, nifty50FallbackListUrl));
            symbols.addAll(fetchIndexSymbols(niftyNext50ListUrl, niftyNext50FallbackListUrl));
            return symbols.stream().limit(100).toList();
        });
    }

    public List<String> getNifty500Symbols() {
        return resolveUniverseSymbols(
                "nifty500",
                500,
                () -> fetchIndexSymbols(nifty500ListUrl, nifty500FallbackListUrl).stream().limit(500).toList()
        );
    }

    @Transactional
    public List<StockEntity> refreshLiveCandles(String symbol) {
        return fetchAndSaveLiveCandles(symbol);
    }

    public LiveRefreshResult refreshLiveCandlesSummary(String symbol) {
        return refreshLiveCandlesSummary(symbol, false);
    }

    public LiveRefreshResult refreshLiveCandlesSummary(String symbol, boolean forceRefresh) {
        String appSymbol = normalizeAppSymbol(symbol);
        StockEntity latestSaved = repository.findTopBySymbolIgnoreCaseOrderByStockDateDesc(appSymbol);
        try {
            if (!forceRefresh && hasRecentSavedData(latestSaved)) {
                return new LiveRefreshResult(appSymbol, true, (int) repository.countBySymbolIgnoreCase(appSymbol), "Recent market data is already loaded.");
            }
            int rowsSaved = fetchAndSaveLiveCandles(appSymbol).size();
            return new LiveRefreshResult(appSymbol, true, rowsSaved, "Latest one-year market data saved.");
        } catch (Exception exception) {
            if (latestSaved != null) {
                return new LiveRefreshResult(appSymbol, true, (int) repository.countBySymbolIgnoreCase(appSymbol), "Using saved market data because live market data is busy right now.");
            }
            return new LiveRefreshResult(appSymbol, false, 0, "Could not load live market data right now.");
        }
    }

    public UniverseRefreshResult refreshUniverse(String universe) {
        return refreshUniverse(universe, false);
    }

    public UniverseRefreshResult refreshUniverse(String universe, boolean forceRefresh) {
        String normalizedUniverse = universe == null ? "nifty100" : universe.trim().toLowerCase();
        List<String> symbols = "nifty500".equals(normalizedUniverse) ? getNifty500Symbols() : getNifty100Symbols();

        ExecutorService executor = Executors.newFixedThreadPool(LIVE_REFRESH_THREADS);
        try {
            List<CompletableFuture<LiveRefreshResult>> futures = symbols.stream()
                    .map(symbol -> CompletableFuture.supplyAsync(() -> refreshLiveCandlesSummary(symbol, forceRefresh), executor))
                    .toList();

            List<LiveRefreshResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            long loaded = results.stream().filter(LiveRefreshResult::loaded).count();
            return new UniverseRefreshResult(normalizedUniverse, symbols.size(), (int) loaded, results.size() - (int) loaded, results);
        } finally {
            executor.shutdown();
        }
    }

    public UniverseRefreshResult getSavedUniverseStatus(String universe) {
        String normalizedUniverse = universe == null ? "nifty100" : universe.trim().toLowerCase();
        List<String> symbols = "nifty500".equals(normalizedUniverse) ? getNifty500Symbols() : getNifty100Symbols();
        List<String> normalizedSymbols = symbols.stream()
                .map(this::normalizeAppSymbol)
                .toList();
        Map<String, Long> rowCounts = repository.findSymbolRowCounts(normalizedSymbols).stream()
                .collect(Collectors.toMap(
                        row -> normalizeAppSymbol(row.getSymbol()),
                        StockRepository.SymbolRowCount::getRowCount,
                        Long::sum
                ));
        List<LiveRefreshResult> results = normalizedSymbols.stream()
                .map(normalizedSymbol -> {
                    Long rowCount = rowCounts.get(normalizedSymbol);
                    boolean loaded = rowCount != null;
                    int rows = loaded ? rowCount.intValue() : 0;
                    String message = loaded ? "Saved market data is available." : "No saved market data found. Update prices from the Prices page.";
                    return new LiveRefreshResult(normalizedSymbol, loaded, rows, message);
                })
                .toList();
        long loaded = results.stream().filter(LiveRefreshResult::loaded).count();
        return new UniverseRefreshResult(normalizedUniverse, symbols.size(), (int) loaded, results.size() - (int) loaded, results);
    }

    private List<StockEntity> fetchAndSaveLiveCandles(String symbol) {
        String appSymbol = normalizeAppSymbol(symbol);
        String providerSymbol = toYahooSymbol(symbol);
        JsonNode chart = restClient.get()
                .uri(URI.create(yahooChartUrl + "/" + encodePathSegment(providerSymbol) + "?range=" + LIVE_HISTORY_RANGE + "&interval=1d"))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json,text/plain,*/*")
                .retrieve()
                .body(JsonNode.class);

        List<StockEntity> rows = toStockRows(appSymbol, chart);
        if (rows.isEmpty()) {
            throw new IllegalStateException("We could not find recent market data for " + appSymbol + ".");
        }

        return transactionTemplate.execute(status -> {
            repository.deleteBySymbolIgnoreCase(appSymbol);
            return repository.saveAll(rows);
        });
    }

    private boolean hasRecentSavedData(StockEntity latestSaved) {
        if (latestSaved == null || latestSaved.getStockDate() == null) {
            return false;
        }
        LocalDate oldestAllowedDate = LocalDate.now(MARKET_ZONE).minusDays(RECENT_SAVED_DATA_DAYS);
        return !latestSaved.getStockDate().isBefore(oldestAllowedDate);
    }

    private List<StockEntity> toStockRows(String appSymbol, JsonNode chart) {
        JsonNode result = chart == null ? null : chart.at("/chart/result/0");
        JsonNode timestamps = result == null ? null : result.path("timestamp");
        JsonNode closes = result == null ? null : result.at("/indicators/quote/0/close");
        JsonNode volumes = result == null ? null : result.at("/indicators/quote/0/volume");

        if (timestamps == null || closes == null || !timestamps.isArray() || !closes.isArray()) {
            return List.of();
        }

        List<StockEntity> rows = new ArrayList<>();
        int length = Math.min(timestamps.size(), closes.size());
        for (int i = 0; i < length; i++) {
            JsonNode close = closes.get(i);
            if (close == null || close.isNull()) {
                continue;
            }

            BigDecimal volume = null;
            if (volumes != null && volumes.isArray() && i < volumes.size() && !volumes.get(i).isNull()) {
                volume = BigDecimal.valueOf(volumes.get(i).asLong());
            }

            rows.add(StockEntity.builder()
                    .symbol(appSymbol)
                    .companyName(appSymbol)
                    .currentPrice(BigDecimal.valueOf(close.asDouble()))
                    .volume(volume)
                    .stockDate(Instant.ofEpochSecond(timestamps.get(i).asLong()).atZone(MARKET_ZONE).toLocalDate())
                    .build());
        }
        return rows;
    }

    private List<String> resolveUniverseSymbols(String universe, int limit, Supplier<List<String>> remoteLoader) {
        RuntimeException remoteFailure;
        try {
            List<String> remoteSymbols = normalizeSymbols(remoteLoader.get(), limit);
            if (remoteSymbols.isEmpty()) {
                throw new IllegalStateException("NSE returned an empty " + universe + " constituent list.");
            }
            rememberUniverseSymbols(universe, remoteSymbols);
            return remoteSymbols;
        } catch (RuntimeException exception) {
            remoteFailure = exception;
        }

        List<String> lastKnownSymbols = "nifty500".equals(universe)
                ? lastKnownNifty500Symbols
                : lastKnownNifty100Symbols;
        if (!lastKnownSymbols.isEmpty()) {
            return lastKnownSymbols;
        }

        List<String> savedSymbols = normalizeSymbols(repository.findDistinctSymbols(), limit);
        if (!savedSymbols.isEmpty()) {
            return savedSymbols;
        }

        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The NSE " + universe + " constituent list is temporarily unavailable, and no saved market symbols are available yet.",
                remoteFailure
        );
    }

    private void rememberUniverseSymbols(String universe, List<String> symbols) {
        if ("nifty500".equals(universe)) {
            lastKnownNifty500Symbols = symbols;
        } else {
            lastKnownNifty100Symbols = symbols;
        }
    }

    private List<String> normalizeSymbols(List<String> symbols, int limit) {
        if (symbols == null) {
            return List.of();
        }
        return symbols.stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(this::normalizeAppSymbol)
                .distinct()
                .limit(limit)
                .toList();
    }

    private List<String> fetchIndexSymbols(String primaryUrl, String fallbackUrl) {
        try {
            return requireIndexSymbols(primaryUrl);
        } catch (RuntimeException primaryFailure) {
            if (primaryUrl.equals(fallbackUrl)) {
                throw primaryFailure;
            }
            try {
                return requireIndexSymbols(fallbackUrl);
            } catch (RuntimeException fallbackFailure) {
                primaryFailure.addSuppressed(fallbackFailure);
                throw primaryFailure;
            }
        }
    }

    private List<String> requireIndexSymbols(String url) {
        List<String> symbols = fetchIndexSymbols(url);
        if (symbols.isEmpty()) {
            throw new IllegalStateException("NSE returned no constituent symbols from " + url + ".");
        }
        return symbols;
    }

    private List<String> fetchIndexSymbols(String url) {
        String csv = restClient.get()
                .uri(url)
                .header("User-Agent", MARKET_DATA_USER_AGENT)
                .header("Accept", "text/csv,text/plain;q=0.9,*/*;q=0.8")
                .retrieve()
                .body(String.class);
        if (csv == null || csv.isBlank()) {
            return List.of();
        }

        String[] lines = csv.split("\\R");
        if (lines.length < 2) {
            return List.of();
        }

        List<String> headers = Arrays.stream(lines[0].split(","))
                .map(this::cleanCsvValue)
                .toList();
        int symbolIndex = headers.indexOf("Symbol");
        if (symbolIndex < 0) {
            return List.of();
        }

        List<String> symbols = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String[] columns = line.split(",", -1);
            if (symbolIndex < columns.length) {
                String symbol = cleanCsvValue(columns[symbolIndex]).toUpperCase();
                if (!symbol.isBlank()) {
                    symbols.add(symbol);
                }
            }
        }
        return symbols;
    }

    private String cleanCsvValue(String value) {
        return value == null ? "" : value.replace("\"", "").trim();
    }

    private String normalizeAppSymbol(String symbol) {
        String normalized = symbol == null || symbol.isBlank() ? "RELIANCE" : symbol.trim().toUpperCase();
        return normalized.replace(".NS", "").replace(".BO", "");
    }

    private String toYahooSymbol(String symbol) {
        String normalized = symbol == null || symbol.isBlank() ? "RELIANCE" : symbol.trim().toUpperCase();
        return normalized.contains(".") ? normalized : normalized + ".NS";
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
