import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Title } from '@angular/platform-browser';

type ServiceState = 'online' | 'checking' | 'offline';

interface Candle {
  date: string;
  close: number;
}

interface StockRow {
  id?: number;
  symbol: string;
  companyName?: string;
  currentPrice?: number;
  marketCap?: number;
  peRatio?: number;
  roe?: number;
  debtToEquity?: number;
  volume?: number;
  stockDate?: string;
}

interface IndicatorResponse {
  symbol: string;
  signal: string;
  buyDate: string | null;
  buyPrice: number | null;
  rsi: number | null;
  sma20: number | null;
  target: number | null;
  stopLoss: number | null;
  result: string;
  candlesCount?: number;
  source?: string;
}

interface StrategyResponse {
  symbol: string;
  decision: string;
  confidence: string;
  buyDate: string | null;
  buyPrice: number | null;
  target: number | null;
  stopLoss: number | null;
  rsi: number | null;
  technicalSignal: string;
  fundamentalVerdict: string;
  fundamentalScore: number;
  backtestSuccessRate: number;
  reason: string;
}

interface ServiceCard {
  name: string;
  label: string;
  endpoint: string;
  state: ServiceState;
}

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private readonly http = inject(HttpClient);
  private readonly titleService = inject(Title);

  gatewayUrl = 'http://localhost:8080';
  symbol = 'RELIANCE';
  candlesJson = '';
  statusMessage = signal('Ready');
  isBusy = signal(false);
  stocks = signal<StockRow[]>([]);
  indicator = signal<IndicatorResponse | null>(null);
  strategy = signal<StrategyResponse | null>(null);
  activeTab = signal<'scanner' | 'strategy' | 'screener' | 'portfolio' | 'services'>('scanner');
  screenerResults = signal<IndicatorResponse[]>([]);

  services = signal<ServiceCard[]>([
    { name: 'Gateway', label: 'API Gateway', endpoint: '/actuator', state: 'checking' },
    { name: 'Stocks', label: 'Stock Data Service', endpoint: '/stocks', state: 'checking' },
    { name: 'Indicators', label: 'Indicator Service', endpoint: '/indicators/health', state: 'checking' },
    { name: 'Scanner', label: 'Scanner Service', endpoint: '/scanner/health', state: 'checking' },
    { name: 'Fundamentals', label: 'Fundamental Service', endpoint: '/fundamentals/health', state: 'checking' },
    { name: 'Strategy', label: 'Strategy Service', endpoint: '/strategy/health', state: 'checking' },
    { name: 'Backtests', label: 'Backtest Service', endpoint: '/backtests/health', state: 'checking' },
    { name: 'Users', label: 'User Service', endpoint: '/users/health', state: 'checking' },
    { name: 'Alerts', label: 'Notification Service', endpoint: '/notifications/health', state: 'checking' },
    { name: 'Discovery', label: 'Eureka Registry', endpoint: '/eureka/apps', state: 'checking' }
  ]);

  readonly sampleCandles: Candle[] = [
    ['2026-05-10', 2800], ['2026-05-11', 2810], ['2026-05-12', 2820], ['2026-05-13', 2790],
    ['2026-05-14', 2785], ['2026-05-15', 2770], ['2026-05-16', 2760], ['2026-05-17', 2750],
    ['2026-05-18', 2740], ['2026-05-19', 2730], ['2026-05-20', 2720], ['2026-05-21', 2710],
    ['2026-05-22', 2700], ['2026-05-23', 2690], ['2026-05-24', 2710], ['2026-05-25', 2730],
    ['2026-05-26', 2750], ['2026-05-27', 2770], ['2026-05-28', 2790], ['2026-05-29', 2810]
  ].map(([date, close]) => ({ date: String(date), close: Number(close) }));

  latestPrice = computed(() => {
    const rows = this.stocks();
    const latest = rows.filter((row) => row.currentPrice != null).at(-1);
    return latest?.currentPrice ?? this.parseCandles().at(-1)?.close ?? null;
  });

  savedRowsForSymbol = computed(() => {
    const currentSymbol = this.normalizedSymbol();
    return this.stocks().filter((row) => row.symbol?.toUpperCase() === currentSymbol);
  });

  screenerBuyCount = computed(() => this.screenerResults().filter((result) => result.signal === 'BUY').length);
  screenerHoldCount = computed(() => this.screenerResults().filter((result) => result.signal === 'HOLD').length);
  screenerWaitCount = computed(() => this.screenerResults().filter((result) => result.signal === 'WAIT' || result.signal === 'NO_DATA').length);
  strategyDecisionClass = computed(() => `decision-${(this.strategy()?.decision ?? 'no_buy').toLowerCase()}`);

  trendPoints = computed(() => {
    const candles = this.parseCandles();
    if (!candles.length) {
      return '';
    }
    const closes = candles.map((candle) => candle.close);
    const min = Math.min(...closes);
    const max = Math.max(...closes);
    const width = 460;
    const height = 130;
    return candles.map((candle, index) => {
      const x = candles.length === 1 ? 0 : (index / (candles.length - 1)) * width;
      const y = max === min ? height / 2 : height - ((candle.close - min) / (max - min)) * height;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(' ');
  });

  ngOnInit(): void {
    this.titleService.setTitle('Stock Badshah Scanner');
    this.loadSampleCandles();
    this.refreshAll();
  }

  setTab(tab: 'scanner' | 'strategy' | 'screener' | 'portfolio' | 'services'): void {
    this.activeTab.set(tab);
  }

  loadSampleCandles(): void {
    this.candlesJson = JSON.stringify(this.sampleCandles, null, 2);
    this.statusMessage.set('Sample candles loaded.');
  }

  async refreshAll(): Promise<void> {
    await Promise.all([this.loadStocks(), this.checkServices()]);
  }

  async loadStocks(): Promise<void> {
    try {
      const rows = await this.http.get<StockRow[]>(this.url('/stocks')).toPromise();
      this.stocks.set(rows ?? []);
    } catch {
      this.statusMessage.set('Backend is not ready. Start services, then refresh.');
    }
  }

  async saveCandles(): Promise<void> {
    await this.runTask('Candles saved through API Gateway.', async () => {
      const currentSymbol = this.normalizedSymbol();
      const rows = this.parseCandles().map((candle) => ({
        symbol: currentSymbol,
        companyName: currentSymbol,
        currentPrice: candle.close,
        stockDate: candle.date
      }));
      await this.http.post(this.url('/stocks/bulk'), rows).toPromise();
      await this.loadStocks();
    });
  }

  async scanSignal(): Promise<void> {
    await this.runTask('Signal calculated.', async () => {
      const candles = this.parseCandles();
      const response = await this.http.post<IndicatorResponse>(this.url('/scanner/scan'), {
        symbol: this.normalizedSymbol(),
        candles
      }).toPromise();
      this.indicator.set(response ?? null);
    });
  }

  async runScreener(): Promise<void> {
    await this.runTask('Market screener refreshed.', async () => {
      const response = await this.http.get<IndicatorResponse[]>(this.url('/scanner/screen')).toPromise();
      this.screenerResults.set(response ?? []);
    });
  }

  async evaluateStrategy(): Promise<void> {
    await this.runTask('Strategy decision refreshed.', async () => {
      const response = await this.http.get<StrategyResponse>(this.url(`/strategy/evaluate/${this.normalizedSymbol()}`)).toPromise();
      this.strategy.set(response ?? null);
      if (response) {
        this.indicator.set({
          symbol: response.symbol,
          signal: response.technicalSignal,
          buyDate: response.buyDate,
          buyPrice: response.buyPrice,
          rsi: response.rsi,
          sma20: null,
          target: response.target,
          stopLoss: response.stopLoss,
          result: response.reason,
          source: 'strategy-service'
        });
      }
    });
  }

  async checkServices(): Promise<void> {
    const checks = await Promise.all(this.services().map(async (service) => {
      try {
        await this.http.get(this.url(service.endpoint)).toPromise();
        return { ...service, state: 'online' as ServiceState };
      } catch {
        return { ...service, state: 'offline' as ServiceState };
      }
    }));
    this.services.set(checks);
  }

  formatNumber(value: number | null | undefined): string {
    if (value == null) {
      return '-';
    }
    return new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 }).format(value);
  }

  signalClass(): string {
    return `signal-${(this.indicator()?.signal ?? 'wait').toLowerCase()}`;
  }

  rowSignalClass(signal: string | null | undefined): string {
    return `signal-${(signal ?? 'wait').toLowerCase()}`;
  }

  decisionClass(decision: string | null | undefined): string {
    return `decision-${(decision ?? 'no_buy').toLowerCase()}`;
  }

  normalizedSymbol(): string {
    return this.symbol.trim().toUpperCase() || 'RELIANCE';
  }

  private url(path: string): string {
    return `${this.gatewayUrl.replace(/\/$/, '')}${path}`;
  }

  private parseCandles(): Candle[] {
    try {
      const value = JSON.parse(this.candlesJson || '[]');
      if (!Array.isArray(value)) {
        return [];
      }
      return value
        .filter((candle) => candle?.date && candle?.close != null)
        .map((candle) => ({ date: String(candle.date), close: Number(candle.close) }))
        .filter((candle) => Number.isFinite(candle.close));
    } catch {
      return [];
    }
  }

  private async runTask(successMessage: string, task: () => Promise<void>): Promise<void> {
    this.isBusy.set(true);
    this.statusMessage.set('Working...');
    try {
      await task();
      this.statusMessage.set(successMessage);
    } catch (error) {
      this.statusMessage.set(error instanceof Error ? error.message : 'Request failed.');
    } finally {
      this.isBusy.set(false);
    }
  }
}
