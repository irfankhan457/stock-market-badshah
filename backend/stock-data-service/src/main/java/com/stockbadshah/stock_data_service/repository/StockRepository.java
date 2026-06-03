package com.stockbadshah.stock_data_service.repository;

import com.stockbadshah.stock_data_service.entity.StockEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;

public interface StockRepository extends JpaRepository<StockEntity, Long> {
    Page<StockEntity> findBySymbolContainingIgnoreCase(String symbol, Pageable pageable);

    List<StockEntity> findBySymbolIgnoreCaseOrderByStockDateAsc(String symbol);

    StockEntity findTopBySymbolIgnoreCaseOrderByStockDateDesc(String symbol);

    long countBySymbolIgnoreCase(String symbol);

    void deleteBySymbolIgnoreCase(String symbol);

    @Query("select distinct upper(s.symbol) from StockEntity s where s.symbol is not null order by upper(s.symbol)")
    List<String> findDistinctSymbols();

    @Query("select distinct upper(s.symbol) from StockEntity s where upper(s.symbol) in :symbols and s.stockDate is not null order by upper(s.symbol)")
    List<String> findSavedSymbols(@Param("symbols") Collection<String> symbols);
}
