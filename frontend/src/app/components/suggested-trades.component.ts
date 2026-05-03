import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, Client } from '../services/api.service';

interface SuggestedTrade {
  symbol: string;
  quantity: number;
  currentPrice: number;
  atr14: number;
  avgPredictedPrice: number | null;
  expectedChangePct: number;
  action: string;
  suggestedSellPrice: number | null;
  suggestedBuyBackPrice: number | null;
  confidence: number;
  reasoning: string;
  etfSignal?: string;
  recentNews?: {
    id: number; symbol: string; title: string; summary: string;
    publisher: string; articleUrl: string; publishedAt: string;
    sentiment: string; sentimentConfidence: number;
    analysisReason: string; llmModel: string; analyzedAt: string;
  }[];
}

interface SuggestedTradeHistory {
  id: number;
  symbol: string;
  quantity: number;
  suggestedDate: string;
  action: string;
  currentPriceAtSuggestion: number;
  currentMarketPrice: number | null;
  suggestedSellPrice: number | null;
  suggestedBuyBackPrice: number | null;
  expectedChangePct: number;
  confidence: number;
  status: 'PENDING' | 'SUCCESS' | 'FAILED';
  resolvedDate: string | null;
}

interface SwingTradeSuggestion {
  id: number | null;
  symbol: string;
  quantity: number;
  currentPrice: number;
  action: 'HOLD' | 'SELL';
  targetPrice: number;
  stopLoss: number;
  predictedReturnPct: number;
  holdDaysEstimated: number;
  confidence: number;
  topStrategies: string;
  reasoning: string;
  suggestedDate: string;
  status: 'PENDING' | 'SUCCESS' | 'FAILED';
  resolvedDate: string | null;
  currentMarketPrice: number | null;
}

interface SuccessRate {
  totalResolved: number;
  successCount: number;
  failedCount: number;
  pendingCount: number;
  successRatePct: number;
}

