import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { problemOf } from '../../core/api/problem';
import { AuthService } from '../../core/auth/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { validationKey } from '../../shared/forms';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      <h2>{{ i18n.t('login.title') }}</h2>

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="field">
          <label for="email">{{ i18n.t('field.email') }}</label>
          <input id="email" type="email" autocomplete="email" formControlName="email" />
          @if (errorOf('email'); as key) {
            <p class="field-error" role="alert">{{ i18n.t(key) }}</p>
          }
        </div>

        <div class="field">
          <label for="password">{{ i18n.t('field.password') }}</label>
          <input id="password" type="password" autocomplete="current-password" formControlName="password" />
          @if (errorOf('password'); as key) {
            <p class="field-error" role="alert">{{ i18n.t(key) }}</p>
          }
        </div>

        <p class="form-error" role="alert" aria-live="assertive">{{ error() }}</p>

        <button type="submit" [disabled]="busy()">{{ i18n.t('login.submit') }}</button>
      </form>

      <p class="alt">
        <a routerLink="/olvide-contrasena">{{ i18n.t('login.forgot') }}</a>
      </p>

      <p class="alt">
        {{ i18n.t('login.noAccount') }}
        <a routerLink="/registro">{{ i18n.t('nav.register') }}</a>
      </p>
    </section>
  `,
})
export class LoginPage {
  protected readonly i18n = inject(I18nService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  protected errorOf(name: 'email' | 'password'): string | null {
    return validationKey(this.form.controls[name]);
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set('');
    try {
      const { email, password } = this.form.getRawValue();
      await this.auth.login(email, password);
      await this.router.navigateByUrl('/inicio');
    } catch (e) {
      this.error.set(this.i18n.t(`error.${problemOf(e).code}`));
    } finally {
      this.busy.set(false);
    }
  }
}
