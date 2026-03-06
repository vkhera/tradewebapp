import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { of, throwError } from 'rxjs';
import { SuggestedTradesComponent } from './suggested-trades.component';
import { ApiService } from '../services/api.service';

// â”€â”€â”€ Shared test data â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

const MOCK_SUGGESTION = {
  symbol: 'NVDA',
  quantity: 10,
  currentPrice: 200.00,
  atr14: 5.00,
  avgPredictedPrice: 185.00,
  expectedChangePct: -7.5,
  action: 'SELL',
  suggestedSellPrice: 200.00,
  suggestedBuyBackPrice: 195.00,
  confidence: 75,
  reasoning: 'Predictions indicate a 7.5% decline.'
};

const MOCK_HISTORY = [
  {
    id: 1,
    symbol: 'AAPL',
    quantity: 5,
    suggestedDate: '2026-03-01T10:00:00',
    action: 'SELL',
    currentPriceAtSuggestion: 175.50,
    suggestedSellPrice: 175.50,
    suggestedBuyBackPrice: 170.00,
    expectedChangePct: -3.2,
    confidence: 80,
    status: 'SUCCESS',
    resolvedDate: '2026-03-02T14:00:00'
  },
  {
    id: 2,
    symbol: 'TSLA',
    quantity: 8,
    suggestedDate: '2026-03-03T09:30:00',
    action: 'WATCH',
    currentPriceAtSuggestion: 250.00,
    suggestedSellPrice: null,
    suggestedBuyBackPrice: 242.00,
    expectedChangePct: 0,
    confidence: 40,
    status: 'PENDING',
    resolvedDate: null
  },
  {
    id: 3,
    symbol: 'MSFT',
    quantity: 3,
    suggestedDate: '2026-02-27T11:00:00',
    action: 'SELL',
    currentPriceAtSuggestion: 410.00,
    suggestedSellPrice: 410.00,
    suggestedBuyBackPrice: 402.00,
    expectedChangePct: -2.5,
    confidence: 65,
    status: 'FAILED',
    resolvedDate: '2026-03-06T06:00:00'
  }
];

const MOCK_SUCCESS_RATE = {
  totalResolved: 4,
  successCount: 3,
  failedCount: 1,
  pendingCount: 2,
  successRatePct: 75.0
};

// â”€â”€â”€ Suite â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

