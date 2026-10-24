import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { problemOf } from '../../core/api/problem';
import { AuthService } from '../../core/auth/auth.service';
import { ExperienceLevel, LearningGoal, Locale, ProfileUpdate } from '../../core/auth/models';
import { I18nService } from '../../core/i18n/i18n.service';
import { ProfileService } from '../../core/profile/profile.service';
import { validationKey } from '../../shared/forms';

type GoalChoice = LearningGoal | 'NONE';

@Component({
  selector: 'app-profile-page',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      <h2>{{ i18n.t('profile.title') }}</h2>

      <form [formGroup]="profileForm" (ngSubmit)="saveProfile()" novalidate>
        <div class="field">
          <label for="displayName">{{ i18n.t('field.displayName') }}</label>
          <input id="displayName" type="text" formControlName="displayName" />
          @if (errorOf(profileForm.controls.displayName); as key) {
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

        <div class="field">
          <label for="experienceLevel">{{ i18n.t('field.experience') }}</label>
          <select id="experienceLevel" formControlName="experienceLevel">
            @for (level of levels; track level) {
              <option [value]="level">{{ i18n.t('level.' + level) }}</option>
            }
          </select>
        </div>

        <div class="field">
          <label for="goal">{{ i18n.t('field.goal') }}</label>
          <select id="goal" formControlName="goal">
            @for (goal of goals; track goal) {
              <option [value]="goal">{{ i18n.t('goal.' + goal) }}</option>
            }
          </select>
        </div>

        <div class="field">
          <label for="dailyGoalMinutes">{{ i18n.t('field.dailyGoal') }}</label>
          <input id="dailyGoalMinutes" type="number" min="5" max="120" formControlName="dailyGoalMinutes" />
          @if (errorOf(profileForm.controls.dailyGoalMinutes); as key) {
            <p class="field-error" role="alert">{{ i18n.t(key) }}</p>
          }
        </div>

        <div class="field">
          <label for="timezone">{{ i18n.t('field.timezone') }}</label>
          <input id="timezone" type="text" list="timezones" formControlName="timezone" />
          <datalist id="timezones">
            @for (zone of timezones; track zone) {
              <option [value]="zone"></option>
            }
          </datalist>
        </div>

        <p class="form-ok" role="status">{{ profileMessage() }}</p>
        <p class="form-error" role="alert" aria-live="assertive">{{ profileError() }}</p>
        <button type="submit" [disabled]="busy()">{{ i18n.t('profile.save') }}</button>
      </form>
    </section>

    <section class="card">
      <h2>{{ i18n.t('profile.password.title') }}</h2>
      <form [formGroup]="passwordForm" (ngSubmit)="changePassword()" novalidate>
        <div class="field">
          <label for="currentPassword">{{ i18n.t('field.currentPassword') }}</label>
          <input id="currentPassword" type="password" autocomplete="current-password" formControlName="currentPassword" />
          @if (errorOf(passwordForm.controls.currentPassword); as key) {
            <p class="field-error" role="alert">{{ i18n.t(key) }}</p>
          }
        </div>
        <div class="field">
          <label for="newPassword">{{ i18n.t('field.newPassword') }}</label>
          <input id="newPassword" type="password" autocomplete="new-password" formControlName="newPassword" />
          @if (errorOf(passwordForm.controls.newPassword); as key) {
            <p class="field-error" role="alert">{{ i18n.t(key) }}</p>
          }
        </div>
        <p class="form-ok" role="status">{{ passwordMessage() }}</p>
        <p class="form-error" role="alert" aria-live="assertive">{{ passwordError() }}</p>
        <button type="submit" [disabled]="busy()">{{ i18n.t('profile.password.submit') }}</button>
      </form>
    </section>

    <section class="card danger">
      <h2>{{ i18n.t('profile.delete.title') }}</h2>
      <p>{{ i18n.t('profile.delete.warning') }}</p>
      <form [formGroup]="deleteForm" (ngSubmit)="deleteAccount()" novalidate>
        <div class="field">
          <label for="deletePassword">{{ i18n.t('field.password') }}</label>
          <input id="deletePassword" type="password" autocomplete="current-password" formControlName="password" />
          @if (errorOf(deleteForm.controls.password); as key) {
            <p class="field-error" role="alert">{{ i18n.t(key) }}</p>
          }
        </div>
        <p class="form-error" role="alert" aria-live="assertive">{{ deleteError() }}</p>
        <button type="submit" class="danger-button" [disabled]="busy()">{{ i18n.t('profile.delete.submit') }}</button>
      </form>
    </section>
  `,
})
export class ProfilePage {
  protected readonly i18n = inject(I18nService);
  private readonly auth = inject(AuthService);
  private readonly profiles = inject(ProfileService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly levels: ExperienceLevel[] = ['BEGINNER', 'SOME', 'EXPERIENCED'];
  protected readonly goals: GoalChoice[] = ['NONE', 'CAREER', 'STUDIES', 'HOBBY', 'PROJECT'];
  protected readonly timezones: string[] = this.supportedTimezones();

  protected readonly profileForm = this.fb.group({
    displayName: ['', [Validators.required, Validators.maxLength(50)]],
    locale: ['es' as Locale],
    experienceLevel: ['BEGINNER' as ExperienceLevel],
    goal: ['NONE' as GoalChoice],
    dailyGoalMinutes: [10, [Validators.required, Validators.min(5), Validators.max(120)]],
    timezone: ['UTC', [Validators.required, Validators.maxLength(64)]],
  });
  protected readonly passwordForm = this.fb.group({
    currentPassword: ['', [Validators.required]],
    newPassword: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(128)]],
  });
  protected readonly deleteForm = this.fb.group({
    password: ['', [Validators.required]],
  });

  protected readonly busy = signal(false);
  protected readonly profileMessage = signal('');
  protected readonly profileError = signal('');
  protected readonly passwordMessage = signal('');
  protected readonly passwordError = signal('');
  protected readonly deleteError = signal('');

  constructor() {
    // Rellena el formulario cuando el perfil está disponible (puede cargar después de abrir la página).
    effect(() => {
      const profile = this.profiles.profile();
      if (profile) {
        this.profileForm.reset({ ...profile, goal: profile.goal ?? 'NONE' });
      }
    });
  }

  protected errorOf(control: Parameters<typeof validationKey>[0]): string | null {
    return validationKey(control);
  }

  protected async saveProfile(): Promise<void> {
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.profileMessage.set('');
    this.profileError.set('');
    try {
      const { goal, ...rest } = this.profileForm.getRawValue();
      const changes: ProfileUpdate = { ...rest, ...(goal === 'NONE' ? {} : { goal }) };
      const saved = await this.profiles.update(changes);
      this.i18n.use(saved.locale);
      this.profileMessage.set(this.i18n.t('profile.saved'));
    } catch (e) {
      this.profileError.set(this.i18n.t(`error.${problemOf(e).code}`));
    } finally {
      this.busy.set(false);
    }
  }

  protected async changePassword(): Promise<void> {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.passwordMessage.set('');
    this.passwordError.set('');
    try {
      const { currentPassword, newPassword } = this.passwordForm.getRawValue();
      await this.auth.changePassword(currentPassword, newPassword);
      this.passwordForm.reset();
      this.passwordMessage.set(this.i18n.t('profile.password.done'));
    } catch (e) {
      this.passwordError.set(this.i18n.t(`error.${problemOf(e).code}`));
    } finally {
      this.busy.set(false);
    }
  }

  protected async deleteAccount(): Promise<void> {
    if (this.deleteForm.invalid) {
      this.deleteForm.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.deleteError.set('');
    try {
      await this.auth.deleteAccount(this.deleteForm.getRawValue().password);
      await this.router.navigateByUrl('/entrar');
    } catch (e) {
      this.deleteError.set(this.i18n.t(`error.${problemOf(e).code}`));
    } finally {
      this.busy.set(false);
    }
  }

  private supportedTimezones(): string[] {
    try {
      return Intl.supportedValuesOf('timeZone');
    } catch {
      return [];
    }
  }
}
