package com.stockbadshah.stock_data_service.service;

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

    public List<StockEntity> getAllStocks() {
        return repository.findAll();
    }
}