describe('SuggestedTradesComponent', () => {
  let fixture: ComponentFixture<SuggestedTradesComponent>;
  let component: SuggestedTradesComponent;
  let apiServiceSpy: jasmine.SpyObj<ApiService>;

  beforeEach(async () => {
    // Seed a client ID in localStorage
    localStorage.setItem('clientId', '1');

    apiServiceSpy = jasmine.createSpyObj<ApiService>('ApiService', [
      'getSuggestedTrades',
      'getSuggestedTradeHistory',
      'getSuggestedTradeSuccessRate'
    ]);
    apiServiceSpy.getSuggestedTrades.and.returnValue(of([MOCK_SUGGESTION]));
    apiServiceSpy.getSuggestedTradeHistory.and.returnValue(of(MOCK_HISTORY));
    apiServiceSpy.getSuggestedTradeSuccessRate.and.returnValue(of(MOCK_SUCCESS_RATE));

    await TestBed.configureTestingModule({
      imports: [SuggestedTradesComponent, CommonModule],
      providers: [{ provide: ApiService, useValue: apiServiceSpy }]
    }).compileComponents();

    fixture = TestBed.createComponent(SuggestedTradesComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    localStorage.removeItem('clientId');
  });

  // â”€â”€ Creation & initial load â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should call getSuggestedTrades, history, and success-rate on init', () => {
    fixture.detectChanges();
    expect(apiServiceSpy.getSuggestedTrades).toHaveBeenCalledWith(1);
    expect(apiServiceSpy.getSuggestedTradeHistory).toHaveBeenCalledWith(1);
    expect(apiServiceSpy.getSuggestedTradeSuccessRate).toHaveBeenCalledWith(1);
  });

  it('should populate suggestions array after init', () => {
    fixture.detectChanges();
    expect(component.suggestions.length).toBe(1);
    expect(component.suggestions[0].symbol).toBe('NVDA');
  });

  it('should populate history array after init', () => {
    fixture.detectChanges();
    expect(component.history.length).toBe(3);
  });

  it('should populate successRate after init', () => {
    fixture.detectChanges();
    expect(component.successRate).not.toBeNull();
    expect(component.successRate?.successRatePct).toBe(75.0);
  });

  // â”€â”€ Template rendering â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  it('should render the success-rate badge in the header', () => {
    fixture.detectChanges();
    const compiled: HTMLElement = fixture.nativeElement;
    const badge = compiled.querySelector('.success-rate-badge');
    expect(badge).not.toBeNull();
    expect(badge?.textContent).toContain('75');
    expect(badge?.textContent).toContain('Success Rate');
  });

  it('should render the history table when records exist', () => {
    fixture.detectChanges();
    const compiled: HTMLElement = fixture.nativeElement;
    const table = compiled.querySelector('.history-table');
    expect(table).not.toBeNull();
  });

  it('should render one row per history record in the table', () => {
    fixture.detectChanges();
    const compiled: HTMLElement = fixture.nativeElement;
    const rows = compiled.querySelectorAll('.history-table tbody tr');
    expect(rows.length).toBe(3);
  });

  it('should show status badges for SUCCESS, FAILED, and PENDING', () => {
    fixture.detectChanges();
    const compiled: HTMLElement = fixture.nativeElement;
    const badges = compiled.querySelectorAll('.status-badge');
    const texts = Array.from(badges).map(b => b.textContent?.trim() ?? '');
    expect(texts.some(t => t.includes('SUCCESS'))).toBeTrue();
    expect(texts.some(t => t.includes('FAILED'))).toBeTrue();
    expect(texts.some(t => t.includes('PENDING'))).toBeTrue();
  });

  it('should display stats chips below the history table', () => {
    fixture.detectChanges();
    const compiled: HTMLElement = fixture.nativeElement;
    const chips = compiled.querySelectorAll('.stat-chip');
    expect(chips.length).toBeGreaterThanOrEqual(4);
  });

  it('should show the disclaimer when suggestions are present', () => {
    fixture.detectChanges();
    const compiled: HTMLElement = fixture.nativeElement;
    expect(compiled.querySelector('.disclaimer')).not.toBeNull();
  });

  // â”€â”€ Error handling â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  it('should show error message if suggestions API fails', () => {
    apiServiceSpy.getSuggestedTrades.and.returnValue(
      throwError(() => ({ message: 'Network error' }))
    );

    fixture.detectChanges();
    const compiled: HTMLElement = fixture.nativeElement;
    expect(compiled.querySelector('.error-box')).not.toBeNull();
    expect(compiled.querySelector('.error-box')?.textContent).toContain('Failed to load suggestions');
  });

  it('should show no-data message when suggestion list is empty', () => {
    apiServiceSpy.getSuggestedTrades.and.returnValue(of([]));

    fixture.detectChanges();
    const compiled: HTMLElement = fixture.nativeElement;
    expect(compiled.querySelector('.no-data')).not.toBeNull();
  });

  it('should show no-history message when history list is empty', () => {
    apiServiceSpy.getSuggestedTradeHistory.and.returnValue(of([]));

    fixture.detectChanges();
    const compiled: HTMLElement = fixture.nativeElement;
    expect(compiled.querySelector('.no-history')).not.toBeNull();
  });

  it('should not crash if success-rate API fails (non-critical)', () => {
    apiServiceSpy.getSuggestedTradeSuccessRate.and.returnValue(
      throwError(() => new Error('rate error'))
    );

    fixture.detectChanges();
    // Component should still render without crashing
    expect(component).toBeTruthy();
    expect(component.successRate).toBeNull();
  });

  // â”€â”€ Missing client ID â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  it('should display error when clientId is missing from localStorage', () => {
    localStorage.removeItem('clientId');
    fixture.detectChanges();
    expect(component.error).toBeTruthy();
    expect(apiServiceSpy.getSuggestedTrades).not.toHaveBeenCalled();
  });

  // â”€â”€ Refresh button â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  it('should reload data when the Refresh button is clicked', () => {
    fixture.detectChanges();
    const compiled: HTMLElement = fixture.nativeElement;
    const btn = compiled.querySelector('.btn-refresh') as HTMLButtonElement;
    btn.click();
    expect(apiServiceSpy.getSuggestedTrades).toHaveBeenCalledTimes(2);
    expect(apiServiceSpy.getSuggestedTradeHistory).toHaveBeenCalledTimes(2);
  });

  // â”€â”€ formatDate helper â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  it('formatDate should return a human-readable date string', () => {
    const result = component.formatDate('2026-03-01T10:30:00');
    expect(result).toContain('Mar');
    expect(result).toContain('1');
    expect(result).toContain('2026');
  });

  // â”€â”€ Daily suggestion visibility â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  it('should call the suggestions API each time loadAll() is invoked (simulates daily usage)', () => {
    fixture.detectChanges();
    // Simulate the component being visited on two additional days
    component.loadAll();
    component.loadAll();

    // Initial ngOnInit + 2 manual loads = 3 total
    expect(apiServiceSpy.getSuggestedTrades).toHaveBeenCalledTimes(3);
    expect(apiServiceSpy.getSuggestedTradeHistory).toHaveBeenCalledTimes(3);
    expect(apiServiceSpy.getSuggestedTradeSuccessRate).toHaveBeenCalledTimes(3);
  });
});
