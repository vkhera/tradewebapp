import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Trade {
  id?: number;
  clientId: number;
  symbol: string;
  quantity: number;
  price: number;
  type: 'BUY' | 'SELL';
  orderType?: 'MARKET' | 'LIMIT';
  status?: string;
  tradeTime?: string;
  fraudCheckPassed?: boolean;
  fraudCheckReason?: string;
  expiryTime?: string;
}

export interface Client {
  id?: number;
  clientCode: string;
  name: string;
  email: string;
  phone: string;
  accountBalance: number;
  status: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'BLOCKED';
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  dailyTradeLimit?: number;
}

export interface Rule {
  id?: number;
  ruleName: string;
  description: string;
  ruleType: string;
  level: 'APPLICATION' | 'CLIENT' | 'TRADE';
  clientId?: number;
  ruleContent: string;
  active: boolean;
  priority: number;
}

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private baseUrl = 'http://localhost:8080/api';
  private adminUrl = 'http://localhost:8080/api/admin';
  
  private getHttpOptions() {
    const headers: any = {
      'Content-Type': 'application/json'
    };
    
    // Add Basic Auth header if user is logged in
    if (typeof window !== 'undefined' && window.localStorage) {
      const currentUser = localStorage.getItem('currentUser');
      if (currentUser) {
        try {
          const user = JSON.parse(currentUser);
          if (user.username && user.password) {
            const basicAuth = btoa(`${user.username}:${user.password}`);
            headers['Authorization'] = `Basic ${basicAuth}`;
          }
        } catch (e) {
          console.error('Error parsing user data', e);
        }
      }
    }
    
    return {
      headers: new HttpHeaders(headers),
      withCredentials: true
    };
  }

  constructor(private http: HttpClient) { }

  // Trade APIs
  executeTrade(trade: Trade): Observable<Trade> {
    return this.http.post<Trade>(`${this.baseUrl}/trades`, trade, this.getHttpOptions());
  }

  getAllTrades(): Observable<Trade[]> {
    return this.http.get<Trade[]>(`${this.adminUrl}/trades`, this.getHttpOptions());
  }

  getTradeById(id: number): Observable<Trade> {
    return this.http.get<Trade>(`${this.baseUrl}/trades/${id}`, this.getHttpOptions());
  }

  getTradesByClient(clientId: number): Observable<Trade[]> {
    return this.http.get<Trade[]>(`${this.baseUrl}/trades/client/${clientId}`, this.getHttpOptions());
  }

  cancelTrade(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/trades/${id}`, this.getHttpOptions());
  }

  // Client APIs
  createClient(client: Client): Observable<Client> {
    return this.http.post<Client>(`${this.adminUrl}/clients`, client, this.getHttpOptions());
  }

  getAllClients(): Observable<Client[]> {
    return this.http.get<Client[]>(`${this.adminUrl}/clients`, this.getHttpOptions());
  }

  getClientById(id: number): Observable<Client> {
    return this.http.get<Client>(`${this.adminUrl}/clients/${id}`, this.getHttpOptions());
  }

  updateClient(id: number, client: Client): Observable<Client> {
    return this.http.put<Client>(`${this.adminUrl}/clients/${id}`, client, this.getHttpOptions());
  }

  deleteClient(id: number): Observable<void> {
    return this.http.delete<void>(`${this.adminUrl}/clients/${id}`, this.getHttpOptions());
  }

  // Rule APIs
  createRule(rule: Rule): Observable<Rule> {
    return this.http.post<Rule>(`${this.adminUrl}/rules`, rule, this.getHttpOptions());
  }

  getAllRules(): Observable<Rule[]> {
    return this.http.get<Rule[]>(`${this.adminUrl}/rules`, this.getHttpOptions());
  }

  getRuleById(id: number): Observable<Rule> {
    return this.http.get<Rule>(`${this.adminUrl}/rules/${id}`, this.getHttpOptions());
  }

  updateRule(id: number, rule: Rule): Observable<Rule> {
    return this.http.put<Rule>(`${this.adminUrl}/rules/${id}`, rule, this.getHttpOptions());
  }

  deleteRule(id: number): Observable<void> {
    return this.http.delete<void>(`${this.adminUrl}/rules/${id}`, this.getHttpOptions());
  }

  // Authentication APIs
  login(username: string, password: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/auth/login`, { username, password }, {
      headers: new HttpHeaders({ 'Content-Type': 'application/json' })
    });
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/auth/logout`, {}, this.getHttpOptions());
  }

  // Portfolio APIs
  getPortfolio(clientId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/portfolio/client/${clientId}`, this.getHttpOptions());
  }

  getPortfolioSummary(clientId: number): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/portfolio/client/${clientId}/summary`, this.getHttpOptions());
  }

  // Account APIs
  getAccount(clientId: number): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/account/client/${clientId}`, this.getHttpOptions());
  }

  addFunds(clientId: number, amount: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/account/client/${clientId}/add-funds`, { amount }, this.getHttpOptions());
  }

  withdrawFunds(clientId: number, amount: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/account/client/${clientId}/withdraw-funds`, { amount }, this.getHttpOptions());
  }

  // Stock Price APIs
  getStockPrice(symbol: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/stocks/price/${symbol}`, this.getHttpOptions());
  }
}
