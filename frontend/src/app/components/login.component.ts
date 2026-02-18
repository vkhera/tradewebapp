import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../services/api.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="login-container">
      <div class="login-card">
        <h2>Stock Brokerage Login</h2>
        <form (ngSubmit)="login()" #loginForm="ngForm">
          <div class="form-group">
            <label for="username">Username</label>
            <input 
              type="text" 
              id="username" 
              [(ngModel)]="username" 
              name="username" 
              required
              placeholder="Enter username">
          </div>
          
          <div class="form-group">
            <label for="password">Password</label>
            <input 
              type="password" 
              id="password" 
              [(ngModel)]="password" 
              name="password" 
              required
              placeholder="Enter password">
          </div>
          
          <div *ngIf="errorMessage" class="error-message">
            {{ errorMessage }}
          </div>
          
          <button type="submit" [disabled]="!loginForm.valid || isLoading">
            {{ isLoading ? 'Logging in...' : 'Login' }}
          </button>
        </form>
        
        <div class="demo-credentials">
          <p><strong>Demo Credentials:</strong></p>
          <p>Client: client1 / pass1234</p>
          <p>Admin: admin1 / pass1234</p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .login-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    }
    
    .login-card {
      background: white;
      padding: 2rem;
      border-radius: 8px;
      box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
      width: 100%;
      max-width: 400px;
    }
    
    h2 {
      text-align: center;
      color: #333;
      margin-bottom: 1.5rem;
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
    
    button {
      width: 100%;
      padding: 0.75rem;
      background: #667eea;
      color: white;
      border: none;
      border-radius: 4px;
      font-size: 1rem;
      cursor: pointer;
      margin-top: 1rem;
    }
    
    button:hover:not(:disabled) {
      background: #5568d3;
    }
    
    button:disabled {
      background: #ccc;
      cursor: not-allowed;
    }
    
    .error-message {
      color: #dc3545;
      padding: 0.5rem;
      background: #f8d7da;
      border-radius: 4px;
      margin: 1rem 0;
      text-align: center;
    }
    
    .demo-credentials {
      margin-top: 1.5rem;
      padding: 1rem;
      background: #f8f9fa;
      border-radius: 4px;
      font-size: 0.875rem;
    }
    
    .demo-credentials p {
      margin: 0.25rem 0;
      color: #666;
    }
  `]
})
export class LoginComponent {
  username = '';
  password = '';
  errorMessage = '';
  isLoading = false;

  constructor(
    private apiService: ApiService,
    private router: Router
  ) {}

  login() {
    this.isLoading = true;
    this.errorMessage = '';
    
    this.apiService.login(this.username, this.password).subscribe({
      next: (response) => {
        // Store user data with credentials for Basic Auth
        const userData = {
          ...response,
          username: this.username,
          password: this.password
        };
        localStorage.setItem('currentUser', JSON.stringify(userData));
        localStorage.setItem('role', response.role);
        localStorage.setItem('clientId', response.clientId?.toString() || '');
        
        if (response.role === 'CLIENT') {
          this.router.navigate(['/portfolio']);
        } else {
          this.router.navigate(['/admin/clients']);
        }
        this.isLoading = false;
      },
      error: (error) => {
        this.errorMessage = 'Invalid username or password';
        this.isLoading = false;
      }
    });
  }
}
