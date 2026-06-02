import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Title } from '@angular/platform-browser';

type ServiceState = 'online' | 'checking' | 'offline';

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

interface StockPage {
  content: StockRow[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
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
  marketCap: number | null;
  peRatio: number | null;
  pegRatio: number | null;
  roe: number | null;
  debtToEquity: number | null;
  profitGrowth: number | null;
  salesGrowth: number | null;
  salesCagr: number | null;
  profitCagr: number | null;
  stockPriceCagr: number | null;
  netProfit: number | null;
  futurePerspective: string | null;
  orderBook: string | null;
  fundamentalDataSource: string | null;
  backtestSuccessRate: number;
  reason: string;
}

interface UniverseRecommendationResponse {
  universe: string;
  total: number;
  loaded: number;
  failed: number;
  passed: number;
  failedSymbols: string[];
  recommendations: StrategyResponse[];
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
  symbol = signal('RELIANCE');
  statusMessage = signal('Ready to check live market data.');
  isBusy = signal(false);
  stocks = signal<StockRow[]>([]);
  priceRows = signal<StockRow[]>([]);
  pricePage = signal(0);
  pricePageSize = signal(50);
  priceTotalElements = signal(0);
  priceTotalPages = signal(0);
  indicator = signal<IndicatorResponse | null>(null);
  strategy = signal<StrategyResponse | null>(null);
  activeTab = signal<'scanner' | 'strategy' | 'screener' | 'portfolio' | 'services'>('scanner');
  screenerResults = signal<IndicatorResponse[]>([]);
  marketRecommendations = signal<StrategyResponse[]>([]);
  recommendationPage = signal(0);
  recommendationPageSize = signal(25);
  successSortDirection = signal<'desc' | 'asc'>('desc');
  scannedCount = signal(0);
  scanTotal = signal(0);
  liveLoadedCount = signal(0);
  liveFailedCount = signal(0);
  failedSymbols = signal<string[]>([]);
  activeScanName = signal('Nifty 100');

  services = signal<ServiceCard[]>([
    { name: 'App', label: 'Main app connection', endpoint: '/actuator', state: 'checking' },
    { name: 'Prices', label: 'Market prices', endpoint: '/stocks', state: 'checking' },
    { name: 'Trend', label: 'Price trend check', endpoint: '/indicators/health', state: 'checking' },
    { name: 'Scanner', label: 'Stock recommendation', endpoint: '/scanner/health', state: 'checking' },
    { name: 'Company', label: 'Company strength check', endpoint: '/fundamentals/health', state: 'checking' },
    { name: 'Advice', label: 'Final buying view', endpoint: '/strategy/health', state: 'checking' },
    { name: 'History', label: 'Past success check', endpoint: '/backtests/health', state: 'checking' },
    { name: 'Account', label: 'User account', endpoint: '/users/health', state: 'checking' },
    { name: 'Alerts', label: 'Price alerts', endpoint: '/notifications/health', state: 'checking' },
    { name: 'Network', label: 'App network', endpoint: '/eureka/apps', state: 'checking' }
  ]);

  readonly symbolModel = {
    get: () => this.symbol(),
    set: (value: string) => this.symbol.set(value)
  };

  latestPrice(): number | null {
    const latest = this.savedRowsForSymbol().filter((row) => row.currentPrice != null).at(-1);
    return latest?.currentPrice ?? this.strategy()?.buyPrice ?? null;
  }

  savedRowsForSymbol(): StockRow[] {
    const currentSymbol = this.normalizedSymbol();
    return this.stocks().filter((row) => row.symbol?.toUpperCase() === currentSymbol);
  }

