import { Component, OnInit, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { ApiService } from '../services/api.service';

interface UnrealizedGain {
  symbol: string;
  quantity: number;
  averagePrice: number;
  currentPrice: number;
  totalCost: number;
  currentValue: number;
  unrealizedGainLoss: number;
  unrealizedGainLossPercent: number;
}

@Component({
  selector: 'app-unrealized-gains',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="unrealized-gains-container">
      <div class="header">
        <h2>Unrealized Gains/Losses</h2>
        <button (click)="downloadCSV()" class="download-btn" [disabled]="holdings.length === 0">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          Download CSV
        </button>
      </div>
      
      <div class="summary-cards">
        <div class="summary-card total-cost">
          <div class="card-icon">💰</div>
          <div class="card-content">
            <div class="card-label">Total Investment</div>
            <div class="card-value">\${{ totalCost | number:'1.2-2' }}</div>
          </div>
        </div>
        <div class="summary-card current-value">
          <div class="card-icon">📊</div>
          <div class="card-content">
            <div class="card-label">Current Value</div>
            <div class="card-value">\${{ totalCurrentValue | number:'1.2-2' }}</div>
          </div>
        </div>
        <div class="summary-card net" [class.positive]="totalUnrealizedGainLoss >= 0" [class.negative]="totalUnrealizedGainLoss < 0">
          <div class="card-icon">{{ totalUnrealizedGainLoss >= 0 ? '↗' : '↘' }}</div>
          <div class="card-content">
            <div class="card-label">Unrealized Gain/Loss</div>
            <div class="card-value">\${{ totalUnrealizedGainLoss | number:'1.2-2' }}</div>
            <div class="card-subvalue">{{ totalUnrealizedGainLossPercent | number:'1.2-2' }}%</div>
          </div>
        </div>
      </div>
      
      <div *ngIf="loading" class="loading">Loading unrealized gains...</div>
      
      <div *ngIf="!loading && holdings.length === 0" class="empty-state">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1">
          <rect x="3" y="3" width="18" height="18" rx="2"/>
          <path d="M9 3v18"/>
        </svg>
        <p>No current holdings</p>
        <p class="hint">Unrealized gains show profit/loss on your current positions</p>
      </div>
      
      <div class="card" *ngIf="!loading && holdings.length > 0">
        <table>
          <thead>
            <tr>
              <th>Symbol</th>
              <th>Quantity</th>
              <th>Avg Buy Price</th>
              <th>Current Price</th>
              <th>Total Cost</th>
              <th>Current Value</th>
              <th>Unrealized Gain/Loss</th>
              <th>Gain/Loss %</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let holding of holdings">
              <td class="symbol">{{ holding.symbol }}</td>
              <td>{{ holding.quantity }}</td>
              <td>\${{ holding.averagePrice | number:'1.2-2' }}</td>
              <td>\${{ holding.currentPrice | number:'1.2-2' }}</td>
              <td>\${{ holding.totalCost | number:'1.2-2' }}</td>
              <td>\${{ holding.currentValue | number:'1.2-2' }}</td>
              <td [class.profit]="holding.unrealizedGainLoss >= 0" [class.loss]="holding.unrealizedGainLoss < 0">
                <strong>\${{ holding.unrealizedGainLoss | number:'1.2-2' }}</strong>
              </td>
              <td [class.profit]="holding.unrealizedGainLossPercent >= 0" [class.loss]="holding.unrealizedGainLossPercent < 0">
                <strong>{{ holding.unrealizedGainLossPercent | number:'1.2-2' }}%</strong>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: [`
    .unrealized-gains-container {
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
    
    .summary-card.total-cost {
      border-left: 4px solid #3b82f6;
    }
    
    .summary-card.current-value {
      border-left: 4px solid #8b5cf6;
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
      width: 56px;
      height: 56px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 28px;
      background: #f3f4f6;
    }
    
    .card-content {
      flex: 1;
    }
    
    .card-label {
      font-size: 14px;
      color: #6b7280;
      margin-bottom: 4px;
      font-weight: 500;
    }
    
    .card-value {
      font-size: 28px;
      font-weight: 700;
      color: #1a202c;
      line-height: 1;
    }
    
    .card-subvalue {
      font-size: 16px;
      font-weight: 600;
      color: #4b5563;
      margin-top: 4px;
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
    }
    
    .loss {
      color: #ef4444;
    }
  `]
})
export class UnrealizedGainsComponent implements OnInit {
  holdings: UnrealizedGain[] = [];
  loading = true;
  totalCost = 0;
  totalCurrentValue = 0;
  totalUnrealizedGainLoss = 0;
  totalUnrealizedGainLossPercent = 0;
  Math = Math;

  constructor(
    private apiService: ApiService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      this.loadUnrealizedGains();
    }
  }

  loadUnrealizedGains() {
    const clientId = localStorage.getItem('clientId');
    if (clientId) {
      this.apiService.getPortfolio(parseInt(clientId)).subscribe({
        next: (data) => {
          this.holdings = data.map((item: any) => ({
            symbol: item.symbol,
            quantity: item.quantity,
            averagePrice: item.averagePrice,
            currentPrice: item.currentPrice,
            totalCost: item.quantity * item.averagePrice,
            currentValue: item.totalValue,
            unrealizedGainLoss: item.profitLoss,
            unrealizedGainLossPercent: item.profitLossPercent
          }));
          
          this.totalCost = this.holdings.reduce((sum, h) => sum + h.totalCost, 0);
          this.totalCurrentValue = this.holdings.reduce((sum, h) => sum + h.currentValue, 0);
          this.totalUnrealizedGainLoss = this.totalCurrentValue - this.totalCost;
          this.totalUnrealizedGainLossPercent = this.totalCost > 0 
            ? (this.totalUnrealizedGainLoss / this.totalCost) * 100 
            : 0;
          
          this.loading = false;
        },
        error: (error) => {
          console.error('Error loading unrealized gains:', error);
          this.loading = false;
        }
      });
    }
  }

  downloadCSV() {
    const headers = ['Symbol', 'Quantity', 'Avg Buy Price', 'Current Price', 'Total Cost', 'Current Value', 'Unrealized Gain/Loss', 'Gain/Loss %'];
    const csvData = this.holdings.map(holding => [
      holding.symbol,
      holding.quantity,
      holding.averagePrice.toFixed(2),
      holding.currentPrice.toFixed(2),
      holding.totalCost.toFixed(2),
      holding.currentValue.toFixed(2),
      holding.unrealizedGainLoss.toFixed(2),
      holding.unrealizedGainLossPercent.toFixed(2)
    ]);

    const csvContent = [
      headers.join(','),
      ...csvData.map(row => row.join(','))
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `unrealized-gains-${new Date().toISOString().split('T')[0]}.csv`;
    link.click();
    window.URL.revokeObjectURL(url);
  }
}
