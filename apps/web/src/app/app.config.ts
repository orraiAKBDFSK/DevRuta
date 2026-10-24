import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { unauthorizedInterceptor } from './core/auth/unauthorized.interceptor';
import { languageInterceptor } from './core/i18n/language.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // La protección CSRF de Angular (cookie XSRF-TOKEN -> cabecera X-XSRF-TOKEN) viene activada por defecto.
    provideHttpClient(withFetch(), withInterceptors([languageInterceptor, unauthorizedInterceptor])),
    provideRouter(routes),
    // Antes de pintar la primera pantalla se averigua si ya hay una sesión abierta.
    provideAppInitializer(() => inject(AuthService).loadSession()),
  ],
};
