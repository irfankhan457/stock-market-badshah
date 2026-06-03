package com.stockbadshah.stock_data_service.controller;

import com.stockbadshah.stock_data_service.dto.CandleResponse;
import com.stockbadshah.stock_data_service.dto.LiveRefreshResult;
import com.stockbadshah.stock_data_service.dto.UniverseRefreshResult;
import com.stockbadshah.stock_data_service.entity.StockEntity;
import com.stockbadshah.stock_data_service.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping
    public StockEntity save(@RequestBody StockEntity stock) {
        return stockService.save(stock);
    }

    @PostMapping("/bulk")
    public List<StockEntity> saveBulk(@RequestBody List<StockEntity> stocks) {
        return stockService.saveAll(stocks);
    }

    @GetMapping
    public List<StockEntity> getAll() {
        return stockService.getAllStocks();
    }

    @GetMapping("/page")
    public Page<StockEntity> getPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "") String symbol,
            @RequestParam(defaultValue = "symbol") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return stockService.getStocksPage(page, size, symbol, sortBy, direction);
    }

    @GetMapping("/meta/symbols")
    public List<String> getSymbols() {
        return stockService.getSymbols();
    }

    @GetMapping("/universe/nifty100")
    public List<String> getNifty100Symbols() {
        return stockService.getNifty100Symbols();
    }

    @GetMapping("/universe/nifty500")
    public List<String> getNifty500Symbols() {
        return stockService.getNifty500Symbols();
    }

    @GetMapping("/{symbol}")
    public List<StockEntity> getBySymbol(@PathVariable String symbol) {
        return stockService.getBySymbol(symbol);
    }

    @GetMapping("/{symbol}/candles")
    public List<CandleResponse> getCandles(@PathVariable String symbol) {
        return stockService.getCandles(symbol);
    }

    @PostMapping("/live/{symbol}/refresh")
    public List<StockEntity> refreshLiveCandles(@PathVariable String symbol) {
        return stockService.refreshLiveCandles(symbol);
    }

    @PostMapping("/live/{symbol}/refresh-summary")
    public LiveRefreshResult refreshLiveCandlesSummary(@PathVariable String symbol) {
        return stockService.refreshLiveCandlesSummary(symbol);
    }

    @PostMapping("/live/universe/{universe}/refresh")
    public UniverseRefreshResult refreshUniverse(
            @PathVariable String universe,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return stockService.refreshUniverse(universe, force);
    }

    @GetMapping("/universe/{universe}/saved-status")
    public UniverseRefreshResult getSavedUniverseStatus(@PathVariable String universe) {
        return stockService.getSavedUniverseStatus(universe);
    }
}
