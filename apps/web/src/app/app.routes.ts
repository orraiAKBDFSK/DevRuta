import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'inicio' },
  {
    path: 'inicio',
    canActivate: [authGuard],
    loadComponent: () => import('./features/home/home.page').then((m) => m.HomePage),
  },
  {
    path: 'ruta/:courseId',
    canActivate: [authGuard],
    loadComponent: () => import('./features/catalog/course-map.page').then((m) => m.CourseMapPage),
  },
  {
    path: 'leccion/:lessonId',
    canActivate: [authGuard],
    loadComponent: () => import('./features/catalog/lesson.page').then((m) => m.LessonPage),
  },
  {
    path: 'progreso',
    canActivate: [authGuard],
    loadComponent: () => import('./features/progress/progress.page').then((m) => m.ProgressPage),
  },
  {
    path: 'perfil',
    canActivate: [authGuard],
    loadComponent: () => import('./features/profile/profile.page').then((m) => m.ProfilePage),
  },
  {
    path: 'entrar',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'registro',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register.page').then((m) => m.RegisterPage),
  },
  {
    path: 'olvide-contrasena',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/forgot-password.page').then((m) => m.ForgotPasswordPage),
  },
  {
    path: 'restablecer',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/reset-password.page').then((m) => m.ResetPasswordPage),
  },
  {
    // Sin guardas: el enlace del correo debe funcionar con o sin sesión abierta.
    path: 'verificar-correo',
    loadComponent: () => import('./features/auth/verify-email.page').then((m) => m.VerifyEmailPage),
  },
  { path: '**', redirectTo: 'inicio' },
];
