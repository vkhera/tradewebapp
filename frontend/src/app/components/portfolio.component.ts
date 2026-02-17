import { Component, OnInit, Inject, PLATFORM_ID } from '@angular/core';
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
            <td class="symbol">{{ holding.symbol }}</td>
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
    </div>
  `,
  styles: [`
    .portfolio-container {
      max-width: 1200px;
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
    
    h2 {
      margin: 0;
    }
    
    .loading, .empty-state {
      text-align: center;
      padding: 3rem;
      color: #666;
    }
    
    .cash-summary {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 1rem;
      margin-bottom: 2rem;
    }
    
    .cash-card {
      background: white;
      padding: 1.5rem;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
      border-left: 4px solid #667eea;
    }
    
    .cash-label {
      font-size: 0.875rem;
      color: #666;
      margin-bottom: 0.5rem;
      text-transform: uppercase;
      font-weight: 500;
    }
    
    .cash-value {
      font-size: 1.5rem;
      font-weight: 700;
      color: #333;
    }
    
    .cash-value.reserved {
      color: #f59e0b;
    }
    
    .cash-value.available {
      color: #10b981;
    }
    
    .cash-value.portfolio {
      color: #667eea;
    }
    
    .portfolio-table {
      width: 100%;
      border-collapse: collapse;
      background: white;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
      border-radius: 8px;
      overflow: hidden;
    }
    
    th, td {
      padding: 1rem;
      text-align: left;
      border-bottom: 1px solid #eee;
    }
    
    th {
      background: #f8f9fa;
      color: #555;
      font-weight: 600;
      text-transform: uppercase;
      font-size: 0.875rem;
    }
    
    .symbol {
      font-weight: 600;
      color: #667eea;
    }
    
    .profit {
      color: #28a745;
      font-weight: 600;
    }
    
    .loss {
      color: #dc3545;
      font-weight: 600;
    }
    
    .total-row {
      background: #f8f9fa;
      font-size: 1.1rem;
    }
    
    .total-row td {
      border-bottom: none;
    }
  `]
})
export class PortfolioComponent implements OnInit {
  portfolio: any[] = [];
  summary: any = null;
  loading = true;
  totalValue = 0;

  constructor(
    private apiService: ApiService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      const clientId = localStorage.getItem('clientId');
      if (clientId) {
        this.loadPortfolio(parseInt(clientId));
      }
    }
  }

  loadPortfolio(clientId: number) {
    this.apiService.getPortfolioSummary(clientId).subscribe({
      next: (data) => {
        this.summary = data;
        this.portfolio = data.holdings;
        this.totalValue = data.totalPortfolioValue;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading portfolio:', error);
        this.loading = false;
      }
    });
  }

  downloadCSV() {
    const headers = ['Symbol', 'Quantity', 'Avg Price', 'Current Price', 'Total Value', 'P/L', 'P/L %'];
    const csvData = this.portfolio.map(holding => [
      holding.symbol,
      holding.quantity,
      holding.averagePrice.toFixed(2),
      holding.currentPrice.toFixed(2),
      holding.totalValue.toFixed(2),
      holding.profitLoss.toFixed(2),
      holding.profitLossPercent.toFixed(2)
    ]);

    const csvContent = [
      headers.join(','),
      ...csvData.map(row => row.join(',')),
      '',
      `Total Portfolio Value,$${this.totalValue.toFixed(2)}`
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `portfolio-${new Date().toISOString().split('T')[0]}.csv`;
    link.click();
    window.URL.revokeObjectURL(url);
  }
}
