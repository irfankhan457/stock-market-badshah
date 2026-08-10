package com.stockbadshah.stock_data_service.repository;

import com.stockbadshah.stock_data_service.entity.StockEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface StockRepository extends JpaRepository<StockEntity, Long> {
    interface SymbolSnapshot {
        String getSymbol();

        long getRowCount();

        LocalDate getLatestDate();
    }

    Page<StockEntity> findBySymbolContainingIgnoreCase(String symbol, Pageable pageable);

    List<StockEntity> findBySymbolOrderByStockDateAsc(String symbol);

    @Modifying
    @Query("delete from StockEntity s where s.symbol = :symbol")
    void deleteBySymbol(@Param("symbol") String symbol);

    @Query("select distinct upper(s.symbol) from StockEntity s where s.symbol is not null order by upper(s.symbol)")
    List<String> findDistinctSymbols();

    @Query("""
            select s.symbol as symbol, count(s) as rowCount, max(s.stockDate) as latestDate
            from StockEntity s
            where s.symbol in :symbols
            group by s.symbol
            having count(s.stockDate) > 0
            """)
    List<SymbolSnapshot> findSymbolSnapshots(@Param("symbols") Collection<String> symbols);
}
