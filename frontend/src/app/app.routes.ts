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
import { authGuard, adminGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'trade',            component: TradeComponent,           canActivate: [authGuard] },
  { path: 'portfolio',        component: PortfolioComponent,       canActivate: [authGuard] },
  { path: 'order-history',    component: OrderHistoryComponent,    canActivate: [authGuard] },
  { path: 'realized-gains',   component: RealizedGainsComponent,   canActivate: [authGuard] },
  { path: 'unrealized-gains', component: UnrealizedGainsComponent, canActivate: [authGuard] },
  { path: 'fund-account',     component: FundAccountComponent,     canActivate: [authGuard] },
  { path: 'import-data',      component: ImportDataComponent,      canActivate: [authGuard] },
  { path: 'admin/clients',    component: AdminClientsComponent,    canActivate: [adminGuard] },
  { path: 'admin/rules',      component: AdminRulesComponent,      canActivate: [adminGuard] },
  { path: '**', redirectTo: '/login' }
];
