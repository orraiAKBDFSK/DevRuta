import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Si el servidor responde 401 a una petición de la aplicación (la sesión caducó o se cerró desde otro
 * dispositivo), olvida la sesión local y lleva al usuario a la pantalla de entrada.
 * Las rutas /v1/auth/* se excluyen: allí un 401 es una respuesta normal (credenciales incorrectas o sin sesión).
 */
export const unauthorizedInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !request.url.includes('/v1/auth/')) {
        auth.clear();
        void router.navigateByUrl('/entrar');
      }
      return throwError(() => error);
    }),
  );
};
