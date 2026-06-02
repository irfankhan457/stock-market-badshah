package com.stockbadshah.stock_data_service.repository;

import com.stockbadshah.stock_data_service.entity.StockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<StockEntity, Long> {
}