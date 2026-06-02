package com.stockbadshah.stock_data_service.controller;

import com.stockbadshah.stock_data_service.dto.CandleResponse;
import com.stockbadshah.stock_data_service.entity.StockEntity;
import com.stockbadshah.stock_data_service.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stocks")
@CrossOrigin(origins = "*")
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

    @GetMapping("/meta/symbols")
    public List<String> getSymbols() {
        return stockService.getSymbols();
    }

    @GetMapping("/{symbol}")
    public List<StockEntity> getBySymbol(@PathVariable String symbol) {
        return stockService.getBySymbol(symbol);
    }

    @GetMapping("/{symbol}/candles")
    public List<CandleResponse> getCandles(@PathVariable String symbol) {
        return stockService.getCandles(symbol);
    }
}