  screenerBuyCount = computed(() => this.marketRecommendations().length);
  screenerHoldCount = computed(() => this.marketRecommendations().filter((result) => result.technicalSignal === 'HOLD').length);
  screenerWaitCount = computed(() => Math.max(0, this.liveLoadedCount() - this.marketRecommendations().length));
  recommendationTotalPages = computed(() => Math.ceil(this.marketRecommendations().length / this.recommendationPageSize()));
  sortedMarketRecommendations = computed(() => {
    const direction = this.successSortDirection();
    return [...this.marketRecommendations()].sort((left, right) => {
      const leftRate = left.backtestSuccessRate ?? 0;
      const rightRate = right.backtestSuccessRate ?? 0;
      return direction === 'desc' ? rightRate - leftRate : leftRate - rightRate;
    });
  });
  visibleMarketRecommendations = computed(() => {
    const page = Math.min(this.recommendationPage(), Math.max(0, this.recommendationTotalPages() - 1));
    const start = page * this.recommendationPageSize();
    return this.sortedMarketRecommendations().slice(start, start + this.recommendationPageSize());
  });
  recommendationStart = computed(() => this.marketRecommendations().length ? this.recommendationPage() * this.recommendationPageSize() + 1 : 0);
  recommendationEnd = computed(() => Math.min(this.marketRecommendations().length, (this.recommendationPage() + 1) * this.recommendationPageSize()));
  strategyDecisionClass = computed(() => `decision-${(this.strategy()?.decision ?? 'no_buy').toLowerCase()}`);
  userRecommendation = computed(() => this.toDecisionLabel(this.strategy()?.decision ?? this.indicator()?.signal));
  topRecommendation = computed(() => {
    const recommendations = [...this.marketRecommendations()];
    return recommendations.sort((a, b) => {
      const buyRank = Number(b.decision === 'BUY') - Number(a.decision === 'BUY');
      if (buyRank !== 0) {
        return buyRank;
      }
      return (b.backtestSuccessRate ?? 0) - (a.backtestSuccessRate ?? 0);
    })[0] ?? null;
  });
  topRecommendationSymbol(): string {
    return this.strategy()?.symbol ?? this.indicator()?.symbol ?? this.topRecommendation()?.symbol ?? this.normalizedSymbol();
  }

  topSuccessChance = computed(() => this.strategy()?.backtestSuccessRate ?? this.topRecommendation()?.backtestSuccessRate ?? null);

  trendPoints(): string {
    const candles = this.savedRowsForSymbol()
      .filter((row) => row.stockDate && row.currentPrice != null)
      .map((row) => ({ date: String(row.stockDate), close: Number(row.currentPrice) }));
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
  }

  ngOnInit(): void {
    this.titleService.setTitle('Stock Badshah Scanner');
    this.refreshAll();
  }

  setTab(tab: 'scanner' | 'strategy' | 'screener' | 'portfolio' | 'services'): void {
    this.activeTab.set(tab);
    if (tab === 'portfolio') {
      void this.loadPricePage();
    }
  }

  async refreshAll(): Promise<void> {
    await Promise.all([this.loadStocks(), this.loadPricePage(), this.checkServices()]);
  }

  async loadStocks(): Promise<void> {
    try {
      const rows = await this.http.get<StockRow[]>(this.url(`/stocks/${this.normalizedSymbol()}`)).toPromise();
      this.stocks.set(rows ?? []);
    } catch {
      this.statusMessage.set('Backend is not ready. Start services, then refresh.');
    }
  }

  async loadPricePage(page = this.pricePage()): Promise<void> {
    try {
      const response = await this.http.get<StockPage>(this.url(`/stocks/page?page=${page}&size=${this.pricePageSize()}`)).toPromise();
      this.priceRows.set(response?.content ?? []);
      this.pricePage.set(response?.number ?? page);
      this.priceTotalElements.set(response?.totalElements ?? 0);
      this.priceTotalPages.set(response?.totalPages ?? 0);
    } catch {
      this.statusMessage.set('Could not load price rows right now.');
    }
  }

  async nextPricePage(): Promise<void> {
    if (this.pricePage() + 1 < this.priceTotalPages()) {
      await this.loadPricePage(this.pricePage() + 1);
    }
  }

