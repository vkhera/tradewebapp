import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, NewsSentimentItem, EtfChange } from '../services/api.service';

@Component({
  selector: 'app-news',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="news-container">
      <div class="page-header">
        <h2>News &amp; Market Signals</h2>
        <div class="controls">
          <label>
            Lookback days:
            <select [(ngModel)]="lookbackDays" (change)="loadNews()">
              <option [value]="1">Yesterday (1 day)</option>
              <option [value]="3">Last 3 days</option>
              <option [value]="7">Last week</option>
            </select>
          </label>
          <label>
            Filter by symbol:
            <input type="text" [(ngModel)]="symbolFilter" placeholder="e.g. AAPL"
                   (keyup.enter)="loadNews()" class="symbol-input" />
          </label>
          <button class="btn-refresh" (click)="loadNews()" [disabled]="loadingNews">
            {{ loadingNews ? 'Loading...' : 'Refresh' }}
          </button>
        </div>
      </div>

      <!-- Error -->
      <div class="error-msg" *ngIf="newsError">{{ newsError }}</div>

      <!-- News Sentiment Table -->
      <section class="card">
        <h3>LLM News Sentiment Analysis</h3>
        <div *ngIf="loadingNews" class="loading">Loading news...</div>
        <div *ngIf="!loadingNews && newsItems.length === 0 && !newsError" class="empty">
          No news found for the selected period.
        </div>
        <div class="table-scroll" *ngIf="newsItems.length > 0">
          <table>
            <thead>
              <tr>
                <th>Symbol</th>
                <th>Title</th>
                <th>Publisher</th>
                <th>Sentiment</th>
                <th>Confidence</th>
                <th>LLM Reason</th>
                <th>Published</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let item of newsItems"
                  [class.row-selected]="selectedSymbol === item.symbol"
                  (click)="selectSymbol(item.symbol)"
                  style="cursor:pointer">
                <td><strong>{{ item.symbol }}</strong></td>
                <td class="title-cell">
                  <a [href]="item.articleUrl" target="_blank" rel="noopener">{{ item.title }}</a>
                </td>
                <td>{{ item.publisher }}</td>
                <td>
                  <span class="badge" [ngClass]="sentimentClass(item.sentiment)">
                    {{ item.sentiment }}
                  </span>
                </td>
                <td>{{ formatPct(item.sentimentConfidence) }}</td>
                <td class="reason-cell">{{ item.analysisReason }}</td>
                <td class="date-cell">{{ formatDate(item.publishedAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- ETF Activity for selected symbol -->
      <section class="card etf-section" *ngIf="selectedSymbol">
        <h3>ETF Holdings Activity for <strong>{{ selectedSymbol }}</strong>
          <button class="btn-sm" (click)="clearSelection()">Clear</button>
        </h3>
        <div *ngIf="loadingEtf" class="loading">Loading ETF data...</div>
        <div *ngIf="!loadingEtf && etfChanges.length === 0 && !etfError" class="empty">
          No recent ETF activity found for {{ selectedSymbol }}.
        </div>
        <div class="error-msg" *ngIf="etfError">{{ etfError }}</div>
        <div class="table-scroll" *ngIf="etfChanges.length > 0">
          <table>
            <thead>
              <tr>
                <th>ETF</th>
                <th>Date</th>
                <th>Action</th>
                <th>Price at Change</th>
                <th>Current Price</th>
                <th>Signal</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let c of etfChanges">
                <td><strong>{{ c.etfName }}</strong></td>
                <td>{{ c.changeDate }}</td>
                <td>
                  <span class="badge" [ngClass]="etfActionClass(c.etfName, c.action)">
                    {{ c.action }}
                  </span>
                </td>
                <td>{{ c.priceAtChange != null ? '$' + c.priceAtChange.toFixed(2) : 'N/A' }}</td>
                <td>{{ c.currentPrice != null ? '$' + c.currentPrice.toFixed(2) : 'N/A' }}</td>
                <td>
                  <span class="badge" [ngClass]="etfSignalClass(c.etfName, c.action)">
                    {{ etfSignalLabel(c.etfName, c.action) }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
  styles: [`
    .news-container { padding: 20px; max-width: 1400px; margin: 0 auto; }
    .page-header { display: flex; flex-wrap: wrap; align-items: center; gap: 16px; margin-bottom: 20px; }
    .page-header h2 { margin: 0; flex: 1; }
    .controls { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
    .controls label { display: flex; flex-direction: column; font-size: 0.75rem; color: #555; gap: 4px; }
    .controls select, .symbol-input { padding: 6px 8px; border: 1px solid #ccc; border-radius: 4px; font-size: 0.9rem; }
    .btn-refresh { padding: 8px 16px; background: #1a73e8; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
    .btn-refresh:disabled { opacity: 0.6; cursor: not-allowed; }
    .btn-sm { margin-left: 12px; padding: 2px 8px; font-size: 0.8rem; border: 1px solid #ccc; border-radius: 4px; cursor: pointer; background: #f5f5f5; }
    .card { background: #fff; border: 1px solid #e0e0e0; border-radius: 8px; padding: 16px; margin-bottom: 20px; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
    .card h3 { margin: 0 0 14px; font-size: 1rem; }
    .etf-section { border-left: 4px solid #1a73e8; }
    .table-scroll { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
    th { background: #f5f5f5; padding: 8px 10px; text-align: left; font-weight: 600; white-space: nowrap; }
    td { padding: 7px 10px; border-bottom: 1px solid #f0f0f0; vertical-align: top; }
    tr.row-selected td { background: #e8f0fe; }
    tr:hover td { background: #f9f9f9; }
    .title-cell { max-width: 280px; }
    .title-cell a { color: #1a73e8; text-decoration: none; word-break: break-word; }
    .title-cell a:hover { text-decoration: underline; }
    .reason-cell { max-width: 320px; font-size: 0.8rem; color: #555; }
    .date-cell { white-space: nowrap; }
    .badge { padding: 2px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 600; white-space: nowrap; }
    .badge-positive { background: #e6f4ea; color: #1e7e34; }
    .badge-negative { background: #fce8e6; color: #c5221f; }
    .badge-neutral  { background: #f1f3f4; color: #555; }
    .badge-bullish  { background: #e6f4ea; color: #1e7e34; }
    .badge-bearish  { background: #fce8e6; color: #c5221f; }
    .loading { color: #888; font-style: italic; padding: 10px 0; }
    .empty   { color: #888; padding: 10px 0; }
    .error-msg { background: #fce8e6; color: #c5221f; padding: 8px 12px; border-radius: 4px; margin-bottom: 12px; }
  `]
})
export class NewsComponent implements OnInit {
  lookbackDays = 1;
  symbolFilter = '';
  selectedSymbol = '';

  newsItems: NewsSentimentItem[] = [];
  etfChanges: EtfChange[] = [];

  loadingNews = false;
  loadingEtf  = false;
  newsError   = '';
  etfError    = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.loadNews();
  }

  loadNews(): void {
    this.loadingNews = true;
    this.newsError   = '';
    const sym = this.symbolFilter.trim().toUpperCase() || undefined;
    this.api.getRecentNewsSentiment(this.lookbackDays, sym).subscribe({
      next: items => { this.newsItems = items; this.loadingNews = false; },
      error: err  => { this.newsError = 'Failed to load news: ' + (err.message || err.status); this.loadingNews = false; }
    });
  }

  selectSymbol(symbol: string): void {
    if (this.selectedSymbol === symbol) return;
    this.selectedSymbol = symbol;
    this.loadEtfActivity(symbol);
  }

  clearSelection(): void {
    this.selectedSymbol = '';
    this.etfChanges = [];
    this.etfError   = '';
  }

  loadEtfActivity(symbol: string): void {
    this.loadingEtf = true;
    this.etfError   = '';
    this.etfChanges = [];
    this.api.getEtfActivityForSymbol(symbol).subscribe({
      next: changes => { this.etfChanges = changes; this.loadingEtf = false; },
      error: err    => { this.etfError = 'Failed to load ETF data: ' + (err.message || err.status); this.loadingEtf = false; }
    });
  }

  sentimentClass(s: string): string {
    if (s === 'POSITIVE') return 'badge-positive';
    if (s === 'NEGATIVE') return 'badge-negative';
    return 'badge-neutral';
  }

  /** Compute bullish/bearish signal based on ETF type + action */
  private etfSignal(etfName: string, action: string): 'bullish' | 'bearish' {
    const etf = (etfName || '').toUpperCase();
    const isAdd = action === 'Added';
    if (etf === 'HDGE') return isAdd ? 'bearish' : 'bullish';  // HDGE is short ETF
    return isAdd ? 'bullish' : 'bearish';                       // BUZZ, MMTM: added = bullish
  }

  etfActionClass(etfName: string, action: string): string {
    return action === 'Added' ? 'badge-positive' : 'badge-negative';
  }

  etfSignalClass(etfName: string, action: string): string {
    return this.etfSignal(etfName, action) === 'bullish' ? 'badge-bullish' : 'badge-bearish';
  }

  etfSignalLabel(etfName: string, action: string): string {
    const sig = this.etfSignal(etfName, action);
    const etf = (etfName || '').toUpperCase();
    const reason = etf === 'BUZZ'  ? 'social buzz' :
                   etf === 'HDGE'  ? 'short hedge' :
                   etf === 'MMTM'  ? 'momentum'    : etf;
    return sig === 'bullish' ? `Bullish (${reason})` : `Bearish (${reason})`;
  }

  formatDate(dt: string): string {
    if (!dt) return '';
    try { return new Date(dt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }); }
    catch { return dt; }
  }

  formatPct(v: number | null): string {
    if (v == null) return '';
    return (v * 100).toFixed(0) + '%';
  }
}
