import { Component, OnInit, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { ApiService, Trade } from '../services/api.service';

@Component({
  selector: 'app-order-history',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="order-history-container">
      <div class="header">
        <h2>Order History</h2>
        <button (click)="downloadCSV()" class="download-btn" [disabled]="orders.length === 0">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          Download CSV
        </button>
      </div>
      
      <div *ngIf="loading" class="loading">Loading order history...</div>
      
      <div *ngIf="!loading && orders.length === 0" class="empty-state">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1">
          <rect x="3" y="3" width="18" height="18" rx="2"/>
          <line x1="9" y1="9" x2="15" y2="15"/>
          <line x1="15" y1="9" x2="9" y2="15"/>
        </svg>
        <p>No order history found</p>
      </div>
      
      <div class="card" *ngIf="!loading && orders.length > 0">
        <table>
          <thead>
            <tr>
              <th>Order ID</th>
              <th>Date & Time</th>
              <th>Symbol</th>
              <th>Type</th>
              <th>Order Type</th>
              <th>Quantity</th>
              <th>Price</th>
              <th>Total Value</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let order of orders">
              <td>#{{ order.id }}</td>
              <td>{{ order.tradeTime | date:'medium' }}</td>
              <td class="symbol">{{ order.symbol }}</td>
              <td>
                <span class="badge" [class.buy]="order.type === 'BUY'" [class.sell]="order.type === 'SELL'">
                  {{ order.type }}
                </span>
              </td>
              <td>
                <span class="order-type-badge" [class.market]="order.orderType === 'MARKET'" 
                      [class.limit]="order.orderType === 'LIMIT'">
                  {{ order.orderType || 'MARKET' }}
                </span>
              </td>
              <td>{{ order.quantity }}</td>
              <td>\${{ order.price | number:'1.2-2' }}</td>
              <td>\${{ (order.quantity * order.price) | number:'1.2-2' }}</td>
              <td>
                <span class="status-badge" [class.executed]="order.status === 'EXECUTED'" 
                      [class.rejected]="order.status === 'REJECTED'"
                      [class.pending]="order.status === 'PENDING'">
                  {{ order.status }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: [`
    .order-history-container {
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
    
    .loading, .empty-state {
      text-align: center;
      padding: 4rem 2rem;
      color: #64748b;
    }
    
    .empty-state svg {
      color: #cbd5e1;
      margin-bottom: 16px;
    }
    
    .symbol {
      font-weight: 600;
      color: #667eea;
    }
    
    .badge {
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
    }
    
    .badge.buy {
      background: #d1fae5;
      color: #065f46;
    }
    
    .badge.sell {
      background: #fee2e2;
      color: #991b1b;
    }
    
    .order-type-badge {
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
    }
    
    .order-type-badge.market {
      background: #dbeafe;
      color: #1e40af;
    }
    
    .order-type-badge.limit {
      background: #fef3c7;
      color: #92400e;
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
    
    .status-badge.pending {
      background: #fef3c7;
      color: #92400e;
    }
  `]
})
export class OrderHistoryComponent implements OnInit {
  orders: Trade[] = [];
  loading = true;

  constructor(
    private apiService: ApiService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      this.loadOrderHistory();
    }
  }

  loadOrderHistory() {
    const clientId = localStorage.getItem('clientId');
    if (clientId) {
      this.apiService.getTradesByClient(parseInt(clientId)).subscribe({
        next: (data) => {
          this.orders = data.sort((a, b) => {
            const timeA = a.tradeTime ? new Date(a.tradeTime).getTime() : 0;
            const timeB = b.tradeTime ? new Date(b.tradeTime).getTime() : 0;
            return timeB - timeA;
          });
          this.loading = false;
        },
        error: (error) => {
          console.error('Error loading order history:', error);
          this.loading = false;
        }
      });
    }
  }

  downloadCSV() {
    const headers = ['Order ID', 'Date & Time', 'Symbol', 'Type', 'Order Type', 'Quantity', 'Price', 'Total Value', 'Status'];
    const csvData = this.orders.map(order => [
      order.id,
      order.tradeTime,
      order.symbol,
      order.type,
      order.orderType || 'MARKET',
      order.quantity,
      order.price,
      (order.quantity * order.price).toFixed(2),
      order.status
    ]);

    const csvContent = [
      headers.join(','),
      ...csvData.map(row => row.join(','))
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `order-history-${new Date().toISOString().split('T')[0]}.csv`;
    link.click();
    window.URL.revokeObjectURL(url);
  }
}
