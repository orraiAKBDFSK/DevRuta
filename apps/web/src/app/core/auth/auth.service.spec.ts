import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { I18nService } from '../i18n/i18n.service';
import { ProfileService } from '../profile/profile.service';
import { AuthService } from './auth.service';
import { Profile } from './models';

const ACCOUNT = { id: 'u1', email: 'ana@example.com', emailVerified: false };
const PROFILE: Profile = {
  displayName: 'Ana',
  locale: 'en',
  experienceLevel: 'BEGINNER',
  goal: null,
  dailyGoalMinutes: 10,
  timezone: 'UTC',
};

/** Deja correr las promesas pendientes para que el servicio emita su siguiente petición. */
const settle = () => new Promise<void>((resolve) => setTimeout(resolve));

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads an existing session together with the profile and applies its language', async () => {
    const done = auth.loadSession();
    http.expectOne('/api/v1/auth/me').flush(ACCOUNT);
    await settle();
    http.expectOne('/api/v1/me/profile').flush(PROFILE);
    await done;

    expect(auth.isAuthenticated()).toBe(true);
    expect(TestBed.inject(ProfileService).profile()?.displayName).toBe('Ana');
    expect(TestBed.inject(I18nService).locale()).toBe('en');
  });

  it('treats a 401 on startup as "no session" without failing', async () => {
    const done = auth.loadSession();
    http.expectOne('/api/v1/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await done;

    expect(auth.isAuthenticated()).toBe(false);
  });

  it('logs in and loads the profile', async () => {
    const done = auth.login('ana@example.com', 'contrasena-larga-1');
    const request = http.expectOne('/api/v1/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'ana@example.com', password: 'contrasena-larga-1' });
    request.flush(ACCOUNT);
    await settle();
    http.expectOne('/api/v1/me/profile').flush(PROFILE);
    await done;

    expect(auth.account()).toEqual(ACCOUNT);
  });

  it('rejects when the credentials are wrong and stays logged out', async () => {
    const done = auth.login('ana@example.com', 'mala');
    http.expectOne('/api/v1/auth/login').flush({ code: 'INVALID_CREDENTIALS' }, { status: 401, statusText: 'Unauthorized' });

    await expect(done).rejects.toBeTruthy();
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('clears the local session on logout even if the server call fails', async () => {
    auth.account.set(ACCOUNT);

    const done = auth.logout();
    http.expectOne('/api/v1/auth/logout').flush(null, { status: 500, statusText: 'Server Error' });

    await expect(done).rejects.toBeTruthy();
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('sends the password in the body when deleting the account and then forgets the session', async () => {
    auth.account.set(ACCOUNT);

    const done = auth.deleteAccount('contrasena-larga-1');
    const request = http.expectOne('/api/v1/me');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toEqual({ password: 'contrasena-larga-1' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await done;

    expect(auth.isAuthenticated()).toBe(false);
  });

  it('marks the account as verified after confirming the email', async () => {
    auth.account.set(ACCOUNT);

    const done = auth.verifyEmail('token-123');
    const request = http.expectOne('/api/v1/auth/verify-email');
    expect(request.request.body).toEqual({ token: 'token-123' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await done;

    expect(auth.account()?.emailVerified).toBe(true);
  });

  it('confirms the email for visitors without a session', async () => {
    const done = auth.verifyEmail('token-123');
    http.expectOne('/api/v1/auth/verify-email').flush(null, { status: 204, statusText: 'No Content' });
    await done;

    expect(auth.isAuthenticated()).toBe(false);
  });

  it('rejects an invalid verification token', async () => {
    const done = auth.verifyEmail('malo');
    http.expectOne('/api/v1/auth/verify-email').flush({ code: 'INVALID_TOKEN' }, { status: 400, statusText: 'Bad Request' });

    await expect(done).rejects.toBeTruthy();
  });

  it('requests a password reset link and a new verification email', async () => {
    const forgot = auth.forgotPassword('ana@example.com');
    const forgotRequest = http.expectOne('/api/v1/auth/forgot-password');
    expect(forgotRequest.request.body).toEqual({ email: 'ana@example.com' });
    forgotRequest.flush(null, { status: 202, statusText: 'Accepted' });
    await forgot;

    const resend = auth.resendVerification();
    http.expectOne('/api/v1/me/verification-email').flush(null, { status: 204, statusText: 'No Content' });
    await resend;
  });

  it('sends the token and the new password to reset the password', async () => {
    const done = auth.resetPassword('token-123', 'una-contrasena-larga');
    const request = http.expectOne('/api/v1/auth/reset-password');
    expect(request.request.body).toEqual({ token: 'token-123', newPassword: 'una-contrasena-larga' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await done;
  });
});