@Component({
  selector: 'app-suggested-trades',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <div class="page-header">
        <div class="header-row">
          <div>
            <h2>💡 Suggested Trades</h2>
            <p class="subtitle">
              AI-powered suggestions based on ATR(14) and 8-hour price predictions.
              Stocks expected to move more than 2% are shown below (up to 5).
            </p>
            <div *ngIf="isAdminUser" class="client-selector-row">
              <label for="suggestedTradesClientSelect">Client</label>
              <select id="suggestedTradesClientSelect"
                      [(ngModel)]="selectedClientId"
                      (ngModelChange)="onClientChange()"
                      [disabled]="clientLoading || clients.length === 0">
                <option [ngValue]="null">Select client</option>
                <option *ngFor="let client of clients" [ngValue]="client.id ?? null">
                  {{ client.name }} ({{ client.clientCode }})
                </option>
              </select>
              <span class="client-selector-hint" *ngIf="clientLoading">Loading clients...</span>
            </div>
          </div>
          <div *ngIf="successRate" class="success-rate-badge" [class.good]="successRate.successRatePct >= 50" [class.poor]="successRate.successRatePct < 50">
            <div class="rate-value">{{ successRate.successRatePct | number:'1.1-1' }}%</div>
            <div class="rate-label">Success Rate</div>
            <div class="rate-detail">{{ successRate.successCount }} of {{ successRate.totalResolved }} resolved</div>
          </div>
        </div>
        <button class="btn btn-refresh" (click)="loadAll()" [disabled]="loading || swingLoading">
          {{ (loading || swingLoading) ? '⏳ Loading...' : '🔄 Refresh All' }}
        </button>
      </div>

      <div *ngIf="error" class="error-box">
        {{ error }}
      </div>

      <div *ngIf="!loading && !error && suggestions.length === 0" class="no-data">
        <div class="no-data-icon">✅</div>
        <p>No trade suggestions at this time.</p>
        <p class="no-data-hint">Your portfolio looks stable – no holdings are expected to move more than 2% in the next 8 hours.</p>
      </div>

      <div *ngFor="let trade of suggestions; let i = index" class="trade-card"
           [class.sell]="trade.action === 'SELL'" [class.watch]="trade.action === 'WATCH'">
        <div class="trade-header">
          <div class="rank">#{{ i + 1 }}</div>
          <div class="symbol">{{ trade.symbol }}</div>
          <div class="action-badge" [class.sell-badge]="trade.action === 'SELL'" [class.watch-badge]="trade.action === 'WATCH'">
            {{ trade.action === 'SELL' ? '📉 SELL SIGNAL' : '👀 WATCH' }}
          </div>
          <div class="confidence-bar" title="Confidence: {{ trade.confidence }}%">
            <div class="confidence-fill" [style.width]="trade.confidence + '%'"
                 [style.background]="trade.action === 'SELL' ? '#dc3545' : '#fd7e14'"></div>
            <span class="confidence-label">{{ trade.confidence }}% confidence</span>
          </div>
        </div>

        <div class="trade-body">
          <div class="metrics">
            <div class="metric">
              <label>Current Price</label>
              <span class="price">\${{ trade.currentPrice | number:'1.2-2' }}</span>
            </div>
            <div class="metric">
              <label>Shares Held</label>
              <span>{{ trade.quantity }}</span>
            </div>
            <div class="metric">
              <label>ATR(14)</label>
              <span class="atr">\${{ trade.atr14 | number:'1.2-2' }}</span>
            </div>
            <div class="metric">
              <label>Avg Predicted (8h)</label>
              <span *ngIf="trade.avgPredictedPrice">\${{ trade.avgPredictedPrice | number:'1.2-2' }}</span>
              <span *ngIf="!trade.avgPredictedPrice" class="muted">N/A</span>
            </div>
            <div class="metric">
              <label>Expected Change</label>
              <span [class.negative]="trade.expectedChangePct < 0" [class.positive]="trade.expectedChangePct > 0">
                {{ trade.expectedChangePct > 0 ? '+' : '' }}{{ trade.expectedChangePct | number:'1.2-2' }}%
              </span>
            </div>
          </div>

          <div *ngIf="trade.action === 'SELL'" class="suggestion-box">
            <div class="suggestion-row">
              <div class="suggestion-item sell-item">
                <div class="suggestion-label">📤 Suggested Sell Price</div>
                <div class="suggestion-price">\${{ trade.suggestedSellPrice | number:'1.2-2' }}</div>
                <div class="suggestion-hint">Sell now to capture current value</div>
              </div>
              <div class="suggestion-arrow">→</div>
              <div class="suggestion-item buy-item">
                <div class="suggestion-label">📥 Suggested Buy-Back Price</div>
                <div class="suggestion-price">\${{ trade.suggestedBuyBackPrice | number:'1.2-2' }}</div>
                <div class="suggestion-hint">Re-enter after expected dip (~1 ATR below)</div>
              </div>
            </div>
            <div class="profit-hint">
              Potential savings per share:
              <strong>\${{ (trade.suggestedSellPrice! - trade.suggestedBuyBackPrice!) | number:'1.2-2' }}</strong>
              · Total:
              <strong>\${{ ((trade.suggestedSellPrice! - trade.suggestedBuyBackPrice!) * trade.quantity) | number:'1.2-2' }}</strong>
            </div>
          </div>

          <div class="reasoning">
            <div class="reasoning-label">Analysis</div>
            <div class="reasoning-text">{{ trade.reasoning }}</div>
          </div>

          <!-- ETF Signal badge -->
          <div class="etf-signal-row" *ngIf="trade.etfSignal && trade.etfSignal !== 'SIDEWAYS'">
            <span class="etf-signal-label">ETF Signal:</span>
            <span class="badge" [ngClass]="trade.etfSignal === 'UPTREND' ? 'badge-bullish' : 'badge-bearish'">
              {{ trade.etfSignal === 'UPTREND' ? '▲ Bullish (BUZZ/MMTM/HDGE)' : '▼ Bearish (BUZZ/MMTM/HDGE)' }}
            </span>
          </div>

          <!-- Recent News mini-table -->
          <div class="news-section" *ngIf="trade.recentNews && trade.recentNews.length > 0">
            <div class="reasoning-label">Recent News</div>
            <div class="news-list">
              <div class="news-item" *ngFor="let n of trade.recentNews">
                <span class="news-sentiment" [ngClass]="n.sentiment === 'POSITIVE' ? 'pos' : n.sentiment === 'NEGATIVE' ? 'neg' : 'neu'">{{ n.sentiment }}</span>
                <a [href]="n.articleUrl" target="_blank" rel="noopener" class="news-title">{{ n.title }}</a>
                <span class="news-pub">{{ n.publisher }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div *ngIf="!loading && suggestions.length > 0" class="disclaimer">
        ⚠️ These suggestions are generated by predictive models and are for informational purposes only.
        They do not constitute financial advice. Always do your own research before trading.
      </div>

      <!-- ── Swing Trade Suggestions ── -->
      <div class="swing-section">
        <div class="swing-header-row">
          <div>
            <h3>🏄 Swing Trade Suggestions</h3>
            <p class="subtitle">Multi-day swing analysis using RSI, MACD, Bollinger Bands, EMA Crossover and Volume Momentum.</p>
          </div>
          <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
            <button class="btn btn-refresh" (click)="refreshSwing()" [disabled]="swingLoading">
              {{ swingLoading ? '⏳ Analysing...' : '🔄 Refresh' }}
            </button>
            <div *ngIf="swingSuccessRate && (swingSuccessRate.totalResolved > 0)" class="success-rate-badge"
                 [class.good]="swingSuccessRate.successRatePct >= 50" [class.poor]="swingSuccessRate.successRatePct < 50">
              <div class="rate-value">{{ swingSuccessRate.successRatePct | number:'1.1-1' }}%</div>
              <div class="rate-label">Swing Rate</div>
              <div class="rate-detail">{{ swingSuccessRate.successCount }}/{{ swingSuccessRate.totalResolved }} resolved</div>
            </div>
          </div>
        </div>

        <div *ngIf="swingLoading" class="loading-spinner">Analysing holdings…</div>

        <div *ngIf="!swingLoading && swingError" class="error-box">{{ swingError }}</div>

        <div *ngIf="!swingLoading && !swingError && swingSuggestions.length === 0" class="no-data" style="margin-top:0">
          <div class="no-data-icon">📊</div>
          <p>No swing trade signals at this time.</p>
          <p class="no-data-hint">Technical strategies did not detect sufficient conviction in any held position.</p>
        </div>

        <table *ngIf="!swingLoading && swingSuggestions.length > 0" class="swing-table">
          <thead>
            <tr>
              <th>#</th>
              <th>Symbol</th>
              <th>Shares</th>
              <th>Current Price</th>
              <th>Action</th>
              <th>Target Price</th>
              <th>Est. Return</th>
              <th>Stop Loss</th>
              <th>Hold Days</th>
              <th>Confidence</th>
              <th>Strategies</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let s of swingSuggestions; let i = index"
                [class.swing-hold]="s.action === 'HOLD'" [class.swing-sell]="s.action === 'SELL'"
                [title]="s.reasoning">
              <td class="rank-cell">#{{ i + 1 }}</td>
              <td class="symbol-cell">{{ s.symbol }}</td>
              <td>{{ s.quantity }}</td>
              <td>\${{ s.currentPrice | number:'1.2-2' }}</td>
              <td>
                <span class="action-pill" [class.hold-pill]="s.action === 'HOLD'" [class.swing-sell-pill]="s.action === 'SELL'">
                  {{ s.action === 'HOLD' ? '📈 HOLD' : '📉 SELL' }}
                </span>
              </td>
              <td>\${{ s.targetPrice | number:'1.2-2' }}</td>
              <td class="positive">+{{ s.predictedReturnPct | number:'1.1-1' }}%</td>
              <td class="negative">\${{ s.stopLoss | number:'1.2-2' }}</td>
              <td>~{{ s.holdDaysEstimated }}d</td>
              <td>
                <div class="mini-confidence">
                  <div class="mini-fill" [style.width]="s.confidence + '%'"
                       [style.background]="s.action === 'HOLD' ? '#28a745' : '#dc3545'"></div>
                  <span class="mini-label">{{ s.confidence }}%</span>
                </div>
              </td>
              <td class="strategies-cell">{{ s.topStrategies }}</td>
            </tr>
          </tbody>
        </table>

        <div *ngIf="swingSuggestions.length > 0" class="swing-disclaimer">
          ℹ️ HOLD = bullish signal; hold your position targeting the listed price.
          SELL = bearish signal; consider exiting and re-entering near the target price.
        </div>
      </div>

      <!-- ── History Table (last 10 days) ── -->
      <div class="history-section">
        <h3>📋 Suggested Trades — Last 10 Days</h3>

        <div *ngIf="historyLoading" class="loading-spinner">Loading history…</div>

        <div *ngIf="!historyLoading && history.length === 0" class="no-history">
          No suggestion history yet. History is populated as trades are suggested each day.
        </div>

        <div *ngIf="!historyLoading && history.length > 0" class="history-table-wrapper">
        <table class="history-table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Symbol</th>
              <th>Action</th>
              <th>Price at Suggestion</th>
              <th>Current Price</th>
              <th>Target (Buy-Back)</th>
              <th>Expected Δ%</th>
              <th>Confidence</th>
              <th>Status</th>
              <th>Resolved</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let h of history" [class.row-success]="h.status === 'SUCCESS'" [class.row-failed]="h.status === 'FAILED'" [class.row-pending]="h.status === 'PENDING'">
              <td>{{ formatDate(h.suggestedDate) }}</td>
              <td class="symbol-cell">{{ h.symbol }}</td>
              <td>
                <span class="action-pill" [class.sell-pill]="h.action === 'SELL'" [class.watch-pill]="h.action === 'WATCH'">
                  {{ h.action }}
                </span>
              </td>
              <td>\${{ h.currentPriceAtSuggestion | number:'1.2-2' }}</td>
              <td>
                <span *ngIf="h.currentMarketPrice !== null && h.currentMarketPrice !== undefined"
                      [class.positive]="h.currentMarketPrice < h.currentPriceAtSuggestion"
                      [class.negative]="h.currentMarketPrice > h.currentPriceAtSuggestion">
                  \${{ h.currentMarketPrice | number:'1.2-2' }}
                </span>
                <span *ngIf="h.currentMarketPrice === null || h.currentMarketPrice === undefined" class="muted">—</span>
              </td>
              <td>
                <span *ngIf="h.suggestedBuyBackPrice">\${{ h.suggestedBuyBackPrice | number:'1.2-2' }}</span>
                <span *ngIf="!h.suggestedBuyBackPrice" class="muted">—</span>
              </td>
              <td [class.negative]="h.expectedChangePct < 0" [class.positive]="h.expectedChangePct > 0">
                {{ h.expectedChangePct > 0 ? '+' : '' }}{{ h.expectedChangePct | number:'1.2-2' }}%
              </td>
              <td>{{ h.confidence }}%</td>
              <td>
                <span class="status-badge" [class.status-success]="h.status === 'SUCCESS'" [class.status-failed]="h.status === 'FAILED'" [class.status-pending]="h.status === 'PENDING'">
                  {{ h.status === 'SUCCESS' ? '✅ SUCCESS' : h.status === 'FAILED' ? '❌ FAILED' : '⏳ PENDING' }}
                </span>
              </td>
              <td class="muted">{{ h.resolvedDate ? formatDate(h.resolvedDate) : '—' }}</td>
            </tr>
          </tbody>
        </table>

        </div>

        <!-- Stats row -->
        <div *ngIf="successRate && (successRate.totalResolved > 0 || successRate.pendingCount > 0)" class="stats-row">
          <div class="stat-chip success-chip">✅ {{ successRate.successCount }} Successful</div>
          <div class="stat-chip failed-chip">❌ {{ successRate.failedCount }} Failed</div>
          <div class="stat-chip pending-chip">⏳ {{ successRate.pendingCount }} Pending</div>
          <div class="stat-chip rate-chip">
            🎯 {{ successRate.successRatePct | number:'1.1-1' }}% success rate
          </div>
        </div>
      </div>

      <!-- ── Swing Trade History (last 5 days) ── -->
      <div *ngIf="swingHistory.length > 0" class="history-section">
        <h3>🏄 Swing Trade History — Last 5 Days</h3>
        <table class="history-table swing-table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Symbol</th>
              <th>Action</th>
              <th>Entry Price</th>
              <th>Current Price</th>
              <th>Target Price</th>
              <th>Est. Return</th>
              <th>Confidence</th>
              <th>Hold Days</th>
              <th>Status</th>
              <th>Resolved</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let h of swingHistory"
                [class.row-success]="h.status === 'SUCCESS'" [class.row-failed]="h.status === 'FAILED'" [class.row-pending]="h.status === 'PENDING'">
              <td>{{ formatDate(h.suggestedDate) }}</td>
              <td class="symbol-cell">{{ h.symbol }}</td>
              <td>
                <span class="action-pill" [class.hold-pill]="h.action === 'HOLD'" [class.swing-sell-pill]="h.action === 'SELL'">
                  {{ h.action === 'HOLD' ? '📈 HOLD' : '📉 SELL' }}
                </span>
              </td>
              <td>\${{ h.currentPrice | number:'1.2-2' }}</td>
              <td>{{ h.currentMarketPrice != null ? ('$' + (h.currentMarketPrice | number:'1.2-2')) : '—' }}</td>
              <td>\${{ h.targetPrice | number:'1.2-2' }}</td>
              <td class="positive">+{{ h.predictedReturnPct | number:'1.1-1' }}%</td>
              <td>{{ h.confidence }}%</td>
              <td>~{{ h.holdDaysEstimated }}d</td>
              <td>
                <span class="status-badge" [class.status-success]="h.status === 'SUCCESS'" [class.status-failed]="h.status === 'FAILED'" [class.status-pending]="h.status === 'PENDING'">
                  {{ h.status === 'SUCCESS' ? '✅ SUCCESS' : h.status === 'FAILED' ? '❌ FAILED' : '⏳ PENDING' }}
                </span>
              </td>
              <td class="muted">{{ h.resolvedDate ? formatDate(h.resolvedDate) : '—' }}</td>
            </tr>
          </tbody>
        </table>

        <div *ngIf="swingSuccessRate && (swingSuccessRate.totalResolved > 0 || swingSuccessRate.pendingCount > 0)" class="stats-row">
          <div class="stat-chip success-chip">✅ {{ swingSuccessRate.successCount }} Successful</div>
          <div class="stat-chip failed-chip">❌ {{ swingSuccessRate.failedCount }} Failed</div>
          <div class="stat-chip pending-chip">⏳ {{ swingSuccessRate.pendingCount }} Pending</div>
          <div class="stat-chip rate-chip">
            🎯 {{ swingSuccessRate.successRatePct | number:'1.1-1' }}% swing success rate
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .container {
      max-width: 1000px;
      margin: 20px auto;
      padding: 20px;
    }

    .page-header {
      margin-bottom: 24px;
    }

    .header-row {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
      flex-wrap: wrap;
      margin-bottom: 12px;
    }

    .page-header h2 {
      font-size: 1.8rem;
      color: #1a1a2e;
      margin: 0 0 8px;
    }

    .subtitle {
      color: #666;
      font-size: 0.9rem;
      margin-bottom: 0;
    }

    /* Success-rate badge in header */
    .success-rate-badge {
      text-align: center;
      padding: 12px 20px;
      border-radius: 12px;
      min-width: 120px;
      border: 2px solid;
    }

    .success-rate-badge.good  { background: #d4edda; border-color: #28a745; color: #155724; }
    .success-rate-badge.poor  { background: #f8d7da; border-color: #dc3545; color: #721c24; }

    .rate-value  { font-size: 1.8rem; font-weight: 700; line-height: 1; }
    .rate-label  { font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; }
    .rate-detail { font-size: 0.72rem; margin-top: 2px; opacity: 0.8; }

    .btn-refresh {
      padding: 8px 20px;
      background: #007bff;
      color: #fff;
      border: none;
      border-radius: 6px;
      cursor: pointer;
      font-size: 0.9rem;
    }

    .btn-refresh:hover:not(:disabled) { background: #0056b3; }
    .btn-refresh:disabled { background: #ccc; cursor: not-allowed; }

    .error-box {
      background: #f8d7da;
      color: #721c24;
      border: 1px solid #f5c6cb;
      border-radius: 6px;
      padding: 12px 16px;
      margin-bottom: 16px;
    }

    .no-data {
      text-align: center;
      padding: 48px 20px;
      background: #f9f9f9;
      border-radius: 8px;
      color: #555;
    }

    .no-data-icon { font-size: 3rem; margin-bottom: 12px; }
    .no-data-hint { font-size: 0.85rem; color: #888; margin-top: 8px; }

    .trade-card {
      background: #fff;
      border-radius: 12px;
      box-shadow: 0 2px 12px rgba(0,0,0,0.08);
      margin-bottom: 20px;
      overflow: hidden;
      border-left: 5px solid #ccc;
    }

    .trade-card.sell { border-left-color: #dc3545; }
    .trade-card.watch { border-left-color: #fd7e14; }

    .trade-header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 14px 20px;
      background: #f8f9fa;
      flex-wrap: wrap;
    }

    .rank {
      font-size: 1.1rem;
      font-weight: 700;
      color: #888;
      min-width: 28px;
    }

    .symbol {
      font-size: 1.4rem;
      font-weight: 700;
      color: #1a1a2e;
      min-width: 70px;
    }

    .action-badge {
      padding: 4px 12px;
      border-radius: 20px;
      font-size: 0.8rem;
      font-weight: 600;
    }

    .sell-badge { background: #f8d7da; color: #721c24; }
    .watch-badge { background: #fff3cd; color: #856404; }

    .confidence-bar {
      position: relative;
      flex: 1;
      min-width: 120px;
      height: 20px;
      background: #e9ecef;
      border-radius: 10px;
      overflow: hidden;
    }

    .confidence-fill {
      height: 100%;
      border-radius: 10px;
      transition: width 0.5s ease;
    }

    .confidence-label {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.72rem;
      font-weight: 600;
      color: #333;
    }

    .trade-body { padding: 20px; }

    .metrics {
      display: flex;
      gap: 20px;
      flex-wrap: wrap;
      margin-bottom: 20px;
    }

    .metric {
      display: flex;
      flex-direction: column;
      min-width: 100px;
    }

    .metric label {
      font-size: 0.72rem;
      color: #888;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      margin-bottom: 4px;
    }

    .negative { color: #dc3545; font-weight: 600; }
    .positive { color: #28a745; font-weight: 600; }
    .muted { color: #aaa; }
    .price { font-weight: 600; }
    .atr { color: #6f42c1; font-weight: 600; }

    .suggestion-box {
      background: #fff8f8;
      border: 1px solid #f5c6cb;
      border-radius: 8px;
      padding: 16px;
      margin-bottom: 16px;
    }

    .suggestion-row {
      display: flex;
      align-items: center;
      gap: 16px;
      flex-wrap: wrap;
    }

    .suggestion-item {
      flex: 1;
      min-width: 140px;
    }

    .suggestion-label {
      font-size: 0.75rem;
      color: #888;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      margin-bottom: 4px;
    }

    .suggestion-price {
      font-size: 1.4rem;
      font-weight: 700;
    }

    .sell-item .suggestion-price { color: #dc3545; }
    .buy-item .suggestion-price { color: #28a745; }

    .suggestion-hint {
      font-size: 0.75rem;
      color: #888;
      margin-top: 2px;
    }

    .suggestion-arrow {
      font-size: 1.5rem;
      color: #aaa;
    }

    .profit-hint {
      margin-top: 12px;
      padding-top: 10px;
      border-top: 1px solid #f5c6cb;
      font-size: 0.85rem;
      color: #555;
    }

    .profit-hint strong { color: #28a745; }

    .reasoning {
      background: #f0f4f8;
      border-radius: 6px;
      padding: 12px 16px;
    }

    .reasoning-label {
      font-size: 0.72rem;
      color: #888;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      margin-bottom: 4px;
    }

    .reasoning-text {
      font-size: 0.9rem;
      color: #444;
      line-height: 1.5;
    }

    .etf-signal-row {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-top: 10px;
    }

    .etf-signal-label { font-size: 0.8rem; color: #555; }

    .badge { padding: 2px 10px; border-radius: 12px; font-size: 0.78rem; font-weight: 600; }
    .badge-bullish { background: #e6f4ea; color: #1e7e34; }
    .badge-bearish { background: #fce8e6; color: #c5221f; }

    .news-section {
      margin-top: 12px;
      background: #f8f9fa;
      border-radius: 6px;
      padding: 10px 14px;
    }

    .news-list { display: flex; flex-direction: column; gap: 6px; margin-top: 6px; }

    .news-item {
      display: flex;
      align-items: baseline;
      gap: 8px;
      font-size: 0.82rem;
      flex-wrap: wrap;
    }

    .news-sentiment {
      padding: 1px 6px;
      border-radius: 10px;
      font-size: 0.7rem;
      font-weight: 700;
      white-space: nowrap;
    }
    .news-sentiment.pos { background: #e6f4ea; color: #1e7e34; }
    .news-sentiment.neg { background: #fce8e6; color: #c5221f; }
    .news-sentiment.neu { background: #f1f3f4; color: #555; }

    .news-title { color: #1a73e8; text-decoration: none; flex: 1; min-width: 180px; }
    .news-title:hover { text-decoration: underline; }
    .news-pub { color: #888; font-size: 0.75rem; white-space: nowrap; }

    .disclaimer {
      background: #fff3cd;
      color: #856404;
      border: 1px solid #ffc107;
      border-radius: 6px;
      padding: 10px 16px;
      font-size: 0.8rem;
      margin-top: 8px;
      margin-bottom: 32px;
    }

    /* ── History section ── */
    .history-section {
      margin-top: 40px;
    }

    .history-section h3 {
      font-size: 1.3rem;
      color: #1a1a2e;
      margin-bottom: 16px;
      border-bottom: 2px solid #e9ecef;
      padding-bottom: 8px;
    }

    .loading-spinner {
      padding: 20px;
      text-align: center;
      color: #888;
      font-style: italic;
    }

    .no-history {
      padding: 24px;
      text-align: center;
      background: #f9f9f9;
      border-radius: 8px;
      color: #666;
      font-size: 0.9rem;
    }

    .history-table-wrapper {
      max-height: 400px;
      overflow-y: auto;
      border-radius: 8px;
      box-shadow: 0 1px 8px rgba(0,0,0,0.07);
    }

    .history-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.85rem;
      background: #fff;
      border-radius: 8px;
      overflow: hidden;
    }

    .history-table thead tr {
      background: #1a1a2e;
      color: #fff;
      position: sticky;
      top: 0;
      z-index: 1;
    }

    .history-table th {
      padding: 10px 12px;
      text-align: left;
      font-weight: 600;
      font-size: 0.78rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .history-table tbody tr {
      border-bottom: 1px solid #f0f0f0;
      transition: background 0.1s;
    }

    .history-table tbody tr:hover { background: #f9f9f9; }

    .history-table td {
      padding: 9px 12px;
      vertical-align: middle;
    }

    .row-success { border-left: 3px solid #28a745; }
    .row-failed  { border-left: 3px solid #dc3545; }
    .row-pending { border-left: 3px solid #fd7e14; }

    .symbol-cell { font-weight: 700; color: #1a1a2e; }

    .action-pill {
      padding: 2px 8px;
      border-radius: 10px;
      font-size: 0.75rem;
      font-weight: 600;
    }

    .sell-pill  { background: #f8d7da; color: #721c24; }
    .watch-pill { background: #fff3cd; color: #856404; }

    .status-badge {
      padding: 3px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
      font-weight: 600;
    }

    .status-success { background: #d4edda; color: #155724; }
    .status-failed  { background: #f8d7da; color: #721c24; }
    .status-pending { background: #fff3cd; color: #856404; }

    /* ── Stats chips ── */
    .stats-row {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
      margin-top: 16px;
    }

    .stat-chip {
      padding: 6px 14px;
      border-radius: 20px;
      font-size: 0.82rem;
      font-weight: 600;
    }

    .success-chip { background: #d4edda; color: #155724; }
    .failed-chip  { background: #f8d7da; color: #721c24; }
    .pending-chip { background: #fff3cd; color: #856404; }
    .rate-chip    { background: #cce5ff; color: #004085; }

    /* ── Swing trade section ── */
    .swing-section {
      margin-top: 32px;
      margin-bottom: 32px;
    }

    .swing-header-row {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
      flex-wrap: wrap;
      margin-bottom: 16px;
      border-bottom: 2px solid #e9ecef;
      padding-bottom: 8px;
    }

    .swing-header-row h3 {
      font-size: 1.3rem;
      color: #1a1a2e;
      margin: 0 0 4px;
    }

    .swing-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.84rem;
      background: #fff;
      border-radius: 8px;
      overflow: hidden;
      box-shadow: 0 1px 8px rgba(0,0,0,0.07);
    }

    .swing-table thead tr { background: #1a1a2e; color: #fff; }

    .swing-table th {
      padding: 10px 10px;
      text-align: left;
      font-weight: 600;
      font-size: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .swing-table tbody tr { border-bottom: 1px solid #f0f0f0; transition: background 0.1s; }
    .swing-table tbody tr:hover { background: #f9f9f9; }
    .swing-table td { padding: 9px 10px; vertical-align: middle; }

    .swing-hold { border-left: 3px solid #28a745; }
    .swing-sell { border-left: 3px solid #dc3545; }

    .rank-cell { color: #888; font-weight: 700; }

    .hold-pill      { background: #d4edda; color: #155724; }
    .swing-sell-pill { background: #f8d7da; color: #721c24; }

    .strategies-cell { font-size: 0.78rem; color: #555; max-width: 140px; word-break: break-word; }

    .mini-confidence {
      position: relative;
      width: 72px;
      height: 16px;
      background: #e9ecef;
      border-radius: 8px;
      overflow: hidden;
    }

    .mini-fill {
      height: 100%;
      border-radius: 8px;
      transition: width 0.5s;
    }

    .mini-label {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.68rem;
      font-weight: 700;
      color: #333;
    }

    .swing-disclaimer {
      margin-top: 10px;
      font-size: 0.78rem;
      color: #666;
      background: #f0f4f8;
      padding: 8px 14px;
      border-radius: 6px;
    }

    .client-selector-row {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
      margin-top: 14px;
    }

    .client-selector-row label {
      font-weight: 600;
      color: #334155;
    }

    .client-selector-row select {
      min-width: 260px;
      padding: 8px 10px;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      background: #fff;
    }

    .client-selector-hint {
      font-size: 0.85rem;
      color: #64748b;
    }
  `]
})
export class SuggestedTradesComponent implements OnInit {
  clients: Client[] = [];
  selectedClientId: number | null = null;
  isAdminUser = false;
  clientLoading = false;

  suggestions: SuggestedTrade[] = [];
  history: SuggestedTradeHistory[] = [];
  successRate: SuccessRate | null = null;
  loading = false;
  historyLoading = false;
  error: string | null = null;

  swingSuggestions: SwingTradeSuggestion[] = [];
  swingHistory: SwingTradeSuggestion[] = [];
  swingSuccessRate: { totalResolved: number; successCount: number; failedCount: number; pendingCount: number; successRatePct: number } | null = null;
  swingLoading = false;
  swingError: string | null = null;

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.isAdminUser = this.isAdmin();
    if (this.isAdminUser) {
      this.loadClientsForAdmin();
      return;
    }
    this.loadAll();
  }

  loadAll(): void {
    const clientId = this.getActiveClientId();
    if (!clientId) {
      this.error = this.isAdminUser
        ? 'Select a client to view suggested trades.'
        : 'Client ID not found. Please log in again.';
      this.clearData();
      return;
    }
    this.loadSuggestions(clientId);
    this.loadHistory(clientId);
    this.loadSuccessRate(clientId);
    this.loadSwingSuggestions(clientId);
    this.loadSwingHistory(clientId);
    this.loadSwingSuccessRate(clientId);
  }

  refreshSwing(): void {
    const clientId = this.getActiveClientId();
    if (!clientId) return;
    this.loadSwingSuggestions(clientId);
    this.loadSwingHistory(clientId);
    this.loadSwingSuccessRate(clientId);
  }

  onClientChange(): void {
    if (!this.isAdminUser) return;
    this.loadAll();
  }

  private loadClientsForAdmin(): void {
    this.clientLoading = true;
    this.error = null;
    this.apiService.getAllClients().subscribe({
      next: (clients) => {
        this.clients = clients;
        this.clientLoading = false;
        if (clients.length === 0) {
          this.error = 'No clients available.';
          this.clearData();
          return;
        }

        const storedClientId = parseInt(localStorage.getItem('clientId') || '0', 10);
        const matchingClientId = clients.find(client => client.id === storedClientId)?.id ?? clients[0].id ?? null;
        this.selectedClientId = matchingClientId;
        this.loadAll();
      },
      error: (err) => {
        this.clientLoading = false;
        this.error = 'Failed to load clients: ' + (err.error?.message || err.message || 'Unknown error');
        this.clearData();
      }
    });
  }

  private isAdmin(): boolean {
    return typeof window !== 'undefined' && window.localStorage
      ? localStorage.getItem('role') === 'ADMIN'
      : false;
  }

  private getActiveClientId(): number {
    if (this.isAdminUser) {
      return this.selectedClientId ?? 0;
    }
    return parseInt(localStorage.getItem('clientId') || '0', 10);
  }

  private clearData(): void {
    this.suggestions = [];
    this.history = [];
    this.successRate = null;
    this.swingSuggestions = [];
    this.swingHistory = [];
    this.swingSuccessRate = null;
  }

  private loadSuggestions(clientId: number): void {
    this.loading = true;
    this.error = null;
    this.apiService.getSuggestedTrades(clientId).subscribe({
      next: (data) => { this.suggestions = data; this.loading = false; },
      error: (err) => {
        this.error = 'Failed to load suggestions: ' + (err.error?.message || err.message || 'Unknown error');
        this.loading = false;
      }
    });
  }

  private loadHistory(clientId: number): void {
    this.historyLoading = true;
    this.apiService.getSuggestedTradeHistory(clientId).subscribe({
      next: (data) => { this.history = data; this.historyLoading = false; },
      error: () => { this.historyLoading = false; }
    });
  }

  private loadSuccessRate(clientId: number): void {
    this.apiService.getSuggestedTradeSuccessRate(clientId).subscribe({
      next: (data) => { this.successRate = data; },
      error: () => { /* non-critical – silently ignore */ }
    });
  }

  private loadSwingSuggestions(clientId: number): void {
    this.swingLoading = true;
    this.swingError = null;
    this.apiService.getSwingTradeSuggestions(clientId).subscribe({
      next: (data) => { this.swingSuggestions = data; this.swingLoading = false; },
      error: (err) => {
        this.swingError = 'Failed to load swing suggestions: ' + (err.error?.message || err.message || 'Unknown error');
        this.swingLoading = false;
      }
    });
  }

  private loadSwingHistory(clientId: number): void {
    this.apiService.getSwingTradeHistory(clientId).subscribe({
      next: (data) => { this.swingHistory = data; },
      error: () => { /* non-critical */ }
    });
  }

  private loadSwingSuccessRate(clientId: number): void {
    this.apiService.getSwingTradeSuccessRate(clientId).subscribe({
      next: (data) => { this.swingSuccessRate = data; },
      error: () => { /* non-critical */ }
    });
  }

  formatDate(dateStr: string): string {
    const d = new Date(dateStr);
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
      + ' ' + d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
  }
}
