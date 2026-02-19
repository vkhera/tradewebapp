import { inject, PLATFORM_ID } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { isPlatformBrowser } from '@angular/common';

export const authGuard: CanActivateFn = () => {
  const router  = inject(Router);
  const platformId = inject(PLATFORM_ID);

  if (!isPlatformBrowser(platformId)) return true; // SSR: let it through

  if (localStorage.getItem('currentUser')) return true;

  router.navigate(['/login']);
  return false;
};

export const adminGuard: CanActivateFn = () => {
  const router  = inject(Router);
  const platformId = inject(PLATFORM_ID);

  if (!isPlatformBrowser(platformId)) return true;

  const role = localStorage.getItem('role');
  if (role === 'ADMIN') return true;

  if (!localStorage.getItem('currentUser')) {
    router.navigate(['/login']);
  } else {
    router.navigate(['/portfolio']);
  }
  return false;
};
