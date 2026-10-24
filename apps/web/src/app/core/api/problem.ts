import { HttpErrorResponse } from '@angular/common/http';

/** Error de la API en formato RFC 9457 (`application/problem+json`) con un `code` estable. */
export interface ApiProblem {
  status: number;
  code: string;
  retryAfterSeconds?: number;
}

/** Convierte cualquier fallo HTTP en un problema con código; los fallos de red usan `NETWORK_ERROR`. */
export function problemOf(error: unknown): ApiProblem {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return { status: 0, code: 'NETWORK_ERROR' };
    }
    const code = typeof error.error?.code === 'string' ? error.error.code : defaultCode(error.status);
    const retryAfter = Number(error.headers?.get('Retry-After'));
    return { status: error.status, code, retryAfterSeconds: retryAfter > 0 ? retryAfter : undefined };
  }
  return { status: 0, code: 'UNKNOWN' };
}

function defaultCode(status: number): string {
  switch (status) {
    case 401:
      return 'UNAUTHENTICATED';
    case 403:
      return 'FORBIDDEN';
    case 429:
      return 'TOO_MANY_ATTEMPTS';
    default:
      return 'UNKNOWN';
  }
}
