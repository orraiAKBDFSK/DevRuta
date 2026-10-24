import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { problemOf } from '../../core/api/problem';
import { AuthService } from '../../core/auth/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';

type State = 'checking' | 'verified' | 'failed';

/** Destino del enlace del correo de verificación: confirma el token al abrirse. */
@Component({
  selector: 'app-verify-email-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      <h2>{{ i18n.t('verify.title') }}</h2>

      @switch (state()) {
        @case ('checking') {
          <p role="status">{{ i18n.t('verify.checking') }}</p>
        }
        @case ('verified') {
          <p role="status">{{ i18n.t('verify.done') }}</p>
          <p>
            <a routerLink="/inicio">{{ i18n.t('nav.home') }}</a>
          </p>
        }
        @case ('failed') {
          <p role="alert">{{ i18n.t('error.' + errorCode()) }}</p>
          <p>
            <a routerLink="/inicio">{{ i18n.t('verify.resendHint') }}</a>
          </p>
        }
      }
    </section>
  `,
})
export class VerifyEmailPage {
  protected readonly i18n = inject(I18nService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly state = signal<State>('checking');
  protected readonly errorCode = signal('INVALID_TOKEN');

  constructor() {
    const route = inject(ActivatedRoute);
    const token = route.snapshot.queryParamMap.get('token');
    // El token sale de la dirección enseguida para que no quede en el historial del navegador.
    void this.router.navigate([], {
      relativeTo: route,
      queryParams: { token: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    void this.verify(token);
  }

  private async verify(token: string | null): Promise<void> {
    if (!token) {
      this.state.set('failed');
      return;
    }
    try {
      await this.auth.verifyEmail(token);
      this.state.set('verified');
    } catch (e) {
      this.errorCode.set(problemOf(e).code);
      this.state.set('failed');
    }
  }
}
