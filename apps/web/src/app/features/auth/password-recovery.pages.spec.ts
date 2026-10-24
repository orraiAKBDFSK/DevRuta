import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import { ForgotPasswordPage } from './forgot-password.page';
import { ResetPasswordPage } from './reset-password.page';
import { VerifyEmailPage } from './verify-email.page';

const settle = () => new Promise<void>((resolve) => setTimeout(resolve));

function configure(token: string | null) {
  localStorage.clear();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) } },
      },
    ],
  });
  TestBed.inject(I18nService).use('es');
  // Evita navegar de verdad: solo interesa comprobar que el token se quita de la dirección.
  const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  return { http: TestBed.inject(HttpTestingController), navigate };
}

function helpers(root: HTMLElement) {
  return {
    type: (selector: string, value: string) => {
      const input = root.querySelector<HTMLInputElement>(selector)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    },
    submit: () => root.querySelector('form')!.dispatchEvent(new Event('submit')),
  };
}

describe('ForgotPasswordPage', () => {
  it('shows the same neutral message and never says whether the email exists', async () => {
    const { http } = configure(null);
    const fixture = TestBed.createComponent(ForgotPasswordPage);
    fixture.detectChanges();
    const { type, submit } = helpers(fixture.nativeElement);

    type('#email', 'nadie@example.com');
    submit();
    const request = http.expectOne('/api/v1/auth/forgot-password');
    expect(request.request.body).toEqual({ email: 'nadie@example.com' });
    request.flush(null, { status: 202, statusText: 'Accepted' });
    await settle();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Si ese correo está registrado');
  });

  it('validates the email before calling the API', async () => {
    const { http } = configure(null);
    const fixture = TestBed.createComponent(ForgotPasswordPage);
    fixture.detectChanges();
    const { type, submit } = helpers(fixture.nativeElement);

    type('#email', 'no-es-un-correo');
    submit();
    await fixture.whenStable();
    fixture.detectChanges();

    http.expectNone('/api/v1/auth/forgot-password');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Escribe un correo válido.');
  });
});

describe('ResetPasswordPage', () => {
  it('removes the token from the address bar and resets the password', async () => {
    const { http, navigate } = configure('token-abc');
    const fixture = TestBed.createComponent(ResetPasswordPage);
    fixture.detectChanges();
    const { type, submit } = helpers(fixture.nativeElement);

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { token: null }, replaceUrl: true }),
    );

    type('#newPassword', 'una-contrasena-larga');
    submit();
    const request = http.expectOne('/api/v1/auth/reset-password');
    expect(request.request.body).toEqual({ token: 'token-abc', newPassword: 'una-contrasena-larga' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Contraseña cambiada');
  });

  it('offers a new link when the token is rejected', async () => {
    const { http } = configure('token-viejo');
    const fixture = TestBed.createComponent(ResetPasswordPage);
    fixture.detectChanges();
    const { type, submit } = helpers(fixture.nativeElement);

    type('#newPassword', 'una-contrasena-larga');
    submit();
    http.expectOne('/api/v1/auth/reset-password').flush({ code: 'INVALID_TOKEN' }, { status: 400, statusText: 'Bad Request' });
    await settle();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('El enlace no es válido o ha caducado.');
    expect(text).toContain('Pedir un enlace nuevo');
  });

  it('explains the problem when the page is opened without a token', () => {
    configure(null);
    const fixture = TestBed.createComponent(ResetPasswordPage);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('form')).toBeNull();
    expect(root.textContent).toContain('El enlace no es válido o ha caducado.');
  });

  it('blocks short passwords without calling the API', async () => {
    const { http } = configure('token-abc');
    const fixture = TestBed.createComponent(ResetPasswordPage);
    fixture.detectChanges();
    const { type, submit } = helpers(fixture.nativeElement);

    type('#newPassword', 'corta');
    submit();
    await fixture.whenStable();
    fixture.detectChanges();

    http.expectNone('/api/v1/auth/reset-password');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Es demasiado corto.');
  });
});

describe('VerifyEmailPage', () => {
  it('confirms the email using the token from the link', async () => {
    const { http, navigate } = configure('token-abc');
    const fixture = TestBed.createComponent(VerifyEmailPage);
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalled();
    const request = http.expectOne('/api/v1/auth/verify-email');
    expect(request.request.body).toEqual({ token: 'token-abc' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Tu correo quedó verificado.');
  });

  it('shows an error for an expired link', async () => {
    const { http } = configure('token-viejo');
    const fixture = TestBed.createComponent(VerifyEmailPage);
    fixture.detectChanges();

    http.expectOne('/api/v1/auth/verify-email').flush({ code: 'INVALID_TOKEN' }, { status: 400, statusText: 'Bad Request' });
    await settle();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('El enlace no es válido o ha caducado.');
  });

  it('fails without calling the API when there is no token', async () => {
    const { http } = configure(null);
    const fixture = TestBed.createComponent(VerifyEmailPage);
    fixture.detectChanges();
    await settle();
    fixture.detectChanges();

    http.expectNone('/api/v1/auth/verify-email');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('El enlace no es válido o ha caducado.');
  });
});
