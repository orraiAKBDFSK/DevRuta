import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { problemOf } from '../../core/api/problem';
import { AuthService } from '../../core/auth/auth.service';
import { Locale } from '../../core/auth/models';
import { I18nService } from '../../core/i18n/i18n.service';
import { validationKey } from '../../shared/forms';

@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      <h2>{{ i18n.t('register.title') }}</h2>

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="field">
          <label for="displayName">{{ i18n.t('field.displayName') }}</label>
          <input id="displayName" type="text" autocomplete="nickname" formControlName="displayName" />
          @if (errorOf('displayName'); as key) {
            <p class="field-error" role="alert">{{ i18n.t(key) }}</p>
          }
        </div>

        <div class="field">
          <label for="email">{{ i18n.t('field.email') }}</label>
          <input id="email" type="email" autocomplete="email" formControlName="email" />
          @if (errorOf('email'); as key) {
            <p class="field-error" role="alert">{{ i18n.t(key) }}</p>
          }
        </div>

        <div class="field">
          <label for="password">{{ i18n.t('field.password') }}</label>
          <input
            id="password"
            type="password"
            autocomplete="new-password"
            aria-describedby="password-hint"
            formControlName="password"
          />
          <p id="password-hint" class="hint">{{ i18n.t('field.passwordHint') }}</p>
          @if (errorOf('password'); as key) {
            <p class="field-error" role="alert">{{ i18n.t(key) }}</p>
          }
        </div>

        <div class="field">
          <label for="locale">{{ i18n.t('field.locale') }}</label>
          <select id="locale" formControlName="locale">
            <option value="es">Español</option>
            <option value="en">English</option>
          </select>
        </div>

        <div class="field checkbox">
          <input id="acceptTerms" type="checkbox" formControlName="acceptTerms" />
          <label for="acceptTerms">{{ i18n.t('register.terms') }}</label>
          @if (termsInvalid()) {
            <p class="field-error" role="alert">{{ i18n.t('validation.required') }}</p>
          }
        </div>

        <p class="form-error" role="alert" aria-live="assertive">{{ error() }}</p>

        <button type="submit" [disabled]="busy()">{{ i18n.t('register.submit') }}</button>
      </form>

      <p class="alt">
        {{ i18n.t('register.haveAccount') }}
        <a routerLink="/entrar">{{ i18n.t('nav.login') }}</a>
      </p>
    </section>
  `,
})
export class RegisterPage {
  protected readonly i18n = inject(I18nService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    displayName: ['', [Validators.required, Validators.maxLength(50)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(128)]],
    locale: [this.i18n.locale() as Locale],
    acceptTerms: [false, [Validators.requiredTrue]],
  });
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  constructor() {
    // Cambiar el idioma en el formulario cambia de inmediato el idioma de la interfaz.
    this.form.controls.locale.valueChanges.pipe(takeUntilDestroyed()).subscribe((locale) => this.i18n.use(locale));
  }

  protected errorOf(name: 'displayName' | 'email' | 'password'): string | null {
    return validationKey(this.form.controls[name]);
  }

  protected termsInvalid(): boolean {
    const control = this.form.controls.acceptTerms;
    return control.invalid && (control.touched || control.dirty);
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set('');
    try {
      await this.auth.register(this.form.getRawValue());
      await this.router.navigateByUrl('/inicio');
    } catch (e) {
      this.error.set(this.i18n.t(`error.${problemOf(e).code}`));
    } finally {
      this.busy.set(false);
    }
  }
}
