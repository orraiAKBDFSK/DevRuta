import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { I18nService } from './i18n.service';

/** Zona horaria del navegador (p. ej. `America/Bogota`); la API la usa para decidir qué día es para el usuario. */
function browserTimeZone(): string | null {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || null;
  } catch {
    return null;
  }
}

/**
 * Pide a la API el contenido en el idioma de la interfaz y le indica la zona horaria del usuario
 * (solo a rutas /api de la misma aplicación).
 */
export const languageInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/')) {
    return next(request);
  }
  const locale = inject(I18nService).locale();
  const headers: Record<string, string> = { 'Accept-Language': locale };
  const zone = browserTimeZone();
  if (zone) {
    headers['X-Time-Zone'] = zone;
  }
  return next(request.clone({ setHeaders: headers }));
};