  async previousPricePage(): Promise<void> {
    if (this.pricePage() > 0) {
      await this.loadPricePage(this.pricePage() - 1);
    }
  }

  nextRecommendationPage(): void {
    if (this.recommendationPage() + 1 < this.recommendationTotalPages()) {
      this.recommendationPage.set(this.recommendationPage() + 1);
    }
  }

  previousRecommendationPage(): void {
    if (this.recommendationPage() > 0) {
      this.recommendationPage.set(this.recommendationPage() - 1);
    }
  }

  toggleSuccessSort(): void {
    this.successSortDirection.set(this.successSortDirection() === 'desc' ? 'asc' : 'desc');
    this.recommendationPage.set(0);
  }

  async refreshLiveData(): Promise<void> {
    await this.runTask('Live market data updated.', async () => {
      await this.refreshLiveSymbol(this.normalizedSymbol());
      await this.loadStocks();
      await this.loadPricePage(this.pricePage());
    });
  }

  async scanSignal(): Promise<void> {
    await this.runTask('Recommendation is ready.', async () => {
      await this.refreshLiveSymbol(this.normalizedSymbol());
      await this.loadStocks();
      const response = await this.http.get<IndicatorResponse>(this.url(`/scanner/scan/${this.normalizedSymbol()}`)).toPromise();
      this.indicator.set(response ?? null);
      await this.evaluateStrategy(false);
    });
  }

  async runScreener(): Promise<void> {
    await this.scanUniverse('nifty100', 'Nifty 100');
  }

  async runNifty500Scanner(): Promise<void> {
    await this.scanUniverse('nifty500', 'Nifty 500');
  }

  async scanUniverse(universe: 'nifty100' | 'nifty500', label: string): Promise<void> {
    await this.runTask(`${label} scan is ready.`, async () => {
      this.activeScanName.set(label);
      this.marketRecommendations.set([]);
      this.recommendationPage.set(0);
      this.scannedCount.set(0);
      this.scanTotal.set(0);
      this.liveLoadedCount.set(0);
      this.liveFailedCount.set(0);
      this.failedSymbols.set([]);
      const expectedTotal = universe === 'nifty500' ? 500 : 100;
      this.scanTotal.set(expectedTotal);
      this.statusMessage.set(`${label} scan is running on the server. Loading live prices and checking the buy rule...`);

      const response = await this.http.get<UniverseRecommendationResponse>(this.url(`/strategy/recommendations/universe/${universe}`)).toPromise();
      const recommendations = this.sortRecommendations(response?.recommendations ?? []);
      this.scanTotal.set(response?.total ?? expectedTotal);
      this.scannedCount.set(response?.total ?? expectedTotal);
      this.liveLoadedCount.set(response?.loaded ?? 0);
      this.liveFailedCount.set(response?.failed ?? 0);
      this.failedSymbols.set(response?.failedSymbols ?? []);
      this.marketRecommendations.set(recommendations);
      this.recommendationPage.set(0);

      const top = this.topRecommendation();
      if (top) {
        await this.selectRecommendation(top, false);
      }
      await this.loadStocks();
      await this.loadPricePage(this.pricePage());

      if (!recommendations.length) {
        this.statusMessage.set(`No ${label} stocks passed the buy rule right now.`);
      } else {
        this.statusMessage.set(`${label} scan complete. ${recommendations.length} stocks passed the buy rule.`);
      }
    });
  }

  async evaluateStrategy(showStatus = true): Promise<void> {
    const task = async () => {
      await this.refreshLiveSymbol(this.normalizedSymbol());
      await this.loadStocks();
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
    };

    if (showStatus) {
      await this.runTask('Recommendation is ready.', task);
      return;
    }

    await task();
  }

