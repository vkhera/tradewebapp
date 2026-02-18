import { Component, Inject, PLATFORM_ID } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { CommonModule, isPlatformBrowser } from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CommonModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'Stock Brokerage Platform';
  menuOpen = false;

  constructor(
    private router: Router,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  private isBrowser(): boolean {
    return isPlatformBrowser(this.platformId);
  }

  isLoggedIn(): boolean {
    if (!this.isBrowser()) return false;
    return localStorage.getItem('currentUser') !== null;
  }

  isClient(): boolean {
    if (!this.isBrowser()) return false;
    return localStorage.getItem('role') === 'CLIENT';
  }

  isAdmin(): boolean {
    if (!this.isBrowser()) return false;
    return localStorage.getItem('role') === 'ADMIN';
  }

  toggleMenu(): void {
    this.menuOpen = !this.menuOpen;
  }

  closeMenu(): void {
    this.menuOpen = false;
  }

  logout(): void {
    if (!this.isBrowser()) return;
    localStorage.removeItem('currentUser');
    localStorage.removeItem('role');
    localStorage.removeItem('clientId');
    this.router.navigate(['/login']);
    this.closeMenu();
  }
}
