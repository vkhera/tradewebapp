import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../services/api.service';

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
}

@Component({
  selector: 'app-suggested-trades',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>💡 Suggested Trades</h2>
        <p class="subtitle">
          AI-powered suggestions based on ATR(14) and 8-hour price predictions.
          Stocks expected to move more than 2% are shown below (up to 5).
        </p>
        <button class="btn btn-refresh" (click)="loadSuggestions()" [disabled]="loading">
          {{ loading ? '⏳ Loading...' : '🔄 Refresh' }}
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
        </div>
      </div>

      <div *ngIf="!loading && suggestions.length > 0" class="disclaimer">
        ⚠️ These suggestions are generated by predictive models and are for informational purposes only.
        They do not constitute financial advice. Always do your own research before trading.
      </div>
    </div>
  `,
  styles: [`
    .container {
      max-width: 900px;
      margin: 20px auto;
      padding: 20px;
    }

    .page-header {
      margin-bottom: 24px;
    }

    .page-header h2 {
      font-size: 1.8rem;
      color: #1a1a2e;
      margin: 0 0 8px;
    }

    .subtitle {
      color: #666;
      font-size: 0.9rem;
      margin-bottom: 16px;
    }

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

    .metric value {
      font-size: 1rem;
      font-weight: 600;
      color: #1a1a2e;
    }

    .metric value.negative { color: #dc3545; }
    .metric value.positive { color: #28a745; }
    .metric value.muted { color: #aaa; font-weight: 400; }
    .metric value.atr { color: #6f42c1; }

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

    .disclaimer {
      background: #fff3cd;
      color: #856404;
      border: 1px solid #ffc107;
      border-radius: 6px;
      padding: 10px 16px;
      font-size: 0.8rem;
      margin-top: 8px;
    }
  `]
})
export class SuggestedTradesComponent implements OnInit {
  suggestions: SuggestedTrade[] = [];
  loading = false;
  error: string | null = null;

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.loadSuggestions();
  }

  loadSuggestions(): void {
    const clientId = parseInt(localStorage.getItem('clientId') || '0', 10);
    if (!clientId) {
      this.error = 'Client ID not found. Please log in again.';
      return;
    }
    this.loading = true;
    this.error = null;

    this.apiService.getSuggestedTrades(clientId).subscribe({
      next: (data) => {
        this.suggestions = data;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load suggestions: ' + (err.error?.message || err.message || 'Unknown error');
        this.loading = false;
      }
    });
  }
}
