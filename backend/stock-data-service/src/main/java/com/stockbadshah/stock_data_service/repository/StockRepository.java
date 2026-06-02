package com.stockbadshah.stock_data_service.repository;

import com.stockbadshah.stock_data_service.entity.StockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StockRepository extends JpaRepository<StockEntity, Long> {
    List<StockEntity> findBySymbolIgnoreCaseOrderByStockDateAsc(String symbol);

    StockEntity findTopBySymbolIgnoreCaseOrderByStockDateDesc(String symbol);

    long countBySymbolIgnoreCase(String symbol);

    void deleteBySymbolIgnoreCase(String symbol);

    @Query("select distinct upper(s.symbol) from StockEntity s where s.symbol is not null order by upper(s.symbol)")
    List<String> findDistinctSymbols();
}
