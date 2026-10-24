import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/api/problem';
import { AuthService } from '../../core/auth/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { validationKey } from '../../shared/forms';

@Component({
  selector: 'app-forgot-password-page',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      <h2>{{ i18n.t('forgot.title') }}</h2>

      @if (sent()) {
        <p role="status">{{ i18n.t('forgot.sent') }}</p>
      } @else {
        <p>{{ i18n.t('forgot.intro') }}</p>
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="field">
            <label for="email">{{ i18n.t('field.email') }}</label>
            <input id="email" type="email" autocomplete="email" formControlName="email" />
            @if (errorOf(); as key) {
              <p class="field-error" role="alert">{{ i18n.t(key) }}</p>
            }
          </div>

          <p class="form-error" role="alert" aria-live="assertive">{{ error() }}</p>

          <button type="submit" [disabled]="busy()">{{ i18n.t('forgot.submit') }}</button>
        </form>
      }

      <p class="alt">
        <a routerLink="/entrar">{{ i18n.t('forgot.back') }}</a>
      </p>
    </section>
  `,
})
export class ForgotPasswordPage {
  protected readonly i18n = inject(I18nService);
  private readonly auth = inject(AuthService);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });
  protected readonly busy = signal(false);
  protected readonly sent = signal(false);
  protected readonly error = signal('');

  protected errorOf(): string | null {
    return validationKey(this.form.controls.email);
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set('');
    try {
      await this.auth.forgotPassword(this.form.getRawValue().email);
      // Mismo mensaje exista o no la cuenta: la pantalla tampoco revela qué correos están registrados.
      this.sent.set(true);
    } catch (e) {
      this.error.set(this.i18n.t(`error.${problemOf(e).code}`));
    } finally {
      this.busy.set(false);
    }
  }
}
