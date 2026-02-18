import { Component, OnInit, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { ApiService, Trade } from '../services/api.service';

interface RealizedGain {
  symbol: string;
  buyTrade: Trade;
  sellTrade: Trade;
  quantity: number;
  buyPrice: number;
  sellPrice: number;
  gainLoss: number;
  gainLossPercent: number;
}

@Component({
  selector: 'app-realized-gains',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="realized-gains-container">
      <div class="header">
        <h2>Realized Gains/Losses</h2>
        <button (click)="downloadCSV()" class="download-btn" [disabled]="realizedGains.length === 0">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          Download CSV
        </button>
      </div>
      
      <div class="summary-cards">
        <div class="summary-card gain">
          <div class="card-icon">↑</div>
          <div class="card-content">
            <div class="card-label">Total Realized Gains</div>
            <div class="card-value">\${{ totalGains | number:'1.2-2' }}</div>
          </div>
        </div>
        <div class="summary-card loss">
          <div class="card-icon">↓</div>
          <div class="card-content">
            <div class="card-label">Total Realized Losses</div>
            <div class="card-value">\${{ Math.abs(totalLosses) | number:'1.2-2' }}</div>
          </div>
        </div>
        <div class="summary-card net" [class.positive]="netGainLoss >= 0" [class.negative]="netGainLoss < 0">
          <div class="card-icon">{{ netGainLoss >= 0 ? '✓' : '✗' }}</div>
          <div class="card-content">
            <div class="card-label">Net Realized Gain/Loss</div>
            <div class="card-value">\${{ netGainLoss | number:'1.2-2' }}</div>
          </div>
        </div>
      </div>
      
      <div *ngIf="loading" class="loading">Calculating realized gains...</div>
      
      <div *ngIf="!loading && realizedGains.length === 0" class="empty-state">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1">
          <line x1="18" y1="20" x2="18" y2="10"/>
          <line x1="12" y1="20" x2="12" y2="4"/>
          <line x1="6" y1="20" x2="6" y2="14"/>
        </svg>
        <p>No realized gains/losses yet</p>
        <p class="hint">Realized gains occur when you sell shares</p>
      </div>
      
      <div class="card" *ngIf="!loading && realizedGains.length > 0">
        <table>
          <thead>
            <tr>
              <th>Symbol</th>
              <th>Buy Date</th>
              <th>Sell Date</th>
              <th>Quantity</th>
              <th>Buy Price</th>
              <th>Sell Price</th>
              <th>Gain/Loss</th>
              <th>Gain/Loss %</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let gain of realizedGains">
              <td class="symbol">{{ gain.symbol }}</td>
              <td>{{ gain.buyTrade.tradeTime | date:'short' }}</td>
              <td>{{ gain.sellTrade.tradeTime | date:'short' }}</td>
              <td>{{ gain.quantity }}</td>
              <td>\${{ gain.buyPrice | number:'1.2-2' }}</td>
              <td>\${{ gain.sellPrice | number:'1.2-2' }}</td>
              <td [class.profit]="gain.gainLoss >= 0" [class.loss]="gain.gainLoss < 0">
                <strong>\${{ gain.gainLoss | number:'1.2-2' }}</strong>
              </td>
              <td [class.profit]="gain.gainLossPercent >= 0" [class.loss]="gain.gainLossPercent < 0">
                <strong>{{ gain.gainLossPercent | number:'1.2-2' }}%</strong>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: [`
    .realized-gains-container {
      max-width: 1400px;
      margin: 0 auto;
    }
    
    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
    }
    
    .download-btn {
      display: flex;
      align-items: center;
      gap: 8px;
      background: #10b981;
      padding: 10px 20px;
    }
    
    .download-btn:hover:not(:disabled) {
      background: #059669;
    }
    
    .summary-cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 20px;
      margin-bottom: 32px;
    }
    
    .summary-card {
      background: white;
      border-radius: 12px;
      padding: 24px;
      display: flex;
      align-items: center;
      gap: 20px;
      box-shadow: 0 4px 6px rgba(0, 0, 0, 0.07);
      transition: all 0.3s ease;
    }
    
    .summary-card:hover {
      transform: translateY(-4px);
      box-shadow: 0 8px 16px rgba(0, 0, 0, 0.12);
    }
    
    .summary-card.gain {
      border-left: 4px solid #10b981;
    }
    
    .summary-card.loss {
      border-left: 4px solid #ef4444;
    }
    
    .summary-card.net.positive {
      border-left: 4px solid #10b981;
      background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
    }
    
    .summary-card.net.negative {
      border-left: 4px solid #ef4444;
      background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
    }
    
    .card-icon {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      font-weight: bold;
      background: #f3f4f6;
      color: #6b7280;
    }
    
    .summary-card.gain .card-icon {
      background: #d1fae5;
      color: #10b981;
    }
    
    .summary-card.loss .card-icon {
      background: #fee2e2;
      color: #ef4444;
    }
    
    .summary-card.net.positive .card-icon {
      background: #10b981;
      color: white;
    }
    
    .summary-card.net.negative .card-icon {
      background: #ef4444;
      color: white;
    }
    
    .card-content {
      flex: 1;
    }
    
    .card-label {
      font-size: 14px;
      color: #6b7280;
      margin-bottom: 4px;
    }
    
    .card-value {
      font-size: 28px;
      font-weight: 700;
      color: #1a202c;
    }
    
    .loading, .empty-state {
      text-align: center;
      padding: 4rem 2rem;
      color: #64748b;
    }
    
    .empty-state svg {
      color: #cbd5e1;
      margin-bottom: 16px;
    }
    
    .empty-state .hint {
      font-size: 13px;
      color: #94a3b8;
      margin-top: 8px;
    }
    
    .symbol {
      font-weight: 600;
      color: #667eea;
    }
    
    .profit {
      color: #10b981;
      font-weight: 600;
    }
    
    .loss {
      color: #ef4444;
      font-weight: 600;
    }
    
    .badge {
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 600;
    }
    
    .badge.buy {
      background: #d1fae5;
      color: #065f46;
    }
    
    .badge.sell {
      background: #fee2e2;
      color: #991b1b;
    }
    
    .status-badge {
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 600;
    }
    
    .status-badge.executed {
      background: #d1fae5;
      color: #065f46;
    }
    
    .status-badge.rejected {
      background: #fee2e2;
      color: #991b1b;
    }
  `]
})
export class RealizedGainsComponent implements OnInit {
  realizedGains: RealizedGain[] = [];
  loading = true;
  totalGains = 0;
  totalLosses = 0;
  netGainLoss = 0;
  Math = Math;

  constructor(
    private apiService: ApiService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      this.loadRealizedGains();
    }
  }

  loadRealizedGains() {
    const clientId = localStorage.getItem('clientId');
    if (clientId) {
      this.apiService.getTradesByClient(parseInt(clientId)).subscribe({
        next: (trades) => {
          this.calculateRealizedGains(trades);
          this.loading = false;
        },
        error: (error) => {
          console.error('Error loading trades:', error);
          this.loading = false;
        }
      });
    }
  }

  calculateRealizedGains(trades: Trade[]) {
    const executedTrades = trades.filter(t => t.status === 'EXECUTED');
    const buyTrades = executedTrades.filter(t => t.type === 'BUY');
    const sellTrades = executedTrades.filter(t => t.type === 'SELL');
    
    const gains: RealizedGain[] = [];
    
    // Group by symbol
    const symbolMap = new Map<string, { buys: Trade[], sells: Trade[] }>();
    
    buyTrades.forEach(buy => {
      if (!symbolMap.has(buy.symbol)) {
        symbolMap.set(buy.symbol, { buys: [], sells: [] });
      }
      symbolMap.get(buy.symbol)!.buys.push(buy);
    });
    
    sellTrades.forEach(sell => {
      if (!symbolMap.has(sell.symbol)) {
        symbolMap.set(sell.symbol, { buys: [], sells: [] });
      }
      symbolMap.get(sell.symbol)!.sells.push(sell);
    });
    
    // Calculate realized gains for each sell
    symbolMap.forEach((trades, symbol) => {
      trades.sells.forEach(sell => {
        // Match with earliest buy (FIFO)
        const matchingBuys = trades.buys.filter(b => {
          const buyTime = b.tradeTime ? new Date(b.tradeTime).getTime() : 0;
          const sellTime = sell.tradeTime ? new Date(sell.tradeTime).getTime() : 0;
          return buyTime < sellTime;
        });
        
        if (matchingBuys.length > 0) {
          const avgBuyPrice = matchingBuys.reduce((sum, b) => sum + b.price, 0) / matchingBuys.length;
          const gainLoss = (sell.price - avgBuyPrice) * sell.quantity;
          const gainLossPercent = ((sell.price - avgBuyPrice) / avgBuyPrice) * 100;
          
          gains.push({
            symbol,
            buyTrade: matchingBuys[0],
            sellTrade: sell,
            quantity: sell.quantity,
            buyPrice: avgBuyPrice,
            sellPrice: sell.price,
            gainLoss,
            gainLossPercent
          });
        }
      });
    });
    
    this.realizedGains = gains.sort((a, b) => {
      const timeA = a.sellTrade.tradeTime ? new Date(a.sellTrade.tradeTime).getTime() : 0;
      const timeB = b.sellTrade.tradeTime ? new Date(b.sellTrade.tradeTime).getTime() : 0;
      return timeB - timeA;
    });
    
    this.totalGains = gains.filter(g => g.gainLoss > 0).reduce((sum, g) => sum + g.gainLoss, 0);
    this.totalLosses = gains.filter(g => g.gainLoss < 0).reduce((sum, g) => sum + g.gainLoss, 0);
    this.netGainLoss = this.totalGains + this.totalLosses;
  }

  downloadCSV() {
    const headers = ['Symbol', 'Buy Date', 'Sell Date', 'Quantity', 'Buy Price', 'Sell Price', 'Gain/Loss', 'Gain/Loss %'];
    const csvData = this.realizedGains.map(gain => [
      gain.symbol,
      gain.buyTrade.tradeTime || '',
      gain.sellTrade.tradeTime || '',
      gain.quantity,
      gain.buyPrice.toFixed(2),
      gain.sellPrice.toFixed(2),
      gain.gainLoss.toFixed(2),
      gain.gainLossPercent.toFixed(2)
    ]);

    const csvContent = [
      headers.join(','),
      ...csvData.map(row => row.join(','))
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `realized-gains-${new Date().toISOString().split('T')[0]}.csv`;
    link.click();
    window.URL.revokeObjectURL(url);
  }
}
