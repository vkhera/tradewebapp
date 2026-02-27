import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../services/api.service';

@Component({
  selector: 'app-admin-client-holdings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="admin-container">
      <h2>Client Holdings</h2>

      <div class="filter-bar" style="display:flex; gap:12px; margin-bottom:16px; flex-wrap:wrap; align-items:center;">
        <div class="form-group" style="margin:0;">
          <label>Filter by Client ID:</label>
          <input type="number" [(ngModel)]="filterClientId" placeholder="All clients"
                 style="width:140px; margin-left:8px;" (ngModelChange)="onFilterChange()">
        </div>
        <div class="form-group" style="margin:0;">
          <label>Filter by Symbol:</label>
          <input type="text" [(ngModel)]="filterSymbol" placeholder="e.g. AAPL"
                 style="width:120px; margin-left:8px; text-transform:uppercase;"
                 (ngModelChange)="onFilterChange()">
        </div>
        <button class="btn-secondary" (click)="clearFilters()">Clear Filters</button>
        <span class="summary-text" style="margin-left:auto; color:#888; font-size:0.9em;">
          {{ filteredHoldings.length }} row(s) &middot;
          {{ uniqueClientCount }} client(s) &middot;
          {{ uniqueSymbolCount }} symbol(s)
        </span>
      </div>

      <div *ngIf="loading" style="padding:24px; text-align:center; color:#888;">Loading...</div>
      <div *ngIf="error" style="padding:12px; color:red;">{{ error }}</div>

      <div *ngIf="!loading && !error" class="clients-table">
        <table *ngIf="filteredHoldings.length > 0; else noData">
          <thead>
            <tr>
              <th>Client ID</th>
              <th>Client Name</th>
              <th>Symbol</th>
              <th>Quantity</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let h of filteredHoldings">
              <td>{{ h.clientId }}</td>
              <td>{{ h.clientName }}</td>
              <td>{{ h.symbol }}</td>
              <td style="text-align:right;">{{ h.quantity }}</td>
            </tr>
          </tbody>
        </table>
        <ng-template #noData>
          <p style="color:#888; padding:16px;">No holdings found matching the current filters.</p>
        </ng-template>
      </div>
    </div>
  `
})
export class AdminClientHoldingsComponent implements OnInit {
  holdings: any[] = [];
  filterClientId: number | null = null;
  filterSymbol: string = '';
  loading = false;
  error: string | null = null;

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    this.apiService.getAllClientHoldings().subscribe({
      next: (data) => {
        this.holdings = data;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load client holdings: ' + (err.message || err.status);
        this.loading = false;
      }
    });
  }

  get filteredHoldings(): any[] {
    return this.holdings.filter(h => {
      const clientMatch = !this.filterClientId || h.clientId === Number(this.filterClientId);
      const symbolMatch = !this.filterSymbol || h.symbol.toUpperCase().includes(this.filterSymbol.toUpperCase());
      return clientMatch && symbolMatch;
    });
  }

  get uniqueClientCount(): number {
    return new Set(this.filteredHoldings.map(h => h.clientId)).size;
  }

  get uniqueSymbolCount(): number {
    return new Set(this.filteredHoldings.map(h => h.symbol)).size;
  }

  onFilterChange(): void {
    // computed getter handles filtering reactively
  }

  clearFilters(): void {
    this.filterClientId = null;
    this.filterSymbol = '';
  }
}
