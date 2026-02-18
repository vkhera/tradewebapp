import { Component, OnInit, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, Trade } from '../services/api.service';

@Component({
  selector: 'app-trade',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="trade-container">
      <h2>Execute Trade</h2>
      
      <div *ngIf="account" class="account-summary">
        <div class="balance-card">
          <div class="balance-item">
            <span class="label">Available Funds</span>
            <span class="amount available">\${{ account.availableBalance | number:'1.2-2' }}</span>
          </div>
          <div class="balance-item">
            <span class="label">Blocked for Open Orders</span>
            <span class="amount blocked">\${{ account.reservedBalance | number:'1.2-2' }}</span>
          </div>
        </div>
      </div>
      
      <div class="trade-form">
        <div class="form-group">
          <label>Client ID:</label>
          <input type="number" [(ngModel)]="trade.clientId" placeholder="Enter client ID">
        </div>
        
        <div class="form-group">
          <label>Symbol:</label>
          <input type="text" [(ngModel)]="trade.symbol" placeholder="e.g., AAPL" maxlength="10" 
                 (blur)="onSymbolChange()" (ngModelChange)="onSymbolInput()">
          <div *ngIf="priceError" class="error-text">{{ priceError }}</div>
        </div>
        
        <div class="form-group">
          <label>Quantity:</label>
          <input type="number" [(ngModel)]="trade.quantity" placeholder="Number of shares"
                 (ngModelChange)="calculateExpectedAmount()">
        </div>
        
        <div class="form-group">
          <label>Order Type:</label>
          <select [(ngModel)]="trade.orderType" (change)="onOrderTypeChange()">
            <option value="MARKET">Market Order</option>
            <option value="LIMIT">Limit Order</option>
          </select>
          <small class="help-text" *ngIf="trade.orderType === 'MARKET'">Executes immediately at current market price</small>
          <small class="help-text" *ngIf="trade.orderType === 'LIMIT'">Executes only when price reaches your limit</small>
        </div>
        
        <div class="form-group" *ngIf="trade.orderType === 'LIMIT'">
          <label>Limit Price:</label>
          <input type="number" step="0.01" [(ngModel)]="trade.price" placeholder="Price per share"
                 (ngModelChange)="calculateExpectedAmount()">
        </div>
        
        <div class="form-group" *ngIf="trade.orderType === 'MARKET' && currentMarketPrice">
          <label>Current Market Price:</label>
          <div class="market-price">\${{ currentMarketPrice | number:'1.2-2' }}</div>
        </div>
                <div *ngIf="expectedTradeAmount > 0" class="expected-amount">
          <div class="amount-label">Expected Trade Amount</div>
          <div class="amount-value">\${{ expectedTradeAmount | number:'1.2-2' }}</div>
          <div *ngIf="account && expectedTradeAmount > account.availableBalance && trade.type === 'BUY'" class="warning-text">
            ⚠️ Insufficient funds. Available: \${{ account.availableBalance | number:'1.2-2' }}
          </div>
        </div>
                <div class="form-group">
          <label>Type:</label>
          <select [(ngModel)]="trade.type">
            <option value="BUY">BUY</option>
            <option value="SELL">SELL</option>
          </select>
        </div>
        
        <button (click)="executeTrade()" [disabled]="isExecuting || !isFormValid()">
          {{ isExecuting ? 'Executing...' : 'Execute Trade' }}
        </button>
      </div>
      
      <div *ngIf="lastResult" class="result" [ngClass]="lastResult.status === 'EXECUTED' ? 'success' : 'error'">
        <h3>Trade Result</h3>
        <p><strong>Status:</strong> {{ lastResult.status }}</p>
        <p><strong>Trade ID:</strong> {{ lastResult.id }}</p>
        <p *ngIf="lastResult.orderType"><strong>Order Type:</strong> {{ lastResult.orderType }}</p>
        <p *ngIf="lastResult.fraudCheckReason"><strong>Reason:</strong> {{ lastResult.fraudCheckReason }}</p>
      </div>
      
      <div class="recent-trades">
        <div class="section-header">
          <h3>Recent Trades</h3>
          <button (click)="downloadCSV()" class="download-btn-small" [disabled]="recentTrades.length === 0">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            CSV
          </button>
        </div>
        <table *ngIf="recentTrades.length > 0">
          <thead>
            <tr>
              <th>ID</th>
              <th>Client</th>
              <th>Symbol</th>
              <th>Quantity</th>
              <th>Price</th>
              <th>Type</th>
              <th>Order Type</th>
              <th>Status</th>
              <th>Time</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let trade of recentTrades">
              <td>{{ trade.id }}</td>
              <td>{{ trade.clientId }}</td>
              <td>{{ trade.symbol }}</td>
              <td>{{ trade.quantity }}</td>
              <td>{{ trade.price | number:'1.2-2' }}</td>
              <td>{{ trade.type }}</td>
              <td>{{ trade.orderType || 'MARKET' }}</td>
              <td>{{ trade.status }}</td>
              <td>{{ trade.tradeTime | date:'short' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: [`
    .trade-container {
      padding: 20px;
      max-width: 1200px;
      margin: 0 auto;
    }
    
    .account-summary {
      margin-bottom: 1.5rem;
    }
    
    .balance-card {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
      background: white;
      padding: 1.5rem;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    
    .balance-item {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    
    .balance-item .label {
      font-size: 0.875rem;
      color: #666;
      font-weight: 500;
    }
    
    .balance-item .amount {
      font-size: 1.5rem;
      font-weight: 700;
    }
    
    .balance-item .amount.available {
      color: #10b981;
    }
    
    .balance-item .amount.blocked {
      color: #f59e0b;
    }
    
    .expected-amount {
      background: linear-gradient(135deg, #667eea15 0%, #764ba215 100%);
      border: 2px solid #667eea;
      border-radius: 8px;
      padding: 1rem;
      margin: 1rem 0;
      text-align: center;
    }
    
    .amount-label {
      font-size: 0.875rem;
      color: #666;
      margin-bottom: 0.5rem;
      font-weight: 500;
    }
    
    .amount-value {
      font-size: 2rem;
      font-weight: 700;
      color: #667eea;
    }
    
    .error-text {
      color: #dc3545;
      font-size: 0.875rem;
      margin-top: 0.25rem;
      font-weight: 500;
    }
    
    .warning-text {
      color: #f59e0b;
      font-size: 0.875rem;
      margin-top: 0.5rem;
      font-weight: 500;
    }
    
    .trade-form {
      background: #f5f5f5;
      padding: 20px;
      border-radius: 8px;
      margin-bottom: 30px;
    }
    
    .form-group {
      margin-bottom: 15px;
    }
    
    .form-group label {
      display: block;
      margin-bottom: 5px;
      font-weight: bold;
    }
    
    .form-group input, .form-group select {
      width: 100%;
      padding: 8px;
      border: 1px solid #ddd;
      border-radius: 4px;
    }
    
    .help-text {
      display: block;
      margin-top: 0.25rem;
      font-size: 0.875rem;
      color: #666;
      font-style: italic;
    }
    
    .market-price {
      font-size: 1.5rem;
      font-weight: 600;
      color: #667eea;
      padding: 0.5rem;
      background: #f8f9fa;
      border-radius: 4px;
      text-align: center;
    }
    
    button {
      background: #007bff;
      color: white;
      padding: 10px 20px;
      border: none;
      border-radius: 4px;
      cursor: pointer;
    }
    
    button:disabled {
      background: #ccc;
      cursor: not-allowed;
    }
    
    .result {
      padding: 15px;
      border-radius: 4px;
      margin-bottom: 20px;
    }
    
    .result.success {
      background: #d4edda;
      border: 1px solid #c3e6cb;
      color: #155724;
    }
    
    .result.error {
      background: #f8d7da;
      border: 1px solid #f5c6cb;
      color: #721c24;
    }
    
    table {
      width: 100%;
      border-collapse: collapse;
      background: white;
    }
    
    th, td {
      padding: 12px;
      text-align: left;
      border-bottom: 1px solid #ddd;
    }
    
    th {
      background: #007bff;
      color: white;
    }
    
    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }
    
    .section-header h3 {
      margin: 0;
    }
    
    .download-btn-small {
      display: flex;
      align-items: center;
      gap: 6px;
      background: #10b981;
      padding: 6px 14px;
      font-size: 13px;
    }
    
    .download-btn-small:hover:not(:disabled) {
      background: #059669;
    }
    
    @media (max-width: 768px) {
      .balance-card {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class TradeComponent implements OnInit {
  trade: Trade = {
    clientId: 0,
    symbol: '',
    quantity: 0,
    price: 0,
    type: 'BUY',
    orderType: 'MARKET'
  };
  
  lastResult: any = null;
  recentTrades: Trade[] = [];
  isExecuting = false;
  currentMarketPrice: number | null = null;
  account: any = null;
  priceError: string = '';
  expectedTradeAmount: number = 0;

  constructor(
    private apiService: ApiService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit() {
    this.loadRecentTrades();
    this.loadAccount();
  }
  
  loadAccount() {
    if (isPlatformBrowser(this.platformId)) {
      const clientId = localStorage.getItem('clientId');
      if (clientId) {
        this.apiService.getAccount(parseInt(clientId)).subscribe({
          next: (data) => {
            this.account = data;
          },
          error: (error) => {
            console.error('Failed to load account', error);
          }
        });
      }
    }
  }
  
  onSymbolInput() {
    this.priceError = '';
    this.currentMarketPrice = null;
    this.expectedTradeAmount = 0;
  }
  
  onSymbolChange() {
    if (this.trade.symbol && this.trade.symbol.trim()) {
      this.fetchMarketPrice(this.trade.symbol.toUpperCase());
    }
  }

  onOrderTypeChange() {
    if (this.trade.orderType === 'MARKET') {
      // Fetch current market price for market orders
      if (this.trade.symbol && this.trade.symbol.trim()) {
        this.fetchMarketPrice(this.trade.symbol.toUpperCase());
      }
    } else {
      this.currentMarketPrice = null;
      this.calculateExpectedAmount();
    }
  }

  fetchMarketPrice(symbol: string) {
    this.priceError = '';
    this.apiService.getStockPrice(symbol).subscribe({
      next: (priceData) => {
        if (priceData.price && priceData.price > 0) {
          this.currentMarketPrice = priceData.price || priceData.currentPrice || priceData.lastPrice;
          if (this.trade.orderType === 'MARKET') {
            this.trade.price = this.currentMarketPrice!;
          }
          this.calculateExpectedAmount();
          this.priceError = '';
        } else {
          this.currentMarketPrice = null;
          this.priceError = 'Ticker ' + symbol + ' not available in Yahoo Finance';
          this.expectedTradeAmount = 0;
        }
      },
      error: (error) => {
        console.error('Failed to fetch market price', error);
        this.currentMarketPrice = null;
        this.priceError = 'Ticker ' + symbol + ' not available in Yahoo Finance';
        this.expectedTradeAmount = 0;
      }
    });
  }
  
  calculateExpectedAmount() {
    if (this.trade.quantity > 0) {
      const price = this.trade.orderType === 'MARKET' && this.currentMarketPrice 
        ? this.currentMarketPrice 
        : this.trade.price;
      this.expectedTradeAmount = this.trade.quantity * price;
    } else {
      this.expectedTradeAmount = 0;
    }
  }

  isFormValid(): boolean {
    if (!this.trade.clientId || !this.trade.symbol || !this.trade.quantity) {
      return false;
    }
    
    if (this.trade.orderType === 'LIMIT' && (!this.trade.price || this.trade.price <= 0)) {
      return false;
    }
    
    return true;
  }

  executeTrade() {
    this.isExecuting = true;
    
    // For market orders, fetch the latest price before executing
    if (this.trade.orderType === 'MARKET' && this.trade.symbol) {
      this.apiService.getStockPrice(this.trade.symbol).subscribe({
        next: (priceData) => {
          this.trade.price = priceData.price || priceData.currentPrice || priceData.lastPrice || this.trade.price;
          this.submitTrade();
        },
        error: (error) => {
          console.error('Failed to fetch market price, using entered price', error);
          this.submitTrade();
        }
      });
    } else {
      this.submitTrade();
    }
  }

  submitTrade() {
    this.apiService.executeTrade(this.trade).subscribe({
      next: (result) => {
        this.lastResult = result;
        this.isExecuting = false;
        this.loadRecentTrades();
        this.loadAccount(); // Reload account to show updated balances
        this.resetForm();
      },
      error: (error) => {
        console.error('Trade execution failed', error);
        this.isExecuting = false;
        this.lastResult = { status: 'FAILED', reason: error.message };
      }
    });
  }

  loadRecentTrades() {
    const clientId = localStorage.getItem('clientId');
    if (clientId) {
      this.apiService.getTradesByClient(parseInt(clientId)).subscribe({
        next: (trades) => {
          this.recentTrades = trades.slice(-10).reverse();
        },
        error: (error) => console.error('Failed to load trades', error)
      });
    }
  }

  resetForm() {
    this.trade = {
      clientId: 0,
      symbol: '',
      quantity: 0,
      price: 0,
      type: 'BUY',
      orderType: 'MARKET'
    };
    this.currentMarketPrice = null;
    this.expectedTradeAmount = 0;
    this.priceError = '';
  }

  downloadCSV() {
    const headers = ['ID', 'Client', 'Symbol', 'Quantity', 'Price', 'Type', 'Order Type', 'Status', 'Time'];
    const csvData = this.recentTrades.map(trade => [
      trade.id || '',
      trade.clientId,
      trade.symbol,
      trade.quantity,
      trade.price,
      trade.type,
      trade.orderType || 'MARKET',
      trade.status || '',
      trade.tradeTime || ''
    ]);

    const csvContent = [
      headers.join(','),
      ...csvData.map(row => row.join(','))
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `recent-trades-${new Date().toISOString().split('T')[0]}.csv`;
    link.click();
    window.URL.revokeObjectURL(url);
  }
}
