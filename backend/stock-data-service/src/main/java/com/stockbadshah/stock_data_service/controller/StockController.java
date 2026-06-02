package com.stockbadshah.stock_data_service.controller;

import com.stockbadshah.stock_data_service.entity.StockEntity;
import com.stockbadshah.stock_data_service.service.StockService;
import lombok.RequiredArgsConstructor;
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

    @GetMapping
    public List<StockEntity> getAll() {
        return stockService.getAllStocks();
    }
}