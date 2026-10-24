import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { AuthService } from './core/auth/auth.service';
import { I18nService } from './core/i18n/i18n.service';

describe('App shell', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.inject(I18nService).use('es');
  });

  it('offers login and registration to visitors', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('Entrar');
    expect(text).toContain('Crear cuenta');
    expect(text).not.toContain('Salir');
  });

  it('offers navigation and logout to logged-in users', () => {
    TestBed.inject(AuthService).account.set({ id: 'u1', email: 'ana@example.com', emailVerified: true });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('Perfil');
    expect(text).toContain('Salir');
    expect(text).not.toContain('Crear cuenta');
  });

  it('asks unverified users to verify their email and lets them resend the message', async () => {
    TestBed.inject(AuthService).account.set({ id: 'u1', email: 'ana@example.com', emailVerified: false });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('.banner')?.textContent).toContain('Aún no has verificado tu correo');
    root.querySelector<HTMLButtonElement>('.banner button')!.click();
    http.expectOne('/api/v1/me/verification-email').flush(null, { status: 204, statusText: 'No Content' });
    await new Promise<void>((resolve) => setTimeout(resolve));
    fixture.detectChanges();

    expect(root.querySelector('.banner')?.textContent).toContain('Te enviamos un correo nuevo.');
  });

  it('does not show the banner to verified users', () => {
    TestBed.inject(AuthService).account.set({ id: 'u1', email: 'ana@example.com', emailVerified: true });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.banner')).toBeNull();
  });

  it('switches the interface language', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const select = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>('#app-locale')!;

    select.value = 'en';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Log in');
  });
});
