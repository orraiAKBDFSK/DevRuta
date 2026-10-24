import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { ACHIEVEMENT_CODES, ProgressSummary } from '../../core/learning/models';
import { ProgressService } from '../../core/learning/progress.service';

type State = 'loading' | 'ready' | 'error';

/** Nivel, XP, racha y logros. La barra usa un elemento <progress> nativo, que ya es accesible. */
@Component({
  selector: 'app-progress-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>{{ i18n.t('progress.title') }}</h2>

    @switch (state()) {
      @case ('loading') {
        <p role="status">{{ i18n.t('common.loading') }}</p>
      }
      @case ('error') {
        <p role="alert">{{ i18n.t('error.UNKNOWN') }}</p>
      }
      @case ('ready') {
        @if (summary(); as s) {
          <section class="card" aria-labelledby="level-title">
            <h3 id="level-title">{{ i18n.t('progress.level', { n: '' + s.level }) }}</h3>
            <p>{{ i18n.t('progress.xpTotal', { xp: '' + s.xp }) }}</p>
            <progress
              [attr.aria-label]="i18n.t('progress.bar')"
              [value]="s.xp - s.levelStartXp"
              [max]="s.nextLevelXp - s.levelStartXp"
            ></progress>
            <p class="hint">{{ i18n.t('progress.toNext', { xp: '' + (s.nextLevelXp - s.xp), n: '' + (s.level + 1) }) }}</p>
          </section>

          <section class="card" aria-labelledby="streak-title">
            <h3 id="streak-title">{{ i18n.t('progress.streak') }}</h3>
            @if (s.streak > 0) {
              <p class="big">{{ s.streak === 1 ? i18n.t('progress.streakDay') : i18n.t('progress.streakDays', { n: '' + s.streak }) }}</p>
              <p>{{ s.activeToday ? i18n.t('progress.activeToday') : i18n.t('progress.notToday') }}</p>
            } @else {
              <p>{{ i18n.t('progress.streakNone') }}</p>
            }
            <p class="hint">{{ i18n.t('progress.longest', { n: '' + s.longestStreak }) }} · {{ i18n.t('progress.utc') }}</p>
          </section>

          <section class="card" aria-labelledby="totals-title">
            <h3 id="totals-title" class="visually-hidden">{{ i18n.t('progress.title') }}</h3>
            <p>{{ i18n.t('progress.solved') }}: <strong>{{ s.solvedExercises }}</strong></p>
            <p>{{ i18n.t('progress.lessons') }}: <strong>{{ s.completedLessons.length }}</strong></p>
          </section>

          <section aria-labelledby="ach-title">
            <h3 id="ach-title">{{ i18n.t('progress.achievements') }}</h3>
            <ul class="achievements">
              @for (a of achievements(); track a.code) {
                <li [class.unlocked]="a.unlockedAt !== null">
                  <strong>{{ i18n.t('achievement.' + a.code + '.name') }}</strong>
                  <span>{{ i18n.t('achievement.' + a.code + '.desc') }}</span>
                  <span class="hint">{{ a.unlockedAt !== null ? i18n.t('progress.unlocked', { date: format(a.unlockedAt) }) : i18n.t('progress.locked') }}</span>
                </li>
              }
            </ul>
          </section>
        }
      }
    }
  `,
})
export class ProgressPage {
  protected readonly i18n = inject(I18nService);
  private readonly progress = inject(ProgressService);

  protected readonly state = signal<State>('loading');
  protected readonly summary = signal<ProgressSummary | null>(null);

  /** Todos los logros, con la fecha si ya están desbloqueados. */
  protected readonly achievements = computed(() => {
    const unlocked = new Map((this.summary()?.achievements ?? []).map((a) => [a.code, a.unlockedAt]));
    return ACHIEVEMENT_CODES.map((code) => ({ code, unlockedAt: unlocked.get(code) ?? null }));
  });

  constructor() {
    void this.load();
  }

  protected format(iso: string): string {
    return new Intl.DateTimeFormat(this.i18n.locale(), { dateStyle: 'medium', timeZone: 'UTC' }).format(new Date(iso));
  }

  private async load(): Promise<void> {
    try {
      this.summary.set(await this.progress.summary());
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }
}
