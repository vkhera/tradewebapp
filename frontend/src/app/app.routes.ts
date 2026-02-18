import { Routes } from '@angular/router';
import { TradeComponent } from './components/trade.component';
import { AdminClientsComponent } from './components/admin-clients.component';
import { AdminRulesComponent } from './components/admin-rules.component';
import { LoginComponent } from './components/login.component';
import { PortfolioComponent } from './components/portfolio.component';
import { FundAccountComponent } from './components/fund-account.component';
import { OrderHistoryComponent } from './components/order-history.component';
import { RealizedGainsComponent } from './components/realized-gains.component';
import { UnrealizedGainsComponent } from './components/unrealized-gains.component';
import { ImportDataComponent } from './components/import-data.component';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'trade', component: TradeComponent },
  { path: 'portfolio', component: PortfolioComponent },
  { path: 'order-history', component: OrderHistoryComponent },
  { path: 'realized-gains', component: RealizedGainsComponent },
  { path: 'unrealized-gains', component: UnrealizedGainsComponent },
  { path: 'fund-account', component: FundAccountComponent },
  { path: 'import-data', component: ImportDataComponent },
  { path: 'admin/clients', component: AdminClientsComponent },
  { path: 'admin/rules', component: AdminRulesComponent }
];
