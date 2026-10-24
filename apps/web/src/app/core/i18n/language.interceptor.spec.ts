import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { I18nService } from './i18n.service';
import { languageInterceptor } from './language.interceptor';

describe('languageInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([languageInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  it('sends the interface language to the API', () => {
    TestBed.inject(I18nService).use('en');

    http.get('/api/v1/catalog/courses').subscribe();

    expect(controller.expectOne('/api/v1/catalog/courses').request.headers.get('Accept-Language')).toBe('en');
  });

  it('sends the time zone of the browser so streaks follow the user’s own day', () => {
    http.get('/api/v1/progress').subscribe();

    const zone = controller.expectOne('/api/v1/progress').request.headers.get('X-Time-Zone');
    expect(zone).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
  });

  it('does not send the time zone to other servers', () => {
    http.get('https://ejemplo.com/datos').subscribe();

    expect(controller.expectOne('https://ejemplo.com/datos').request.headers.has('X-Time-Zone')).toBe(false);
  });

  it('follows the language when it changes', () => {
    const i18n = TestBed.inject(I18nService);
    i18n.use('es');
    http.get('/api/a').subscribe();
    i18n.use('en');
    http.get('/api/b').subscribe();

    expect(controller.expectOne('/api/a').request.headers.get('Accept-Language')).toBe('es');
    expect(controller.expectOne('/api/b').request.headers.get('Accept-Language')).toBe('en');
  });

  it('does not add the header to requests to other servers', () => {
    http.get('https://ejemplo.com/datos').subscribe();

    expect(controller.expectOne('https://ejemplo.com/datos').request.headers.has('Accept-Language')).toBe(false);
  });
});
