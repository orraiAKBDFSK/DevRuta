import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { authGuard, guestGuard } from './auth.guard';

describe('route guards', () => {
  let auth: AuthService;

  const run = (guard: typeof authGuard) =>
    TestBed.runInInjectionContext(() => guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    auth = TestBed.inject(AuthService);
  });

  it('authGuard sends anonymous visitors to the login page', () => {
    const result = run(authGuard);

    expect(result instanceof UrlTree).toBe(true);
    expect((result as UrlTree).toString()).toBe('/entrar');
  });

  it('authGuard lets logged-in users through', () => {
    auth.account.set({ id: 'u1', email: 'ana@example.com', emailVerified: true });

    expect(run(authGuard)).toBe(true);
  });

  it('guestGuard sends logged-in users home and lets visitors see the login page', () => {
    expect(run(guestGuard)).toBe(true);

    auth.account.set({ id: 'u1', email: 'ana@example.com', emailVerified: true });
    expect((run(guestGuard) as UrlTree).toString()).toBe('/inicio');
  });
});
