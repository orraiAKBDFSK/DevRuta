import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { problemOf } from '../../core/api/problem';
import { AuthService } from '../../core/auth/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { validationKey } from '../../shared/forms';

@Component({
  selector: 'app-reset-password-page',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      <h2>{{ i18n.t('reset.title') }}</h2>

      @if (done()) {
        <p role="status">{{ i18n.t('reset.done') }}</p>
        <p><a routerLink="/entrar">{{ i18n.t('nav.login') }}</a></p>
      } @else if (!token()) {
        <p role="alert">{{ i18n.t('error.INVALID_TOKEN') }}</p>
        <p><a routerLink="/olvide-contrasena">{{ i18n.t('reset.requestNew') }}</a></p>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="field">
            <label for="newPassword">{{ i18n.t('field.newPassword') }}</label>
            <input
              id="newPassword"
              type="password"
              autocomplete="new-password"
              aria-describedby="password-hint"
              formControlName="newPassword"
            />
            <p id="password-hint" class="hint">{{ i18n.t('field.passwordHint') }}</p>
            @if (errorOf(); as key) {
              <p class="field-error" role="alert">{{ i18n.t(key) }}</p>
            }
          </div>

          <p class="form-error" role="alert" aria-live="assertive">{{ error() }}</p>
          @if (tokenRejected()) {
            <p><a routerLink="/olvide-contrasena">{{ i18n.t('reset.requestNew') }}</a></p>
          }

          <button type="submit" [disabled]="busy()">{{ i18n.t('reset.submit') }}</button>
        </form>
      }
    </section>
  `,
})
export class ResetPasswordPage {
  protected readonly i18n = inject(I18nService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  private readonly route = inject(ActivatedRoute);

  // El token se guarda en memoria y se quita de la dirección enseguida: así no queda en el historial del navegador.
  protected readonly token = signal(this.route.snapshot.queryParamMap.get('token') ?? '');

  protected readonly form = inject(FormBuilder).nonNullable.group({
    newPassword: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(128)]],
  });
  protected readonly busy = signal(false);
  protected readonly done = signal(false);
  protected readonly error = signal('');
  protected readonly tokenRejected = signal(false);

  constructor() {
    if (this.token()) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { token: null },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
    }
  }

  protected errorOf(): string | null {
    return validationKey(this.form.controls.newPassword);
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.tokenRejected.set(false);
    try {
      await this.auth.resetPassword(this.token(), this.form.getRawValue().newPassword);
      this.done.set(true);
    } catch (e) {
      const problem = problemOf(e);
      this.tokenRejected.set(problem.code === 'INVALID_TOKEN');
      this.error.set(this.i18n.t(`error.${problem.code}`));
    } finally {
      this.busy.set(false);
    }
  }
}
