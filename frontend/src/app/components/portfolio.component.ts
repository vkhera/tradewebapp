import { Component, OnInit, OnDestroy, Inject, PLATFORM_ID, HostListener } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../services/api.service';

@Component({
  selector: 'app-portfolio',
  standalone: true,
  imports: [CommonModule, FormsModule],
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
      
      <!-- ── Filter bar ── -->
      <div *ngIf="!loading && portfolio.length > 0" class="filter-bar">
        <div class="filter-input-wrap">
          <svg class="filter-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input type="text" class="filter-input" placeholder="Filter by symbol…"
                 [value]="filterText" (input)="onFilter($event)" autocomplete="off">
          <button *ngIf="filterText" class="filter-clear" (click)="filterText=''" title="Clear filter">&times;</button>
        </div>
        <div class="atr-filter-wrap">
          <label class="atr-filter-label">ATR(14)</label>
          <select class="atr-filter-select" [(ngModel)]="atrFilter">
            <option value="all">All volatility</option>
            <option value="low">Low (&lt;1%)</option>
            <option value="mid">Mid (1–3%)</option>
            <option value="high">High (≥3%)</option>
            <option value="na">No data</option>
          </select>
        </div>
        <span class="filter-count">{{ filteredPortfolio.length }} / {{ portfolio.length }} holdings</span>
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
            <th title="Average True Range – 14-day Wilder smoothed. Measures daily price volatility.">ATR(14)</th>
            <th title="75th-percentile of the 14 True Ranges used in ATR(14). Typical high-volatility day range.">ATR-75(14)</th>
            <th title="90th-percentile of the 14 True Ranges used in ATR(14). Tail-risk / worst-day range.">ATR-90(14)</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngIf="filteredPortfolio.length === 0">
            <td colspan="11" class="no-match">No holdings match current filters</td>
          </tr>
          <tr *ngFor="let holding of filteredPortfolio">

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
            <td class="atr-cell">
              <ng-container *ngIf="holding.atr14 != null; else atrNA">
                <span class="atr-value"
                      [class.atr-high]="holding.atrPct >= 3"
                      [class.atr-mid]="holding.atrPct >= 1 && holding.atrPct < 3"
                      [class.atr-low]="holding.atrPct < 1"
                      [title]="'ATR-14: $' + holding.atr14.toFixed(2) + ' (' + holding.atrPct.toFixed(2) + '% of price)'">
                  \${{ holding.atr14 | number:'1.2-2' }}
                  <span class="atr-pct">({{ holding.atrPct | number:'1.1-1' }}%)</span>
                </span>
              </ng-container>
              <ng-template #atrNA><span class="atr-na">–</span></ng-template>
            </td>
            <td class="atr-cell">
              <ng-container *ngIf="holding.atr75 != null; else atr75NA">
                <span class="atr-value"
                      [class.atr-high]="holding.atr75Pct >= 3"
                      [class.atr-mid]="holding.atr75Pct >= 1 && holding.atr75Pct < 3"
                      [class.atr-low]="holding.atr75Pct < 1"
                      [title]="'ATR-75: $' + holding.atr75.toFixed(2) + ' (' + holding.atr75Pct.toFixed(2) + '% of price)'">
                  \${{ holding.atr75 | number:'1.2-2' }}
                  <span class="atr-pct">({{ holding.atr75Pct | number:'1.1-1' }}%)</span>
                </span>
              </ng-container>
              <ng-template #atr75NA><span class="atr-na">–</span></ng-template>
            </td>
            <td class="atr-cell">
              <ng-container *ngIf="holding.atr90 != null; else atr90NA">
                <span class="atr-value"
                      [class.atr-high]="holding.atr90Pct >= 3"
                      [class.atr-mid]="holding.atr90Pct >= 1 && holding.atr90Pct < 3"
                      [class.atr-low]="holding.atr90Pct < 1"
                      [title]="'ATR-90: $' + holding.atr90.toFixed(2) + ' (' + holding.atr90Pct.toFixed(2) + '% of price)'">
                  \${{ holding.atr90 | number:'1.2-2' }}
                  <span class="atr-pct">({{ holding.atr90Pct | number:'1.1-1' }}%)</span>
                </span>
              </ng-container>
              <ng-template #atr90NA><span class="atr-na">–</span></ng-template>
            </td>
          </tr>
        </tbody>
        <tfoot *ngIf="summary">
          <tr class="total-row">
            <td colspan="5"><strong>Total Invested</strong></td>
            <td colspan="6"><strong>\${{ summary.totalInvestedValue | number:'1.2-2' }}</strong></td>
          </tr>
          <tr class="total-row">
            <td colspan="5"><strong>Current Value</strong></td>
            <td colspan="6"><strong>\${{ summary.totalPortfolioValue | number:'1.2-2' }}</strong></td>
          </tr>
          <tr class="total-row" [class.profit]="summary.totalProfitLoss >= 0" [class.loss]="summary.totalProfitLoss < 0">
            <td colspan="5"><strong>Total P/L</strong></td>
            <td colspan="6">
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
          <span class="tooltip-title">{{ activeHolding.symbol }} – Today\'s Price Forecasts</span>
          <span class="tooltip-current">Now: <strong>\${{ activeHolding.currentPrice | number:'1.2-2' }}</strong></span>
          <button class="popup-close" (click)="closePopup()" title="Close">&times;</button>
        </div>

        <div *ngIf="activeHolding.predictionLoading" class="tooltip-loading">
          Fetching predictions…
        </div>

        <div *ngIf="!activeHolding.predictionLoading && activeHolding.predictions?.length" class="day-section-title today-title">Today</div>
        <table *ngIf="!activeHolding.predictionLoading && activeHolding.predictions?.length" class="pred-table">
          <thead>
            <tr>
              <th>Hour</th>
              <th>Predicted</th>
              <th>Actual</th>
              <th>Δ %</th>
              <th>Confidence</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let p of activeHolding.predictions"
                [class.pred-up]="p.changePercent > 0"
                [class.pred-down]="p.changePercent < 0"
                [class.pred-past]="p.isPast">
              <td class="pred-hour">{{ p.hourLabel }}</td>
              <td class="pred-price">\${{ p.predictedPrice | number:'1.2-2' }}</td>
              <td class="pred-actual">
                <span *ngIf="p.isPast && p.actualPrice">\${{ p.actualPrice | number:'1.2-2' }}</span>
                <span *ngIf="!p.isPast || !p.actualPrice" class="pred-pending">–</span>
              </td>
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

        <!-- Previous business day predictions -->
        <div *ngIf="!activeHolding.predictionLoading && activeHolding.prevDayPredictions?.length" class="prev-day-section">
          <div class="day-section-title prev-day-title">Previous Business Day</div>
          <table class="pred-table prev-day-table">
            <thead>
              <tr>
                <th>Hour</th>
                <th>Predicted</th>
                <th>Actual</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let p of activeHolding.prevDayPredictions">
                <td class="pred-hour">{{ p.hourLabel }}</td>
                <td class="pred-price">\${{ p.predictedPrice | number:'1.2-2' }}</td>
                <td class="pred-actual">
                  <span *ngIf="p.actualPrice">\${{ p.actualPrice | number:'1.2-2' }}</span>
                  <span *ngIf="!p.actualPrice" class="pred-pending">–</span>
                </td>
              </tr>
            </tbody>
          </table>
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

        <div *ngIf="activeHolding.indexInfluences?.length" class="idx-section">
          <div class="weights-title">Market Index Influences</div>
          <div class="idx-grid">
            <div class="idx-header">
              <span>Index</span><span>Price</span><span>Today</span><span>Correlation</span><span>Weight</span><span>Impact</span>
            </div>
            <div *ngFor="let idx of activeHolding.indexInfluences" class="idx-row">
              <span class="idx-sym">{{ idx.indexSymbol }}</span>
              <span class="idx-price">\${{ idx.currentPrice | number:'1.0-2' }}</span>
              <span class="idx-ret" [class.pos]="idx.todayReturnPct > 0" [class.neg]="idx.todayReturnPct < 0">
                {{ idx.todayReturnPct > 0 ? '+' : '' }}{{ idx.todayReturnPct | number:'1.2-2' }}%
              </span>
              <span class="idx-corr" [class.pos]="idx.correlation > 0.15" [class.neg]="idx.correlation < -0.15">
                {{ idx.correlation | number:'1.2-2' }}
              </span>
              <span class="idx-wt">{{ (idx.weight * 100) | number:'1.0-0' }}%</span>
              <span class="idx-impact" [class.pos]="idx.influencePct > 0" [class.neg]="idx.influencePct < 0">
                {{ idx.influencePct > 0 ? '+' : '' }}{{ idx.influencePct | number:'1.3-3' }}%
              </span>
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
      width: 580px;
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
      /* Override global table { background: white } — keeps the overlay's dark bg visible */
      background: transparent;
    }
    .pred-table tbody tr:hover { background: rgba(255,255,255,0.06); } /* override global white hover */
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

    /* Actual price column */
    .pred-actual { color: #e2e8f0; font-weight: 600; }
    .pred-pending { color: #475569; font-size: 0.85rem; }
    .pred-past td { opacity: 0.75; }

    /* Section title labels (Today / Previous Business Day) */
    .day-section-title {
      padding: 5px 14px;
      font-size: 0.7rem; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase;
      background: #1e293b; border-top: 2px solid #334155; border-bottom: 1px solid #334155;
    }
    .today-title { color: #38bdf8; border-top: none; }
    .prev-day-title { color: #94a3b8; margin-top: 4px; }
    /* Previous business day section */
    .prev-day-section { border-top: 2px solid #475569; margin-top: 4px; background: rgba(15,23,42,0.4); }
    .prev-day-table thead tr { background: #1a2535; }

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

    /* Index influences section */
    .idx-section { padding: 10px 14px; border-top: 1px solid #334155; }
    .idx-grid { display: flex; flex-direction: column; gap: 3px; }
    .idx-header { display: grid; grid-template-columns: 52px 72px 60px 80px 44px 58px;
                  gap: 4px; font-size: 0.68rem; color: #64748b; font-weight: 600;
                  text-transform: uppercase; padding-bottom: 4px; border-bottom: 1px solid #2d3e55; }
    .idx-header span, .idx-row span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .idx-row { display: grid; grid-template-columns: 52px 72px 60px 80px 44px 58px;
               gap: 4px; font-size: 0.74rem; color: #cbd5e1; align-items: center; padding: 2px 0; }
    .idx-sym { font-weight: 700; color: #e2e8f0; }
    .idx-price { color: #94a3b8; }
    .idx-ret, .idx-corr, .idx-impact { font-weight: 600; }
    .pos { color: #10b981; }
    .neg { color: #ef4444; }
    .idx-wt { color: #667eea; }
    .idx-impact { font-size: 0.72rem; }

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

    /* ── ATR cell ── */
    .atr-cell { white-space: nowrap; }
    .atr-value { font-weight: 600; font-size: 0.9rem; display: inline-flex; align-items: baseline; gap: 4px; }
    .atr-pct   { font-size: 0.75rem; font-weight: 400; opacity: 0.85; }
    .atr-high  { color: #dc3545; }   /* ≥ 3 %  – high volatility */
    .atr-mid   { color: #f59e0b; }   /* 1–3 %  – moderate */
    .atr-low   { color: #28a745; }   /* < 1 %  – low volatility */
    .atr-na    { color: #adb5bd; font-size: 0.85rem; }

    /* ── ATR filter dropdown ── */
    .atr-filter-wrap {
      display: flex; align-items: center; gap: 6px; flex: 0 0 auto;
    }
    .atr-filter-label {
      font-size: 0.82rem; color: #6b7280; white-space: nowrap; font-weight: 600;
    }
    .atr-filter-select {
      padding: 7px 10px; border: 1.5px solid #d1d5db; border-radius: 8px;
      font-size: 0.85rem; color: #374151; background: #fff;
      outline: none; cursor: pointer; transition: border-color 0.2s;
    }
    .atr-filter-select:focus { border-color: #667eea; box-shadow: 0 0 0 3px rgba(102,126,234,0.15); }

    /* ── Filter bar ── */
    .filter-bar {
      display: flex; align-items: center; justify-content: space-between;
      margin-bottom: 12px; gap: 12px;
    }
    .filter-input-wrap {
      position: relative; display: flex; align-items: center; flex: 0 0 auto;
    }
    .filter-icon {
      position: absolute; left: 10px; color: #9ca3af; pointer-events: none;
    }
    .filter-input {
      padding: 8px 36px 8px 34px; border: 1.5px solid #d1d5db;
      border-radius: 8px; font-size: 0.9rem; width: 240px;
      outline: none; transition: border-color 0.2s;
    }
    .filter-input:focus { border-color: #667eea; box-shadow: 0 0 0 3px rgba(102,126,234,0.15); }
    .filter-clear {
      position: absolute; right: 8px; background: none; border: none;
      color: #9ca3af; font-size: 1.1rem; cursor: pointer; line-height: 1;
      padding: 0 2px;
    }
    .filter-clear:hover { color: #374151; }
    .filter-count {
      font-size: 0.82rem; color: #6b7280; white-space: nowrap;
    }
    .no-match {
      text-align: center; padding: 2rem; color: #6b7280; font-style: italic;
    }
  `]
})
export class PortfolioComponent implements OnInit, OnDestroy {
  portfolio: any[] = [];
  summary: any = null;
  loading = true;
  totalValue = 0;
  filterText = '';
  atrFilter  = 'all';

  // Active popup state
  activeHolding: any = null;
  popupTop  = 0;
  popupLeft = 0;

  get filteredPortfolio(): any[] {
    let result = this.portfolio;
    if (this.filterText.trim()) {
      const f = this.filterText.trim().toLowerCase();
      result = result.filter(h => h.symbol.toLowerCase().includes(f));
    }
    if (this.atrFilter !== 'all') {
      result = result.filter(h => {
        const pct: number | null = h.atrPct;
        switch (this.atrFilter) {
          case 'low':  return pct != null && pct < 1;
          case 'mid':  return pct != null && pct >= 1 && pct < 3;
          case 'high': return pct != null && pct >= 3;
          case 'na':   return pct == null;
          default:     return true;
        }
      });
    }
    return result;
  }

  onFilter(event: Event) {
    this.filterText = (event.target as HTMLInputElement).value;
  }

  constructor(
    private apiService: ApiService,
    private router: Router,
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
      } else {
        // No session — redirect to login
        this.router.navigate(['/login']);
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
        this.portfolio = data.holdings
          .map((h: any) => ({
            ...h,
            // Pre-compute ATR values as % of current price for colour-coding
            atrPct:   (h.atr14 != null && h.currentPrice > 0) ? (h.atr14 / h.currentPrice) * 100 : null,
            atr75Pct: (h.atr75 != null && h.currentPrice > 0) ? (h.atr75 / h.currentPrice) * 100 : null,
            atr90Pct: (h.atr90 != null && h.currentPrice > 0) ? (h.atr90 / h.currentPrice) * 100 : null
          }))
          // Sort by total market value descending; zero-priced entries go to bottom
          .sort((a: any, b: any) => (b.totalValue || 0) - (a.totalValue || 0));
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

    // Position popup beside the clicked button (position:fixed → viewport coords,
    // so do NOT add window.scrollY — getBoundingClientRect() is already viewport-relative)
    const btn = event.currentTarget as HTMLElement;
    const rect = btn.getBoundingClientRect();
    const popupWidth  = 460;
    const popupHeight = 440;

    // Horizontal: prefer right of button, flip left if not enough room
    const fitsRight = rect.right + 8 + popupWidth <= window.innerWidth;
    this.popupLeft = Math.max(8, fitsRight ? rect.right + 8 : rect.left - popupWidth - 8);

    // Vertical: prefer below button, flip above if not enough room below
    const spaceBelow = window.innerHeight - rect.bottom;
    if (spaceBelow >= popupHeight) {
      this.popupTop = rect.bottom + 4;
    } else {
      // Not enough space below — place above the button
      this.popupTop = Math.max(8, rect.top - popupHeight - 4);
    }

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
        holding.predictions = (data.hourlyPredictions || [])
          .filter((p: any) => this.isDuringMarketHours(p.targetHour))
          .map((p: any) => {
          const predicted: number = p.predictedPrice;
          const change = currentPrice > 0
            ? ((predicted - currentPrice) / currentPrice) * 100
            : 0;
          return {
            hourLabel:      this.formatHour(p.targetHour),
            predictedPrice: predicted,
            changePercent:  parseFloat(change.toFixed(2)),
            confidencePct:  Math.round((p.confidence || 0) * 100),
            actualPrice:    p.actualPrice || null,
            isPast:         this.isPastHour(p.targetHour),
          };
        });

        holding.prevDayPredictions = (data.previousDayPredictions || [])
          .filter((p: any) => this.isDuringMarketHours(p.targetHour))
          .map((p: any) => ({
            hourLabel:      this.formatHour(p.targetHour),
            predictedPrice: p.predictedPrice,
            actualPrice:    p.actualPrice || null,
          }));

        if (data.techniqueWeights) {
          holding.techniqueWeights = Object.entries(data.techniqueWeights).map(([name, w]: any) => ({
            name: name.replace(/_/g, ' '),
            pct:  Math.round(w * 100)
          }));
        }

        if (data.indexInfluences?.length) {
          holding.indexInfluences = data.indexInfluences;
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

  /**
   * Returns true if the targetHour falls within NYSE market hours:
   * 9:30 AM – 4:00 PM Eastern Time.
   *
   * targetHour is a LocalDateTime from the backend serialised without timezone
   * (e.g. "2026-02-19T10:30:00"), so we parse the time component directly from
   * the ISO string to avoid browser-timezone conversion errors.
   */
  private isDuringMarketHours(isoString: string): boolean {
    if (!isoString) return false;
    // The backend stores targetHour as UTC LocalDateTime (Docker container runs in UTC).
    // Extract "HH:MM" directly from the ISO string to read the UTC hour.
    const timePart = isoString.substring(11, 16);   // e.g. "14:30"
    const [hStr, mStr] = timePart.split(':');
    const totalMinutes = parseInt(hStr, 10) * 60 + parseInt(mStr, 10);
    // NYSE market hours converted to UTC:
    //   9:30 AM ET (EST=UTC-5) = 14:30 UTC = 870 min
    //   9:30 AM ET (EDT=UTC-4) = 13:30 UTC = 810 min  ← use as lower bound to cover DST
    //   4:00 PM ET (EST=UTC-5) = 21:00 UTC = 1260 min ← use as upper bound
    //   4:00 PM ET (EDT=UTC-4) = 20:00 UTC = 1200 min
    return totalMinutes >= 810 && totalMinutes <= 1260;
  }

  private formatHour(isoString: string): string {
    if (!isoString) return '';
    // Backend sends UTC LocalDateTime without 'Z'; append 'Z' so the browser parses as UTC,
    // then convert to Eastern Time for display.
    const utcDate = new Date(isoString + 'Z');
    return utcDate.toLocaleTimeString('en-US', {
      hour: 'numeric',
      minute: '2-digit',
      timeZone: 'America/New_York',
      hour12: true
    }).replace(':00', '');
  }

  private isPastHour(isoString: string): boolean {
    if (!isoString) return false;
    // Append 'Z' so the ISO string is parsed as UTC, matching how the backend stores targetHour
    return new Date(isoString + 'Z').getTime() < Date.now();
  }

  downloadCSV() {
    const headers = ['Symbol', 'Quantity', 'Avg Price', 'Current Price', 'Total Value', 'P/L', 'P/L %', 'ATR(14)', 'ATR%', 'ATR-75(14)', 'ATR-75%', 'ATR-90(14)', 'ATR-90%'];
    const csvData = this.portfolio.map(h => [
      h.symbol, h.quantity,
      h.averagePrice.toFixed(2), h.currentPrice.toFixed(2),
      h.totalValue.toFixed(2), h.profitLoss.toFixed(2), h.profitLossPercent.toFixed(2),
      h.atr14  != null ? h.atr14.toFixed(2)  : '',
      h.atrPct != null ? h.atrPct.toFixed(2) : '',
      h.atr75  != null ? h.atr75.toFixed(2)  : '',
      h.atr75Pct != null ? h.atr75Pct.toFixed(2) : '',
      h.atr90  != null ? h.atr90.toFixed(2)  : '',
      h.atr90Pct != null ? h.atr90Pct.toFixed(2) : ''
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
