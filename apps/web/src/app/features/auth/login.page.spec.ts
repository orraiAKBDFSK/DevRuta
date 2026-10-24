import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import { LoginPage } from './login.page';

const settle = () => new Promise<void>((resolve) => setTimeout(resolve));

describe('LoginPage', () => {
  let http: HttpTestingController;

  const setup = () => {
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const type = (selector: string, value: string) => {
      const input = root.querySelector<HTMLInputElement>(selector)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const submit = () => root.querySelector('form')!.dispatchEvent(new Event('submit'));
    return { fixture, root, type, submit };
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.inject(I18nService).use('es');
    http = TestBed.inject(HttpTestingController);
  });

  it('does not call the API while the form is invalid and shows validation messages', async () => {
    const { fixture, root, submit } = setup();

    submit();
    await fixture.whenStable();
    fixture.detectChanges();

    http.expectNone('/api/v1/auth/login');
    expect(root.textContent).toContain('Este campo es obligatorio.');
  });

  it('shows a translated message when the credentials are wrong', async () => {
    const { fixture, root, type, submit } = setup();
    type('#email', 'ana@example.com');
    type('#password', 'incorrecta');

    submit();
    http.expectOne('/api/v1/auth/login').flush({ code: 'INVALID_CREDENTIALS' }, { status: 401, statusText: 'Unauthorized' });
    await settle();
    fixture.detectChanges();

    expect(root.querySelector('.form-error')?.textContent).toContain('Correo o contraseña incorrectos.');
  });

  it('goes to the home page after logging in', async () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const { type, submit } = setup();
    type('#email', 'ana@example.com');
    type('#password', 'contrasena-larga-1');

    submit();
    http.expectOne('/api/v1/auth/login').flush({ id: 'u1', email: 'ana@example.com' });
    await settle();
    http.expectOne('/api/v1/me/profile').flush({
      displayName: 'Ana',
      locale: 'es',
      experienceLevel: 'BEGINNER',
      goal: null,
      dailyGoalMinutes: 10,
      timezone: 'UTC',
    });
    await settle();

    expect(navigate).toHaveBeenCalledWith('/inicio');
  });
});
