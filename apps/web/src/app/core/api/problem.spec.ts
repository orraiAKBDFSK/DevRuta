import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { problemOf } from './problem';

describe('problemOf', () => {
  it('uses the stable code sent by the API', () => {
    const error = new HttpErrorResponse({ status: 409, error: { code: 'EMAIL_ALREADY_REGISTERED' } });

    expect(problemOf(error)).toEqual({ status: 409, code: 'EMAIL_ALREADY_REGISTERED', retryAfterSeconds: undefined });
  });

  it('reads Retry-After on rate limiting', () => {
    const error = new HttpErrorResponse({
      status: 429,
      headers: new HttpHeaders({ 'Retry-After': '120' }),
      error: { code: 'TOO_MANY_ATTEMPTS' },
    });

    expect(problemOf(error)).toEqual({ status: 429, code: 'TOO_MANY_ATTEMPTS', retryAfterSeconds: 120 });
  });

  it('falls back to a code derived from the status when the body has none', () => {
    expect(problemOf(new HttpErrorResponse({ status: 403 })).code).toBe('FORBIDDEN');
    expect(problemOf(new HttpErrorResponse({ status: 500 })).code).toBe('UNKNOWN');
  });

  it('reports network failures', () => {
    expect(problemOf(new HttpErrorResponse({ status: 0 })).code).toBe('NETWORK_ERROR');
  });

  it('handles errors that are not HTTP errors', () => {
    expect(problemOf(new Error('boom')).code).toBe('UNKNOWN');
  });
});
