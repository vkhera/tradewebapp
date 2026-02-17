import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, Client } from '../services/api.service';

@Component({
  selector: 'app-admin-clients',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="admin-container">
      <h2>Client Management</h2>
      
      <button (click)="showForm = !showForm" class="btn-primary">
        {{ showForm ? 'Cancel' : 'Add New Client' }}
      </button>
      
      <div *ngIf="showForm" class="client-form">
        <h3>{{ editingClient ? 'Edit Client' : 'New Client' }}</h3>
        
        <div class="form-group">
          <label>Client Code:</label>
          <input type="text" [(ngModel)]="client.clientCode" placeholder="Unique code">
        </div>
        
        <div class="form-group">
          <label>Name:</label>
          <input type="text" [(ngModel)]="client.name" placeholder="Full name">
        </div>
        
        <div class="form-group">
          <label>Email:</label>
          <input type="email" [(ngModel)]="client.email" placeholder="email@example.com">
        </div>
        
        <div class="form-group">
          <label>Phone:</label>
          <input type="tel" [(ngModel)]="client.phone" placeholder="Phone number">
        </div>
        
        <div class="form-group">
          <label>Account Balance:</label>
          <input type="number" step="0.01" [(ngModel)]="client.accountBalance" placeholder="0.00">
        </div>
        
        <div class="form-group">
          <label>Status:</label>
          <select [(ngModel)]="client.status">
            <option value="ACTIVE">ACTIVE</option>
            <option value="INACTIVE">INACTIVE</option>
            <option value="SUSPENDED">SUSPENDED</option>
            <option value="BLOCKED">BLOCKED</option>
          </select>
        </div>
        
        <div class="form-group">
          <label>Risk Level:</label>
          <select [(ngModel)]="client.riskLevel">
            <option value="LOW">LOW</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="HIGH">HIGH</option>
          </select>
        </div>
        
        <div class="form-group">
          <label>Daily Trade Limit:</label>
          <input type="number" step="0.01" [(ngModel)]="client.dailyTradeLimit" placeholder="Optional">
        </div>
        
        <button (click)="saveClient()">{{ editingClient ? 'Update' : 'Create' }} Client</button>
        <button (click)="resetForm()">Cancel</button>
      </div>
      
      <div class="clients-table">
        <h3>All Clients</h3>
        <table *ngIf="clients.length > 0">
          <thead>
            <tr>
              <th>ID</th>
              <th>Code</th>
              <th>Name</th>
              <th>Email</th>
              <th>Balance</th>
              <th>Status</th>
              <th>Risk Level</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let c of clients">
              <td>{{ c.id }}</td>
              <td>{{ c.clientCode }}</td>
              <td>{{ c.name }}</td>
              <td>{{ c.email }}</td>
              <td>{{ c.accountBalance | number:'1.2-2' }}</td>
              <td>{{ c.status }}</td>
              <td>{{ c.riskLevel }}</td>
              <td>
                <button (click)="editClient(c)" class="btn-edit">Edit</button>
                <button (click)="deleteClient(c.id!)" class="btn-delete">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: [`
    .admin-container {
      padding: 20px;
      max-width: 1400px;
      margin: 0 auto;
    }
    
    .client-form {
      background: #f5f5f5;
      padding: 20px;
      border-radius: 8px;
      margin: 20px 0;
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
    
    button {
      padding: 10px 15px;
      margin-right: 10px;
      border: none;
      border-radius: 4px;
      cursor: pointer;
    }
    
    .btn-primary {
      background: #007bff;
      color: white;
    }
    
    .btn-edit {
      background: #28a745;
      color: white;
    }
    
    .btn-delete {
      background: #dc3545;
      color: white;
    }
    
    table {
      width: 100%;
      border-collapse: collapse;
      background: white;
      margin-top: 20px;
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
  `]
})
export class AdminClientsComponent implements OnInit {
  clients: Client[] = [];
  client: Client = this.getEmptyClient();
  showForm = false;
  editingClient: Client | null = null;

  constructor(private apiService: ApiService) {}

  ngOnInit() {
    this.loadClients();
  }

  loadClients() {
    this.apiService.getAllClients().subscribe({
      next: (clients) => this.clients = clients,
      error: (error) => console.error('Failed to load clients', error)
    });
  }

  saveClient() {
    if (this.editingClient && this.editingClient.id) {
      this.apiService.updateClient(this.editingClient.id, this.client).subscribe({
        next: () => {
          this.loadClients();
          this.resetForm();
        },
        error: (error) => console.error('Failed to update client', error)
      });
    } else {
      this.apiService.createClient(this.client).subscribe({
        next: () => {
          this.loadClients();
          this.resetForm();
        },
        error: (error) => console.error('Failed to create client', error)
      });
    }
  }

  editClient(client: Client) {
    this.editingClient = client;
    this.client = { ...client };
    this.showForm = true;
  }

  deleteClient(id: number) {
    if (confirm('Are you sure you want to delete this client?')) {
      this.apiService.deleteClient(id).subscribe({
        next: () => this.loadClients(),
        error: (error) => console.error('Failed to delete client', error)
      });
    }
  }

  resetForm() {
    this.client = this.getEmptyClient();
    this.showForm = false;
    this.editingClient = null;
  }

  private getEmptyClient(): Client {
    return {
      clientCode: '',
      name: '',
      email: '',
      phone: '',
      accountBalance: 0,
      status: 'ACTIVE',
      riskLevel: 'MEDIUM'
    };
  }
}
