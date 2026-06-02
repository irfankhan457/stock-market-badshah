package com.stockbadshah.stock_data_service.service;

import com.stockbadshah.stock_data_service.dto.CandleResponse;
import com.stockbadshah.stock_data_service.entity.StockEntity;
import com.stockbadshah.stock_data_service.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository repository;

    public StockEntity save(StockEntity stock) {
        return repository.save(stock);
    }

    public List<StockEntity> saveAll(List<StockEntity> stocks) {
        return repository.saveAll(stocks);
    }

    public List<StockEntity> getAllStocks() {
        return repository.findAll();
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
}
