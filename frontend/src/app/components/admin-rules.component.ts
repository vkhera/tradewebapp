import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, Rule } from '../services/api.service';

@Component({
  selector: 'app-admin-rules',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="admin-container">
      <h2>Rule Management</h2>
      
      <button (click)="showForm = !showForm" class="btn-primary">
        {{ showForm ? 'Cancel' : 'Add New Rule' }}
      </button>
      
      <div *ngIf="showForm" class="rule-form">
        <h3>{{ editingRule ? 'Edit Rule' : 'New Rule' }}</h3>
        
        <div class="form-group">
          <label>Rule Name:</label>
          <input type="text" [(ngModel)]="rule.ruleName" placeholder="Unique rule name">
        </div>
        
        <div class="form-group">
          <label>Description:</label>
          <textarea [(ngModel)]="rule.description" placeholder="Rule description" rows="3"></textarea>
        </div>
        
        <div class="form-group">
          <label>Rule Type:</label>
          <select [(ngModel)]="rule.ruleType">
            <option value="FRAUD_CHECK">FRAUD_CHECK</option>
            <option value="RISK_LIMIT">RISK_LIMIT</option>
            <option value="TRADING_HOURS">TRADING_HOURS</option>
            <option value="POSITION_LIMIT">POSITION_LIMIT</option>
            <option value="PRICE_VALIDATION">PRICE_VALIDATION</option>
          </select>
        </div>
        
        <div class="form-group">
          <label>Level:</label>
          <select [(ngModel)]="rule.level">
            <option value="APPLICATION">APPLICATION</option>
            <option value="CLIENT">CLIENT</option>
            <option value="TRADE">TRADE</option>
          </select>
        </div>
        
        <div class="form-group" *ngIf="rule.level === 'CLIENT'">
          <label>Client ID:</label>
          <input type="number" [(ngModel)]="rule.clientId" placeholder="Client ID for client-specific rules">
        </div>
        
        <div class="form-group">
          <label>Rule Content (DRL):</label>
          <textarea [(ngModel)]="rule.ruleContent" placeholder="Drools rule definition" rows="6"></textarea>
        </div>
        
        <div class="form-group">
          <label>Priority:</label>
          <input type="number" [(ngModel)]="rule.priority" placeholder="1-100">
        </div>
        
        <div class="form-group">
          <label>
            <input type="checkbox" [(ngModel)]="rule.active">
            Active
          </label>
        </div>
        
        <button (click)="saveRule()">{{ editingRule ? 'Update' : 'Create' }} Rule</button>
        <button (click)="resetForm()">Cancel</button>
      </div>
      
      <div class="rules-table">
        <h3>All Rules</h3>
        <table *ngIf="rules.length > 0">
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Type</th>
              <th>Level</th>
              <th>Priority</th>
              <th>Active</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let r of rules">
              <td>{{ r.id }}</td>
              <td>{{ r.ruleName }}</td>
              <td>{{ r.ruleType }}</td>
              <td>{{ r.level }}</td>
              <td>{{ r.priority }}</td>
              <td>{{ r.active ? 'Yes' : 'No' }}</td>
              <td>
                <button (click)="editRule(r)" class="btn-edit">Edit</button>
                <button (click)="deleteRule(r.id!)" class="btn-delete">Delete</button>
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
    
    .rule-form {
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
    
    .form-group input, .form-group select, .form-group textarea {
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
export class AdminRulesComponent implements OnInit {
  rules: Rule[] = [];
  rule: Rule = this.getEmptyRule();
  showForm = false;
  editingRule: Rule | null = null;

  constructor(private apiService: ApiService) {}

  ngOnInit() {
    this.loadRules();
  }

  loadRules() {
    this.apiService.getAllRules().subscribe({
      next: (rules) => this.rules = rules,
      error: (error) => console.error('Failed to load rules', error)
    });
  }

  saveRule() {
    if (this.editingRule && this.editingRule.id) {
      this.apiService.updateRule(this.editingRule.id, this.rule).subscribe({
        next: () => {
          this.loadRules();
          this.resetForm();
        },
        error: (error) => console.error('Failed to update rule', error)
      });
    } else {
      this.apiService.createRule(this.rule).subscribe({
        next: () => {
          this.loadRules();
          this.resetForm();
        },
        error: (error) => console.error('Failed to create rule', error)
      });
    }
  }

  editRule(rule: Rule) {
    this.editingRule = rule;
    this.rule = { ...rule };
    this.showForm = true;
  }

  deleteRule(id: number) {
    if (confirm('Are you sure you want to delete this rule?')) {
      this.apiService.deleteRule(id).subscribe({
        next: () => this.loadRules(),
        error: (error) => console.error('Failed to delete rule', error)
      });
    }
  }

  resetForm() {
    this.rule = this.getEmptyRule();
    this.showForm = false;
    this.editingRule = null;
  }

  private getEmptyRule(): Rule {
    return {
      ruleName: '',
      description: '',
      ruleType: 'FRAUD_CHECK',
      level: 'APPLICATION',
      ruleContent: '',
      active: true,
      priority: 1
    };
  }
}
