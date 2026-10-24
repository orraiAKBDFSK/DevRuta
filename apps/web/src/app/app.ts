import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { problemOf } from './core/api/problem';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { Locale } from './core/auth/models';
import { I18nService } from './core/i18n/i18n.service';
import { ProfileService } from './core/profile/profile.service';

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly i18n = inject(I18nService);
  private readonly profiles = inject(ProfileService);
  private readonly router = inject(Router);

  protected readonly verificationMessage = signal('');

  protected async resendVerification(): Promise<void> {
    try {
      await this.auth.resendVerification();
      this.verificationMessage.set(this.i18n.t('banner.sent'));
    } catch (e) {
      this.verificationMessage.set(this.i18n.t(`error.${problemOf(e).code}`));
    }
  }

  protected async logout(): Promise<void> {
    try {
      await this.auth.logout();
    } finally {
      await this.router.navigateByUrl('/entrar');
    }
  }

  protected async changeLocale(event: Event): Promise<void> {
    const locale = (event.target as HTMLSelectElement).value as Locale;
    this.i18n.use(locale);
    if (this.auth.isAuthenticated()) {
      try {
        await this.profiles.update({ locale });
      } catch {
        // El idioma de la interfaz ya cambió; se volverá a intentar guardar en el próximo cambio.
      }
    }
  }
}
