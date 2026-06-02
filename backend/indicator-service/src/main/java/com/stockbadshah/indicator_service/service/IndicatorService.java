package com.stockbadshah.indicator_service.service;

import com.stockbadshah.indicator_service.dto.CandleRequest;
import com.stockbadshah.indicator_service.dto.IndicatorRequest;
import com.stockbadshah.indicator_service.dto.IndicatorResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Service
public class IndicatorService {

	private static final int RSI_PERIOD = 14;
	private static final int SMA_PERIOD = 20;

	public IndicatorResponse calculate(IndicatorRequest request) {
		List<CandleRequest> candles = request.candles() == null
				? List.of()
				: request.candles().stream()
				.sorted(Comparator.comparing(CandleRequest::date))
				.toList();

		if (candles.isEmpty()) {
			return new IndicatorResponse(
					request.symbol().toUpperCase(),
					"NO_DATA",
					null,
					null,
					null,
					null,
					null,
					null,
					"Please send candle date and close price data."
			);
		}

		CandleRequest latest = candles.getLast();
		BigDecimal buyPrice = scale(latest.close());
		BigDecimal rsi = calculateRsi(candles);
		BigDecimal sma20 = calculateSma(candles, SMA_PERIOD);
		String signal = resolveSignal(buyPrice, rsi, sma20);
		BigDecimal target = scale(buyPrice.multiply(BigDecimal.valueOf(1.08)));
		BigDecimal stopLoss = scale(buyPrice.multiply(BigDecimal.valueOf(0.95)));

		return new IndicatorResponse(
				request.symbol().toUpperCase(),
				signal,
				latest.date(),
				buyPrice,
				rsi,
				sma20,
				target,
				stopLoss,
				"Calculated using latest close price, RSI 14, and SMA 20."
		);
	}

	private BigDecimal calculateSma(List<CandleRequest> candles, int period) {
		if (candles.size() < period) {
			return null;
		}

		BigDecimal sum = candles.subList(candles.size() - period, candles.size()).stream()
				.map(CandleRequest::close)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		return scale(sum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP));
	}

	private BigDecimal calculateRsi(List<CandleRequest> candles) {
		if (candles.size() <= RSI_PERIOD) {
			return null;
		}

		BigDecimal gain = BigDecimal.ZERO;
		BigDecimal loss = BigDecimal.ZERO;
		List<CandleRequest> recent = candles.subList(candles.size() - RSI_PERIOD - 1, candles.size());

		for (int i = 1; i < recent.size(); i++) {
			BigDecimal change = recent.get(i).close().subtract(recent.get(i - 1).close());
			if (change.signum() >= 0) {
				gain = gain.add(change);
			} else {
				loss = loss.add(change.abs());
			}
		}

		if (loss.compareTo(BigDecimal.ZERO) == 0) {
			return BigDecimal.valueOf(100);
		}

		BigDecimal averageGain = gain.divide(BigDecimal.valueOf(RSI_PERIOD), 6, RoundingMode.HALF_UP);
		BigDecimal averageLoss = loss.divide(BigDecimal.valueOf(RSI_PERIOD), 6, RoundingMode.HALF_UP);
		BigDecimal relativeStrength = averageGain.divide(averageLoss, 6, RoundingMode.HALF_UP);
		BigDecimal rsi = BigDecimal.valueOf(100).subtract(
				BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(relativeStrength), 6, RoundingMode.HALF_UP)
		);

		return scale(rsi);
	}

	private String resolveSignal(BigDecimal buyPrice, BigDecimal rsi, BigDecimal sma20) {
		if (rsi == null || sma20 == null) {
			return "WAIT";
		}
		if (rsi.compareTo(BigDecimal.valueOf(30)) <= 0 && buyPrice.compareTo(sma20) >= 0) {
			return "BUY";
		}
		if (rsi.compareTo(BigDecimal.valueOf(70)) >= 0) {
			return "SELL";
		}
		return "HOLD";
	}

	private BigDecimal scale(BigDecimal value) {
		return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
	}
}
