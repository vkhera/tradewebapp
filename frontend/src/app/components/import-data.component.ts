import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../services/api.service';
import { Router } from '@angular/router';

interface ImportResponse {
  success: boolean;
  message: string;
  recordsProcessed: number;
  recordsImported: number;
  recordsSkipped: number;
  errors: string[];
}

@Component({
  selector: 'app-import-data',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <h2>Import Portfolio Data</h2>
      
      <div class="import-section">
        <h3>Import Holdings</h3>
        <p>Import current portfolio holdings from CSV file</p>
        
        <div class="form-group">
          <label>Client ID:</label>
          <input 
            type="number" 
            [(ngModel)]="holdingsClientId" 
            placeholder="Enter Client ID"
            class="form-control">
        </div>
        
        <div class="form-group">
          <label>File Name:</label>
          <input 
            type="text" 
            [(ngModel)]="holdingsFileName" 
            value="ExportData17022026162214.csv"
            placeholder="Enter CSV file name"
            class="form-control">
        </div>
        
        <button 
          (click)="importHoldings()" 
          [disabled]="importingHoldings"
          class="btn btn-primary">
          {{ importingHoldings ? 'Importing...' : 'Import Holdings' }}
        </button>
        
        <div *ngIf="holdingsResult" class="result-box" [class.success]="holdingsResult.success" [class.error]="!holdingsResult.success">
          <h4>{{ holdingsResult.message }}</h4>
          <p>Processed: {{ holdingsResult.recordsProcessed }}</p>
          <p>Imported: {{ holdingsResult.recordsImported }}</p>
          <p>Skipped: {{ holdingsResult.recordsSkipped }}</p>
          <div *ngIf="holdingsResult.errors && holdingsResult.errors.length > 0">
            <h5>Errors:</h5>
            <ul>
              <li *ngFor="let error of holdingsResult.errors">{{ error }}</li>
            </ul>
          </div>
        </div>
      </div>
      
      <hr>
      
      <div class="import-section cleanup-section">
        <h3>Cleanup Client Data</h3>
        <p>Remove all portfolio holdings and trade activity for a client before re-importing</p>
        
        <div class="form-group">
          <label>Client ID:</label>
          <input 
            type="number" 
            [(ngModel)]="cleanupClientId" 
            placeholder="Enter Client ID"
            class="form-control">
        </div>
        
        <button 
          (click)="cleanupClient()" 
          [disabled]="cleaningUp"
          class="btn btn-danger">
          {{ cleaningUp ? 'Cleaning...' : 'Cleanup Client Data' }}
        </button>
        
        <div *ngIf="cleanupResult" class="result-box" [class.success]="cleanupResult.success" [class.error]="!cleanupResult.success">
          <h4>{{ cleanupResult.message }}</h4>
          <p *ngIf=\"cleanupResult.recordsProcessed !== undefined\">Total records deleted: {{ cleanupResult.recordsProcessed }}</p>
          <div *ngIf=\"cleanupResult.errors && cleanupResult.errors.length > 0\">
            <h5>Errors:</h5>
            <ul>
              <li *ngFor=\"let error of cleanupResult.errors\">{{ error }}</li>
            </ul>
          </div>
        </div>
      </div>
      
      <hr>
      
      <div class="import-section">
        <h3>Import Activity</h3>
        <p>Import trade activity and reconcile with portfolio</p>
        
        <div class="form-group">
          <label>Client ID:</label>
          <input 
            type="number" 
            [(ngModel)]="activityClientId" 
            placeholder="Enter Client ID"
            class="form-control">
        </div>
        
        <div class="form-group">
          <label>File Name:</label>
          <input 
            type="text" 
            [(ngModel)]="activityFileName" 
            value="ExportData17022026162518-IRA94178-Activity.csv"
            placeholder="Enter CSV file name"
            class="form-control">
        </div>
        
        <button 
          (click)="importActivity()" 
          [disabled]="importingActivity"
          class="btn btn-primary">
          {{ importingActivity ? 'Importing...' : 'Import Activity' }}
        </button>
        
        <div *ngIf="activityResult" class="result-box" [class.success]="activityResult.success" [class.error]="!activityResult.success">
          <h4>{{ activityResult.message }}</h4>
          <p>Processed: {{ activityResult.recordsProcessed }}</p>
          <p>Imported: {{ activityResult.recordsImported }}</p>
          <p>Skipped: {{ activityResult.recordsSkipped }}</p>
          <div *ngIf="activityResult.errors && activityResult.errors.length > 0">
            <h5>Errors:</h5>
            <ul>
              <li *ngFor="let error of activityResult.errors">{{ error }}</li>
            </ul>
          </div>
        </div>
      </div>
      
      <div class="actions">
        <button (click)="viewPortfolio()" class="btn btn-secondary">View Portfolio</button>
        <button (click)="viewHistory()" class="btn btn-secondary">View Order History</button>
      </div>
    </div>
  `,
  styles: [`
    .container {
      max-width: 800px;
      margin: 20px auto;
      padding: 20px;
    }
    
    h2 {
      color: #333;
      margin-bottom: 30px;
    }
    
    .import-section {
      background: #f9f9f9;
      padding: 20px;
      border-radius: 8px;
      margin-bottom: 20px;
    }
    
    h3 {
      color: #2c3e50;
      margin-bottom: 10px;
    }
    
    .form-group {
      margin-bottom: 15px;
    }
    
    label {
      display: block;
      margin-bottom: 5px;
      font-weight: bold;
      color: #555;
    }
    
    .form-control {
      width: 100%;
      padding: 8px 12px;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 14px;
    }
    
    .btn {
      padding: 10px 20px;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-size: 14px;
      margin-right: 10px;
    }
    
    .btn-primary {
      background-color: #007bff;
      color: white;
    }
    
    .btn-primary:hover:not(:disabled) {
      background-color: #0056b3;
    }
    
    .btn-primary:disabled {
      background-color: #cccccc;
      cursor: not-allowed;
    }
    
    .btn-secondary {
      background-color: #6c757d;
      color: white;
    }
    
    .btn-secondary:hover {
      background-color: #545b62;
    }
    
    .btn-danger {
      background-color: #dc3545;
      color: white;
    }
    
    .btn-danger:hover:not(:disabled) {
      background-color: #c82333;
    }
    
    .btn-danger:disabled {
      background-color: #cccccc;
      cursor: not-allowed;
    }
    
    .cleanup-section {
      background: #fff3cd;
      border: 1px solid #ffc107;
    }
    
    .result-box {
      margin-top: 20px;
      padding: 15px;
      border-radius: 4px;
      border: 1px solid;
    }
    
    .result-box.success {
      background-color: #d4edda;
      border-color: #c3e6cb;
      color: #155724;
    }
    
    .result-box.error {
      background-color: #f8d7da;
      border-color: #f5c6cb;
      color: #721c24;
    }
    
    .result-box h4 {
      margin-top: 0;
      margin-bottom: 10px;
    }
    
    .result-box p {
      margin: 5px 0;
    }
    
    .result-box ul {
      margin: 10px 0;
      padding-left: 20px;
    }
    
    .actions {
      margin-top: 30px;
      padding-top: 20px;
      border-top: 1px solid #ddd;
    }
    
    hr {
      margin: 30px 0;
      border: none;
      border-top: 2px solid #ddd;
    }
  `]
})
export class ImportDataComponent {
  cleanupClientId: number = 1;
  cleaningUp: boolean = false;
  cleanupResult: any = null;
  
  holdingsClientId: number = 1;
  holdingsFileName: string = 'ExportData17022026162214.csv';
  importingHoldings: boolean = false;
  holdingsResult: ImportResponse | null = null;
  
  activityClientId: number = 1;
  activityFileName: string = 'ExportData17022026162518-IRA94178-Activity.csv';
  importingActivity: boolean = false;
  activityResult: ImportResponse | null = null;
  
  constructor(
    private apiService: ApiService,
    private router: Router
  ) {}
  
  cleanupClient() {
    if (!this.cleanupClientId) {
      alert('Please provide Client ID');
      return;
    }
    
    // Check if user is logged in
    const currentUser = localStorage.getItem('currentUser');
    if (!currentUser) {
      alert('You must be logged in to perform this action. Please log in first.');
      return;
    }
    
    if (!confirm(`Are you sure you want to delete all portfolio and trade data for client ${this.cleanupClientId}? This cannot be undone.`)) {
      return;
    }
    
    console.log('Starting cleanup for client:', this.cleanupClientId);
    console.log('User credentials:', currentUser);
    this.cleaningUp = true;
    this.cleanupResult = null;
    
    this.apiService.cleanupClientData(this.cleanupClientId)
      .subscribe({
        next: (response) => {
          console.log('Cleanup response:', response);
          this.cleanupResult = response;
          this.cleaningUp = false;
        },
        error: (error) => {
          console.error('Error cleaning up client data:', error);
          console.error('Error status:', error.status);
          console.error('Error message:', error.message);
          console.error('Error details:', error.error);
          this.cleanupResult = {
            success: false,
            message: 'Error cleaning up data: ' + (error.error?.message || error.message || 'Unknown error')
          };
          this.cleaningUp = false;
        }
      });
  }
  
  importHoldings() {
    if (!this.holdingsClientId || !this.holdingsFileName) {
      alert('Please provide both Client ID and File Name');
      return;
    }
    
    this.importingHoldings = true;
    this.holdingsResult = null;
    
    this.apiService.importHoldings(this.holdingsClientId, this.holdingsFileName)
      .subscribe({
        next: (response) => {
          this.holdingsResult = response;
          this.importingHoldings = false;
        },
        error: (error) => {
          console.error('Error importing holdings:', error);
          this.holdingsResult = {
            success: false,
            message: 'Error importing holdings: ' + (error.error?.message || error.message),
            recordsProcessed: 0,
            recordsImported: 0,
            recordsSkipped: 0,
            errors: [error.error?.message || error.message]
          };
          this.importingHoldings = false;
        }
      });
  }
  
  importActivity() {
    if (!this.activityClientId || !this.activityFileName) {
      alert('Please provide both Client ID and File Name');
      return;
    }
    
    this.importingActivity = true;
    this.activityResult = null;
    
    this.apiService.importActivity(this.activityClientId, this.activityFileName)
      .subscribe({
        next: (response) => {
          this.activityResult = response;
          this.importingActivity = false;
        },
        error: (error) => {
          console.error('Error importing activity:', error);
          this.activityResult = {
            success: false,
            message: 'Error importing activity: ' + (error.error?.message || error.message),
            recordsProcessed: 0,
            recordsImported: 0,
            recordsSkipped: 0,
            errors: [error.error?.message || error.message]
          };
          this.importingActivity = false;
        }
      });
  }
  
  viewPortfolio() {
    this.router.navigate(['/portfolio']);
  }
  
  viewHistory() {
    this.router.navigate(['/order-history']);
  }
}
