import { Component, OnInit, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../services/api.service';

@Component({
  selector: 'app-fund-account',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="fund-container">
      <h2>Fund Account</h2>
      
      <div *ngIf="loading" class="loading">Loading account...</div>
      
      <div *ngIf="!loading && !account" class="empty-state">
        <p>Please log in to view your account.</p>
      </div>
      
      <div *ngIf="!loading && account" class="account-info">
        <div class="balance-card">
          <div class="balance-item">
            <span class="label">Total Cash Balance</span>
            <span class="amount">\${{ account.cashBalance | number:'1.2-2' }}</span>
          </div>
          
          <div class="balance-item">
            <span class="label">Reserved for Pending Orders</span>
            <span class="amount reserved">\${{ account.reservedBalance | number:'1.2-2' }}</span>
          </div>
          
          <div class="balance-item highlight">
            <span class="label">Available Balance</span>
            <span class="amount available">\${{ account.availableBalance | number:'1.2-2' }}</span>
          </div>
        </div>
        
        <div class="funds-actions">
          <div class="add-funds-section">
            <h3>Add Funds</h3>
            <form (ngSubmit)="addFunds()" #addFundsForm="ngForm">
              <div class="form-group">
                <label for="amountToAdd">Amount to Add</label>
                <input 
                  type="number" 
                  id="amountToAdd" 
                  [(ngModel)]="amountToAdd" 
                  name="amountToAdd" 
                  min="0.01"
                  step="0.01"
                  required
                  placeholder="Enter amount">
              </div>
              
              <button type="submit" class="btn-primary" [disabled]="!addFundsForm.valid || isAdding">
                {{ isAdding ? 'Processing...' : 'Add Funds' }}
              </button>
            </form>
          </div>

          <div class="withdraw-funds-section">
            <h3>Withdraw Funds</h3>
            <form (ngSubmit)="withdrawFunds()" #withdrawFundsForm="ngForm">
              <div class="form-group">
                <label for="amountToWithdraw">Amount to Withdraw</label>
                <input 
                  type="number" 
                  id="amountToWithdraw" 
                  [(ngModel)]="amountToWithdraw" 
                  name="amountToWithdraw" 
                  min="0.01"
                  step="0.01"
                  [max]="account.availableBalance"
                  required
                  placeholder="Enter amount">
                <small class="help-text">Maximum: \${{ account.availableBalance | number:'1.2-2' }}</small>
              </div>
              
              <button type="submit" class="btn-secondary" [disabled]="!withdrawFundsForm.valid || isWithdrawing || amountToWithdraw > account.availableBalance">
                {{ isWithdrawing ? 'Processing...' : 'Withdraw Funds' }}
              </button>
            </form>
          </div>
        </div>

        <div *ngIf="successMessage" class="success-message">
          {{ successMessage }}
        </div>
        
        <div *ngIf="errorMessage" class="error-message">
          {{ errorMessage }}
        </div>
      </div>
    </div>
  `,
  styles: [`
    .fund-container {
      padding: 2rem;
      max-width: 800px;
      margin: 0 auto;
    }
    
    h2 {
      color: #333;
      margin-bottom: 1.5rem;
    }
    
    h3 {
      color: #555;
      margin-bottom: 1rem;
    }
    
    .loading {
      text-align: center;
      padding: 3rem;
      color: #666;
    }
    
    .empty-state {
      text-align: center;
      padding: 3rem;
      background: white;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    
    .empty-state p {
      color: #666;
      font-size: 1.1rem;
    }
    
    .error-message {
      background: #fee;
      color: #c33;
      padding: 1rem;
      border-radius: 8px;
      margin-bottom: 1rem;
    }
    
    .account-info {
      background: white;
      border-radius: 8px;
      padding: 2rem;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    
    .balance-card {
      margin-bottom: 2rem;
    }
    
    .balance-item {
      display: flex;
      justify-content: space-between;
      padding: 1rem;
      border-bottom: 1px solid #eee;
    }
    
    .balance-item.highlight {
      background: #f8f9fa;
      border: 2px solid #667eea;
      border-radius: 4px;
      margin-top: 1rem;
    }
    
    .label {
      font-weight: 500;
      color: #555;
    }
    
    .amount {
      font-size: 1.25rem;
      font-weight: 600;
      color: #333;
    }
    
    .amount.reserved {
      color: #ffc107;
    }
    
    .amount.available {
      color: #28a745;
    }
    
    .funds-actions {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 2rem;
      border-top: 2px solid #eee;
      padding-top: 2rem;
    }
    
    .add-funds-section,
    .withdraw-funds-section {
      padding-top: 2rem;
    }
    
    .form-group {
      margin-bottom: 1rem;
    }
    
    label {
      display: block;
      margin-bottom: 0.5rem;
      color: #555;
      font-weight: 500;
    }
    
    input {
      width: 100%;
      padding: 0.75rem;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 1rem;
      box-sizing: border-box;
    }
    
    input:focus {
      outline: none;
      border-color: #667eea;
    }
    
    .help-text {
      display: block;
      margin-top: 0.25rem;
      font-size: 0.875rem;
      color: #666;
    }
    
    .btn-primary {
      width: 100%;
      padding: 0.75rem;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-size: 1rem;
      font-weight: 500;
      transition: all 0.3s;
    }
    
    .btn-primary:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: 0 4px 8px rgba(102, 126, 234, 0.4);
    }
    
    .btn-primary:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
    
    .btn-secondary {
      width: 100%;
      padding: 0.75rem;
      background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-size: 1rem;
      font-weight: 500;
      transition: all 0.3s;
    }
    
    .btn-secondary:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: 0 4px 8px rgba(245, 87, 108, 0.4);
    }
    
    .btn-secondary:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
    
    .success-message {
      color: #28a745;
      padding: 0.75rem;
      background: #d4edda;
      border: 1px solid #c3e6cb;
      border-radius: 4px;
      margin-top: 1rem;
      text-align: center;
    }
    
    .error-message {
      color: #dc3545;
      padding: 0.75rem;
      background: #f8d7da;
      border: 1px solid #f5c6cb;
      border-radius: 4px;
      margin-top: 1rem;
      text-align: center;
    }
    
    @media (max-width: 768px) {
      .funds-actions {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class FundAccountComponent implements OnInit {
  account: any = null;
  loading = true;
  amountToAdd = 0;
  amountToWithdraw = 0;
  isAdding = false;
  isWithdrawing = false;
  successMessage = '';
  errorMessage = '';

  constructor(
    private apiService: ApiService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      const clientId = localStorage.getItem('clientId');
      console.log('Fund Account - clientId from localStorage:', clientId);
      if (clientId && clientId !== '' && clientId !== 'null') {
        this.loadAccount(parseInt(clientId));
      } else {
        this.loading = false;
        console.warn('No valid clientId found in localStorage. Value:', clientId);
      }
    } else {
      this.loading = false;
      console.log('Server-side rendering detected, skipping account load');
    }
  }

  loadAccount(clientId: number) {
    this.loading = true;
    this.apiService.getAccount(clientId).subscribe({
      next: (data) => {
        console.log('Account loaded:', data);
        this.account = data;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading account:', error);
        this.account = null;
        this.loading = false;
        this.errorMessage = 'Failed to load account information. Please try again.';
      }
    });
  }

  addFunds() {
    if (!isPlatformBrowser(this.platformId)) return;
    
    const clientId = localStorage.getItem('clientId');
    if (!clientId) return;
    
    this.isAdding = true;
    this.successMessage = '';
    this.errorMessage = '';
    
    this.apiService.addFunds(parseInt(clientId), this.amountToAdd).subscribe({
      next: () => {
        this.successMessage = `Successfully added $${this.amountToAdd.toFixed(2)}`;
        this.amountToAdd = 0;
        this.loadAccount(parseInt(clientId));
        this.isAdding = false;
        
        setTimeout(() => {
          this.successMessage = '';
        }, 3000);
      },
      error: (error) => {
        console.error('Error adding funds:', error);
        this.errorMessage = 'Failed to add funds. Please try again.';
        this.isAdding = false;
      }
    });
  }

  withdrawFunds() {
    if (!isPlatformBrowser(this.platformId)) return;
    
    const clientId = localStorage.getItem('clientId');
    if (!clientId) return;
    
    if (this.amountToWithdraw > this.account.availableBalance) {
      this.errorMessage = 'Insufficient available balance for withdrawal';
      return;
    }
    
    this.isWithdrawing = true;
    this.successMessage = '';
    this.errorMessage = '';
    
    this.apiService.withdrawFunds(parseInt(clientId), this.amountToWithdraw).subscribe({
      next: () => {
        this.successMessage = `Successfully withdrew $${this.amountToWithdraw.toFixed(2)}`;
        this.amountToWithdraw = 0;
        this.loadAccount(parseInt(clientId));
        this.isWithdrawing = false;
        
        setTimeout(() => {
          this.successMessage = '';
        }, 3000);
      },
      error: (error) => {
        console.error('Error withdrawing funds:', error);
        this.errorMessage = error.error?.message || 'Failed to withdraw funds. Please try again.';
        this.isWithdrawing = false;
      }
    });
  }
}
