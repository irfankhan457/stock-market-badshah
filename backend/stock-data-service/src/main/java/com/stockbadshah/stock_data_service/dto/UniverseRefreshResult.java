package com.stockbadshah.stock_data_service.dto;

import java.util.List;

public record UniverseRefreshResult(
        String universe,
        int total,
        int loaded,
        int failed,
        List<LiveRefreshResult> results
) {
}
