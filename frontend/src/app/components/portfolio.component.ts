import { Component, OnInit, OnDestroy, Inject, PLATFORM_ID, HostListener } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { ApiService } from '../services/api.service';

@Component({
  selector: 'app-portfolio',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="portfolio-container">
      <div class="header">
        <h2>My Portfolio</h2>
        <button (click)="downloadCSV()" class="download-btn" [disabled]="portfolio.length === 0">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          Download CSV
        </button>
      </div>
      
      <div *ngIf="loading" class="loading">Loading portfolio...</div>
      
      <!-- Cash Summary Section -->
      <div *ngIf="!loading && summary" class="cash-summary">
        <div class="cash-card">
          <div class="cash-label">Cash Balance</div>
          <div class="cash-value">\${{ summary.cashBalance | number:'1.2-2' }}</div>
        </div>
        <div class="cash-card">
          <div class="cash-label">Reserved (Pending Orders)</div>
          <div class="cash-value reserved">\${{ summary.reservedBalance | number:'1.2-2' }}</div>
        </div>
        <div class="cash-card">
          <div class="cash-label">Available Cash</div>
          <div class="cash-value available">\${{ summary.availableBalance | number:'1.2-2' }}</div>
        </div>
        <div class="cash-card">
          <div class="cash-label">Total Portfolio Value</div>
          <div class="cash-value portfolio">\${{ summary.totalPortfolioValue | number:'1.2-2' }}</div>
        </div>
      </div>
      
      <div *ngIf="!loading && portfolio.length === 0" class="empty-state">
        <p>No holdings yet. Start trading to build your portfolio!</p>
      </div>
      
      <table *ngIf="!loading && portfolio.length > 0" class="portfolio-table">
        <thead>
          <tr>
            <th>Symbol</th>
            <th>Trend</th>
            <th>Quantity</th>
            <th>Avg Price</th>
            <th>Current Price</th>
            <th>Total Value</th>
            <th>P/L</th>
            <th>P/L %</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let holding of portfolio">

            <!-- ── Symbol cell with click Predictions button ── -->
            <td class="symbol-cell">
              <span class="symbol-ticker">{{ holding.symbol }}</span>
              <button class="pred-btn"
                      [class.pred-btn--loading]="holding.predictionLoading"
                      [class.pred-btn--open]="holding.showTooltip && !holding.predictionLoading"
                      (click)="togglePredictions(holding, $event)"
                      [attr.data-symbol]="holding.symbol">
                <span *ngIf="!holding.predictionLoading">📊 Predictions</span>
                <span *ngIf="holding.predictionLoading" class="btn-loading-text">⏳ Loading…</span>
              </button>
            </td>

            <td class="trend-cell">
              <span *ngIf="holding.trend === 'UPTREND'" class="trend-arrow up" [title]="'Uptrend (' + holding.confidence + '% confidence)'">↗</span>
              <span *ngIf="holding.trend === 'DOWNTREND'" class="trend-arrow down" [title]="'Downtrend (' + holding.confidence + '% confidence)'">↘</span>
              <span *ngIf="holding.trend === 'SIDEWAYS'" class="trend-arrow sideways" [title]="'Sideways (' + holding.confidence + '% confidence)'">→</span>
              <span *ngIf="!holding.trend" class="trend-arrow loading">⋯</span>
            </td>
            <td>{{ holding.quantity }}</td>
            <td>\${{ holding.averagePrice | number:'1.2-2' }}</td>
            <td>\${{ holding.currentPrice | number:'1.2-2' }}</td>
            <td>\${{ holding.totalValue | number:'1.2-2' }}</td>
            <td [class.profit]="holding.profitLoss >= 0" [class.loss]="holding.profitLoss < 0">
              \${{ holding.profitLoss | number:'1.2-2' }}
            </td>
            <td [class.profit]="holding.profitLossPercent >= 0" [class.loss]="holding.profitLossPercent < 0">
              {{ holding.profitLossPercent | number:'1.2-2' }}%
            </td>
          </tr>
        </tbody>
        <tfoot *ngIf="summary">
          <tr class="total-row">
            <td colspan="4"><strong>Total Invested</strong></td>
            <td colspan="3"><strong>\${{ summary.totalInvestedValue | number:'1.2-2' }}</strong></td>
          </tr>
          <tr class="total-row">
            <td colspan="4"><strong>Current Value</strong></td>
            <td colspan="3"><strong>\${{ summary.totalPortfolioValue | number:'1.2-2' }}</strong></td>
          </tr>
          <tr class="total-row" [class.profit]="summary.totalProfitLoss >= 0" [class.loss]="summary.totalProfitLoss < 0">
            <td colspan="4"><strong>Total P/L</strong></td>
            <td colspan="3">
              <strong>\${{ summary.totalProfitLoss | number:'1.2-2' }} ({{ summary.totalProfitLossPercent | number:'1.2-2' }}%)</strong>
            </td>
          </tr>
        </tfoot>
      </table>

      <!-- ── Fixed-position prediction popup (rendered outside table flow) ── -->
      <div *ngIf="activeHolding && activeHolding.showTooltip"
           class="pred-popup-overlay"
           [style.top.px]="popupTop"
           [style.left.px]="popupLeft"
           (click)="$event.stopPropagation()">

        <div class="tooltip-header">
          <span class="tooltip-title">{{ activeHolding.symbol }} – 8h Price Forecasts</span>
          <span class="tooltip-current">Now: <strong>\${{ activeHolding.currentPrice | number:'1.2-2' }}</strong></span>
          <button class="popup-close" (click)="closePopup()" title="Close">&times;</button>
        </div>

        <div *ngIf="activeHolding.predictionLoading" class="tooltip-loading">
          Fetching predictions…
        </div>

        <table *ngIf="!activeHolding.predictionLoading && activeHolding.predictions?.length" class="pred-table">
          <thead>
            <tr>
              <th>Hour</th>
              <th>Predicted</th>
              <th>Δ %</th>
              <th>Confidence</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let p of activeHolding.predictions"
                [class.pred-up]="p.changePercent > 0"
                [class.pred-down]="p.changePercent < 0">
              <td class="pred-hour">{{ p.hourLabel }}</td>
              <td class="pred-price">\${{ p.predictedPrice | number:'1.2-2' }}</td>
              <td class="pred-change">
                <span [class.up]="p.changePercent > 0" [class.down]="p.changePercent < 0">
                  {{ p.changePercent > 0 ? '+' : '' }}{{ p.changePercent | number:'1.2-2' }}%
                </span>
              </td>
              <td class="pred-conf">
                <div class="conf-bar">
                  <div class="conf-fill" [style.width.%]="p.confidencePct"
                       [class.conf-high]="p.confidencePct >= 70"
                       [class.conf-mid]="p.confidencePct >= 40 && p.confidencePct < 70"
                       [class.conf-low]="p.confidencePct < 40"></div>
                </div>
                <span class="conf-label">{{ p.confidencePct | number:'1.0-0' }}%</span>
              </td>
            </tr>
          </tbody>
        </table>

        <div *ngIf="!activeHolding.predictionLoading && !activeHolding.predictions?.length" class="tooltip-no-data">
          No prediction data available yet.
        </div>

        <div *ngIf="activeHolding.techniqueWeights" class="weights-section">
          <div class="weights-title">Technique Weights</div>
          <div class="weights-list">
            <div *ngFor="let w of activeHolding.techniqueWeights" class="weight-row">
              <span class="weight-name">{{ w.name }}</span>
              <div class="weight-bar-bg"><div class="weight-bar-fill" [style.width.%]="w.pct"></div></div>
              <span class="weight-pct">{{ w.pct | number:'1.0-0' }}%</span>
            </div>
          </div>
        </div>

        <div class="tooltip-footer" *ngIf="activeHolding.predictedAt">
          Updated: {{ activeHolding.predictedAt }} {{ activeHolding.predictionCached ? '(cached)' : '(fresh)' }}
        </div>
      </div>
    </div>
  `,
  styles: [`
    .portfolio-container { max-width: 1200px; margin: 0 auto; }

    .header {
      display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px;
    }
    .download-btn {
      display: flex; align-items: center; gap: 8px; background: #10b981; padding: 10px 20px;
    }
    .download-btn:hover:not(:disabled) { background: #059669; }
    h2 { margin: 0; }

    .loading, .empty-state { text-align: center; padding: 3rem; color: #666; }

    .cash-summary {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 1rem; margin-bottom: 2rem;
    }
    .cash-card {
      background: white; padding: 1.5rem; border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1); border-left: 4px solid #667eea;
    }
    .cash-label {
      font-size: 0.875rem; color: #666; margin-bottom: 0.5rem;
      text-transform: uppercase; font-weight: 500;
    }
    .cash-value { font-size: 1.5rem; font-weight: 700; color: #333; }
    .cash-value.reserved { color: #f59e0b; }
    .cash-value.available { color: #10b981; }
    .cash-value.portfolio { color: #667eea; }

    .portfolio-table {
      width: 100%; border-collapse: collapse; background: white;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1); border-radius: 8px; overflow: visible;
    }
    th, td { padding: 1rem; text-align: left; border-bottom: 1px solid #eee; }
    th {
      background: #f8f9fa; color: #555; font-weight: 600;
      text-transform: uppercase; font-size: 0.875rem;
    }

    /* ── Symbol cell ── */
    .symbol-cell {
      font-weight: 600;
      white-space: nowrap;
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .symbol-ticker {
      color: #667eea;
      font-weight: 700;
      font-size: 0.95rem;
    }

    /* ── Predictions button ── */
    .pred-btn {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 3px 10px;
      font-size: 0.75rem;
      font-weight: 600;
      border: 1.5px solid #667eea;
      border-radius: 20px;
      background: transparent;
      color: #667eea;
      cursor: pointer;
      transition: background 0.2s, color 0.2s, border-color 0.2s;
      line-height: 1.6;
      white-space: nowrap;
    }
    .pred-btn:hover {
      background: #667eea;
      color: #fff;
    }
    .pred-btn--loading {
      background: #f59e0b !important;
      border-color: #f59e0b !important;
      color: #fff !important;
      cursor: wait;
    }
    .pred-btn--open {
      background: #10b981 !important;
      border-color: #10b981 !important;
      color: #fff !important;
    }
    .btn-loading-text { letter-spacing: 0.02em; }

    /* ── Prediction popup (fixed overlay) ── */
    .pred-popup-overlay {
      position: fixed;
      z-index: 9999;
      width: 460px;
      background: #1e293b;
      color: #e2e8f0;
      border-radius: 12px;
      box-shadow: 0 12px 40px rgba(0,0,0,0.55);
      font-size: 0.82rem;
      overflow: hidden;
      animation: popupFadeIn 0.18s ease;
    }
    @keyframes popupFadeIn {
      from { opacity: 0; transform: scale(0.97) translateY(4px); }
      to   { opacity: 1; transform: scale(1)   translateY(0); }
    }
    .tooltip-header {
      display: flex; justify-content: space-between; align-items: center;
      background: #334155; padding: 11px 14px;
    }
    .tooltip-title { font-weight: 700; font-size: 0.92rem; color: #f8fafc; }
    .tooltip-current { color: #94a3b8; font-size: 0.82rem; }
    .tooltip-current strong { color: #38bdf8; }
    .popup-close {
      background: none; border: none; color: #94a3b8;
      font-size: 1.1rem; cursor: pointer; padding: 0 0 0 10px;
      line-height: 1;
    }
    .popup-close:hover { color: #f8fafc; }

    .tooltip-loading, .tooltip-no-data {
      padding: 24px; text-align: center; color: #94a3b8;
    }

    /* ── Prediction table inside tooltip ── */
    .pred-table {
      width: 100%; border-collapse: collapse;
    }
    .pred-table thead tr { background: #263248; }
    .pred-table th {
      padding: 7px 10px; color: #94a3b8;
      font-size: 0.78rem; text-transform: uppercase; font-weight: 600;
      border-bottom: 1px solid #334155; background: transparent;
    }
    .pred-table td { padding: 7px 10px; border-bottom: 1px solid #263248; }
    .pred-table tr:last-child td { border-bottom: none; }
    .pred-table tr.pred-up  { background: rgba(16,185,129,0.05); }
    .pred-table tr.pred-down { background: rgba(239,68,68,0.05); }

    .pred-hour  { color: #94a3b8; font-size: 0.8rem; white-space: nowrap; }
    .pred-price { font-weight: 700; color: #f8fafc; }
    .pred-change .up   { color: #10b981; font-weight: 600; }
    .pred-change .down { color: #ef4444; font-weight: 600; }

    /* Confidence bar */
    .pred-conf { display: flex; align-items: center; gap: 6px; }
    .conf-bar { width: 48px; height: 6px; background: #334155; border-radius: 3px; overflow: hidden; }
    .conf-fill { height: 100%; border-radius: 3px; transition: width 0.3s; }
    .conf-high  { background: #10b981; }
    .conf-mid   { background: #f59e0b; }
    .conf-low   { background: #ef4444; }
    .conf-label { color: #94a3b8; font-size: 0.75rem; min-width: 28px; }

    /* Technique weights section */
    .weights-section { padding: 10px 14px; border-top: 1px solid #334155; }
    .weights-title { font-size: 0.75rem; color: #64748b; text-transform: uppercase; margin-bottom: 6px; font-weight: 600; }
    .weights-list  { display: flex; flex-direction: column; gap: 4px; }
    .weight-row    { display: flex; align-items: center; gap: 8px; }
    .weight-name   { font-size: 0.75rem; color: #94a3b8; width: 120px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .weight-bar-bg { flex: 1; height: 5px; background: #334155; border-radius: 3px; overflow: hidden; }
    .weight-bar-fill { height: 100%; background: #667eea; border-radius: 3px; }
    .weight-pct { font-size: 0.72rem; color: #64748b; width: 30px; text-align: right; }

    .tooltip-footer {
      padding: 6px 14px; background: #263248;
      font-size: 0.72rem; color: #64748b; text-align: right;
    }

    /* ── Trend arrows ── */
    .trend-cell { text-align: center; font-size: 1.5rem; }
    .trend-arrow { display: inline-block; font-size: 1.8rem; font-weight: bold; cursor: help; }
    .trend-arrow.up      { color: #10b981; }
    .trend-arrow.down    { color: #ef4444; }
    .trend-arrow.sideways { color: #f59e0b; }
    .trend-arrow.loading { color: #9ca3af; animation: pulse 1.5s ease-in-out infinite; }
    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50%       { opacity: 0.5; }
    }

    .profit { color: #28a745; font-weight: 600; }
    .loss   { color: #dc3545; font-weight: 600; }

    .total-row { background: #f8f9fa; font-size: 1.1rem; }
    .total-row td { border-bottom: none; }
  `]
})
export class PortfolioComponent implements OnInit, OnDestroy {
  portfolio: any[] = [];
  summary: any = null;
  loading = true;
  totalValue = 0;

  // Active popup state
  activeHolding: any = null;
  popupTop  = 0;
  popupLeft = 0;

  constructor(
    private apiService: ApiService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  @HostListener('document:click')
  onDocumentClick() {
    this.closePopup();
  }

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      const clientId = localStorage.getItem('clientId');
      if (clientId) {
        this.loadPortfolio(parseInt(clientId));
      }
    }
  }

  ngOnDestroy() {
    this.closePopup();
  }

  loadPortfolio(clientId: number) {
    this.apiService.getPortfolioSummary(clientId).subscribe({
      next: (data) => {
        this.summary = data;
        this.portfolio = data.holdings;
        this.totalValue = data.totalPortfolioValue;
        this.loading = false;
        this.loadTrends();
      },
      error: (err) => {
        console.error('Error loading portfolio:', err);
        this.loading = false;
      }
    });
  }

  loadTrends() {
    this.portfolio.forEach(holding => {
      this.apiService.getTrendAnalysis(holding.symbol).subscribe({
        next: (t) => {
          holding.trend = t.overallTrend;
          holding.confidence = Math.round(t.confidence * 100);
        },
        error: () => {
          holding.trend = 'SIDEWAYS';
          holding.confidence = 0;
        }
      });
    });
  }

  // ── Predictions button toggle ────────────────────────────────────────────────

  togglePredictions(holding: any, event: MouseEvent) {
    event.stopPropagation();

    // If clicking the already-open holding → close
    if (this.activeHolding === holding && holding.showTooltip) {
      this.closePopup();
      return;
    }

    // Close any previously open popup
    if (this.activeHolding) {
      this.activeHolding.showTooltip = false;
    }

    // Position popup below/right of the clicked button
    const btn = event.currentTarget as HTMLElement;
    const rect = btn.getBoundingClientRect();
    const popupWidth = 460;
    const fitsRight  = rect.right + 8 + popupWidth <= window.innerWidth;
    this.popupLeft = Math.max(8, fitsRight ? rect.right + 8 : rect.left - popupWidth - 8);
    this.popupTop  = Math.min(rect.bottom + window.scrollY + 4, window.scrollY + window.innerHeight - 440);

    this.activeHolding = holding;
    holding.showTooltip = true;

    // Load predictions if not yet fetched
    if (holding.predictionsLoaded) return;

    holding.predictionLoading = true;
    this.apiService.getPricePredictions(holding.symbol).subscribe({
      next: (data) => {
        holding.predictionsLoaded = true;
        holding.predictionLoading = false;
        holding.predictionCached  = data.cached;
        holding.predictedAt       = data.calculatedAt
          ? new Date(data.calculatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
          : '';

        const currentPrice: number = data.currentPrice || holding.currentPrice;
        holding.predictions = (data.hourlyPredictions || []).map((p: any) => {
          const predicted: number = p.predictedPrice;
          const change = currentPrice > 0
            ? ((predicted - currentPrice) / currentPrice) * 100
            : 0;
          return {
            hourLabel:      this.formatHour(p.targetHour),
            predictedPrice: predicted,
            changePercent:  parseFloat(change.toFixed(2)),
            confidencePct:  Math.round((p.confidence || 0) * 100),
          };
        });

        if (data.techniqueWeights) {
          holding.techniqueWeights = Object.entries(data.techniqueWeights).map(([name, w]: any) => ({
            name: name.replace(/_/g, ' '),
            pct:  Math.round(w * 100)
          }));
        }
      },
      error: (err) => {
        console.error(`Error loading predictions for ${holding.symbol}:`, err);
        holding.predictionLoading = false;
        holding.predictionsLoaded = true;
        holding.predictions = [];
      }
    });
  }

  closePopup() {
    if (this.activeHolding) {
      this.activeHolding.showTooltip = false;
    }
    this.activeHolding = null;
  }

  // ── Utilities ───────────────────────────────────────────────────────────────

  private formatHour(isoString: string): string {
    if (!isoString) return '';
    const d = new Date(isoString);
    const h = d.getHours();
    const suffix = h >= 12 ? 'PM' : 'AM';
    const hour12 = h % 12 || 12;
    return `+${this.getHourOffset(isoString)}h (${hour12}${suffix})`;
  }

  private getHourOffset(isoString: string): number {
    const now = new Date();
    const target = new Date(isoString);
    return Math.round((target.getTime() - now.getTime()) / 3_600_000);
  }

  downloadCSV() {
    const headers = ['Symbol', 'Quantity', 'Avg Price', 'Current Price', 'Total Value', 'P/L', 'P/L %'];
    const csvData = this.portfolio.map(h => [
      h.symbol, h.quantity,
      h.averagePrice.toFixed(2), h.currentPrice.toFixed(2),
      h.totalValue.toFixed(2), h.profitLoss.toFixed(2), h.profitLossPercent.toFixed(2)
    ]);
    const csvContent = [
      headers.join(','),
      ...csvData.map((row: any[]) => row.join(',')),
      '',
      `Total Portfolio Value,$${this.totalValue.toFixed(2)}`
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url  = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `portfolio-${new Date().toISOString().split('T')[0]}.csv`;
    link.click();
    window.URL.revokeObjectURL(url);
  }
}