  async selectRecommendation(recommendation: StrategyResponse, scrollToTop = true): Promise<void> {
    this.symbol.set(recommendation.symbol);
    this.strategy.set(recommendation);
    this.indicator.set({
      symbol: recommendation.symbol,
      signal: recommendation.technicalSignal,
      buyDate: recommendation.buyDate,
      buyPrice: recommendation.buyPrice,
      rsi: recommendation.rsi,
      sma20: null,
      target: recommendation.target,
      stopLoss: recommendation.stopLoss,
      result: recommendation.reason,
      source: 'strategy-service'
    });
    await this.loadStocks();
    if (scrollToTop) {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
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

  formatPercent(value: number | null | undefined): string {
    if (value == null) {
      return '-';
    }
    return `${this.formatNumber(value)}%`;
  }

  formatTargetGain(result: StrategyResponse): string {
    const gain = this.targetGainPercent(result.buyPrice, result.target);
    if (gain == null) {
      return '';
    }
    const sign = gain >= 0 ? '+' : '';
    return `(${sign}${this.formatNumber(gain)}%)`;
  }

  recommendationReasons(): string[] {
    const currentStrategy = this.strategy();
    const currentIndicator = this.indicator();
    if (!currentStrategy && !currentIndicator) {
      return ['Select a stock or click View to see why the app is showing this recommendation.'];
    }

    const price = currentStrategy?.buyPrice ?? currentIndicator?.buyPrice ?? null;
    const target = currentStrategy?.target ?? currentIndicator?.target ?? null;
    const stopLoss = currentStrategy?.stopLoss ?? currentIndicator?.stopLoss ?? null;
    const trendScore = currentStrategy?.rsi ?? currentIndicator?.rsi ?? null;
    const gain = this.targetGainPercent(price, target);
    const technicalSignal = this.toDecisionLabel(currentStrategy?.technicalSignal ?? currentIndicator?.signal);
    const successRate = currentStrategy?.backtestSuccessRate ?? null;
    const companyScore = currentStrategy?.fundamentalScore ?? null;
    const companyVerdict = currentStrategy?.fundamentalVerdict ?? null;
    const daysChecked = currentIndicator?.candlesCount || this.savedRowsForSymbol().length || null;

    const reasons = [
      `Price trend check says: ${technicalSignal}. This means recent price movement is supporting the buying view.`,
      `Trend score is ${this.formatNumber(trendScore)}. A stronger score means the stock has better recent buying strength.`,
      `Past success chance is ${this.formatPercent(successRate)} based on recent market history and this app's target and stop-loss rule.`,
      `Current price is ${this.formatNumber(price)} and expected target is ${this.formatNumber(target)} ${gain == null ? '' : `(${gain >= 0 ? '+' : ''}${this.formatNumber(gain)}%)`}.`,
      `Stop loss is ${this.formatNumber(stopLoss)}. This is the safety level where the idea should be reviewed if price moves down.`,
      `Company strength check is ${companyVerdict || 'not available'}${companyScore == null ? '' : ` with score ${companyScore}`}. Missing company data is not treated as a negative signal.`,
      `Market days checked: ${daysChecked || '-'}. More recent price rows help the app judge the trend and past success rate.`
    ];

    return reasons;
  }

  fullCheckReasons(): string[] {
    const response = this.strategy();
    if (!response) {
      return ['Click Get Full Recommendation to run price trend, company strength, past success, and final buying checks.'];
    }

    return [
      `Technical view: ${this.toDecisionLabel(response.technicalSignal)}. The app checks recent price movement and trend score before giving a buy view.`,
      `Trend score is ${this.formatNumber(response.rsi)}. Higher trend score means recent buying strength is better.`,
      `Past success chance is ${this.formatPercent(response.backtestSuccessRate)}. This is tested using recent history with the app's target and stop-loss rule.`,
      `Valuation: PE is ${this.formatNumber(response.peRatio)} and PEG is ${this.formatNumber(response.pegRatio)}. Lower PEG is generally better because it compares price valuation with profit growth.`,
      `Company size: market cap is ${this.formatNumber(response.marketCap)} Cr. Bigger companies are usually more stable than very small companies.`,
      `Profitability: ROE is ${this.formatPercent(response.roe)}. This tells how efficiently the company uses shareholder money.`,
      `Growth: sales growth is ${this.formatPercent(response.salesGrowth)} and profit growth is ${this.formatPercent(response.profitGrowth)}. A good buy should ideally have both sales and profit moving up.`,
      `Future view: ${response.futurePerspective || 'Future perspective is not available from the free data source.'}`,
      `Order book: ${response.orderBook || 'Order book is not available from the free data source.'}`,
      `Final result: ${this.toDecisionLabel(response.decision)} with ${this.confidenceLabel(response.confidence)} confidence.`
    ];
  }

  toDecisionLabel(value: string | null | undefined): string {
    switch ((value ?? '').toUpperCase()) {
      case 'BUY':
        return 'Good to buy';
      case 'HOLD':
        return 'Hold for now';
      case 'SELL':
      case 'NO_BUY':
        return 'Do not buy now';
      case 'NO_DATA':
        return 'No market data found';
      default:
        return 'Wait and watch';
    }
  }

  confidenceLabel(value: string | null | undefined): string {
    switch ((value ?? '').toUpperCase()) {
      case 'HIGH':
        return 'High';
      case 'MEDIUM':
        return 'Medium';
      case 'LOW':
        return 'Low';
      default:
        return '-';
    }
  }

  sourceLabel(source: string | null | undefined): string {
    if (!source) {
      return '-';
    }
    return source === 'stock-data-service' || source === 'strategy-service' ? 'Live market data' : 'Saved market data';
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
    return this.symbol().trim().toUpperCase() || 'RELIANCE';
  }

  private url(path: string): string {
    return `${this.gatewayUrl.replace(/\/$/, '')}${path}`;
  }

  private sortRecommendations(recommendations: StrategyResponse[]): StrategyResponse[] {
    return recommendations.sort((a, b) => {
      const buyRank = Number(b.decision === 'BUY') - Number(a.decision === 'BUY');
      if (buyRank !== 0) {
        return buyRank;
      }
      const successRank = (b.backtestSuccessRate ?? 0) - (a.backtestSuccessRate ?? 0);
      if (successRank !== 0) {
        return successRank;
      }
      return (b.buyPrice ?? 0) - (a.buyPrice ?? 0);
    });
  }

  private targetGainPercent(price: number | null | undefined, target: number | null | undefined): number | null {
    if (!price || !target || price <= 0) {
      return null;
    }
    return ((target - price) / price) * 100;
  }

  private async refreshLiveWithRetry(symbol: string): Promise<boolean> {
    for (let attempt = 1; attempt <= 3; attempt++) {
      try {
        await this.refreshLiveSymbol(symbol);
        this.liveLoadedCount.set(this.liveLoadedCount() + 1);
        return true;
      } catch {
        if (attempt < 3) {
          await this.wait(attempt * 1200);
        }
      }
    }

    this.rememberFailedSymbol(symbol);
    return false;
  }

  private rememberFailedSymbol(symbol: string): void {
    if (!this.failedSymbols().includes(symbol)) {
      this.failedSymbols.set([...this.failedSymbols(), symbol]);
      this.liveFailedCount.set(this.liveFailedCount() + 1);
    }
  }

  private async refreshLiveSymbol(symbol: string): Promise<void> {
    await this.http.post<StockRow[]>(this.url(`/stocks/live/${encodeURIComponent(symbol)}/refresh`), {}).toPromise();
  }

  private wait(milliseconds: number): Promise<void> {
    return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
  }

  private async runTask(successMessage: string, task: () => Promise<void>): Promise<void> {
    this.isBusy.set(true);
    this.statusMessage.set('Checking latest data...');
    try {
      await task();
      if (!this.statusMessage().startsWith('No ') && !this.statusMessage().includes('scan complete')) {
        this.statusMessage.set(successMessage);
      }
    } catch (error) {
      this.statusMessage.set(error instanceof Error ? error.message : 'Request failed.');
    } finally {
      this.isBusy.set(false);
    }
  }
}
