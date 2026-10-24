import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { I18nService } from '../i18n/i18n.service';
import { ProfileService } from '../profile/profile.service';
import { Account, RegisterRequest } from './models';

/**
 * Estado de la sesión del usuario. La sesión vive en una cookie HttpOnly que el navegador envía solo;
 * aquí únicamente se guarda quién es el usuario para mostrar la interfaz correcta.
 * Angular envía el token CSRF (cookie XSRF-TOKEN -> cabecera X-XSRF-TOKEN) sin código adicional.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly profiles = inject(ProfileService);
  private readonly i18n = inject(I18nService);

  readonly account = signal<Account | null>(null);
  readonly isAuthenticated = computed(() => this.account() !== null);

  /** Pregunta al servidor si ya hay una sesión abierta. También recibe la cookie CSRF. Nunca falla. */
  async loadSession(): Promise<void> {
    try {
      this.account.set(await firstValueFrom(this.http.get<Account>('/api/v1/auth/me')));
      await this.loadProfile();
    } catch {
      this.account.set(null);
    }
  }

  async register(request: RegisterRequest): Promise<void> {
    this.account.set(await firstValueFrom(this.http.post<Account>('/api/v1/auth/register', request)));
    await this.loadProfile();
    // La zona horaria se usará para las rachas diarias; es información útil pero no imprescindible.
    try {
      await this.profiles.update({ timezone: Intl.DateTimeFormat().resolvedOptions().timeZone });
    } catch {
      // Se puede configurar después en el perfil.
    }
  }

  async login(email: string, password: string): Promise<void> {
    this.account.set(await firstValueFrom(this.http.post<Account>('/api/v1/auth/login', { email, password })));
    await this.loadProfile();
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>('/api/v1/auth/logout', {}));
    } finally {
      this.clear();
    }
  }

  /** Confirma el correo con el token del enlace recibido. Funciona con o sin sesión abierta. */
  async verifyEmail(token: string): Promise<void> {
    await firstValueFrom(this.http.post<void>('/api/v1/auth/verify-email', { token }));
    const current = this.account();
    if (current) {
      this.account.set({ ...current, emailVerified: true });
    }
  }

  async resendVerification(): Promise<void> {
    await firstValueFrom(this.http.post<void>('/api/v1/me/verification-email', {}));
  }

  /** El servidor responde igual exista o no el correo; aquí tampoco se puede saber. */
  async forgotPassword(email: string): Promise<void> {
    await firstValueFrom(this.http.post<void>('/api/v1/auth/forgot-password', { email }));
  }

  async resetPassword(token: string, newPassword: string): Promise<void> {
    await firstValueFrom(this.http.post<void>('/api/v1/auth/reset-password', { token, newPassword }));
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await firstValueFrom(this.http.post<void>('/api/v1/me/password', { currentPassword, newPassword }));
  }

  async deleteAccount(password: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>('/api/v1/me', { body: { password } }));
    this.clear();
  }

  /** Olvida la sesión local (por ejemplo, cuando el servidor responde 401). */
  clear(): void {
    this.account.set(null);
    this.profiles.clear();
  }

  private async loadProfile(): Promise<void> {
    try {
      this.i18n.use((await this.profiles.load()).locale);
    } catch {
      // Sin perfil se mantiene el idioma actual; la sesión sigue siendo válida.
    }
  }
}
