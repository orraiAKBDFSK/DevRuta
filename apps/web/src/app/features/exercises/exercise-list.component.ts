import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { LearningService } from '../../core/learning/learning.service';
import { Exercise } from '../../core/learning/models';
import { ExerciseCard } from './exercise-card.component';

type State = 'loading' | 'ready' | 'error';

/** Los ejercicios de una lección con un contador de los resueltos. Recarga al cambiar de lección o de idioma. */
@Component({
  selector: 'app-exercise-list',
  imports: [ExerciseCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="exercises" [attr.aria-label]="i18n.t('exercises.title')">
      <h3>{{ i18n.t('exercises.title') }}</h3>

      @switch (state()) {
        @case ('loading') {
          <p role="status">{{ i18n.t('common.loading') }}</p>
        }
        @case ('error') {
          <p role="alert">{{ i18n.t('error.UNKNOWN') }}</p>
        }
        @case ('ready') {
          @if (exercises().length === 0) {
            <p class="hint">{{ i18n.t('exercises.none') }}</p>
          } @else {
            <p class="hint" role="status">
              {{ i18n.t('exercises.progress', { solved: '' + solvedCount(), total: '' + evaluableCount() }) }}
            </p>
            @for (exercise of exercises(); track exercise.id; let i = $index) {
              <app-exercise-card
                [exercise]="exercise"
                [index]="i + 1"
                (progress)="markSolved($event.id)"
              />
            }
          }
        }
      }
    </section>
  `,
})
export class ExerciseList {
  readonly lessonId = input.required<string>();

  protected readonly i18n = inject(I18nService);
  private readonly learning = inject(LearningService);

  protected readonly state = signal<State>('loading');
  protected readonly exercises = signal<Exercise[]>([]);
  private readonly newlySolved = signal<ReadonlySet<string>>(new Set());

  protected readonly evaluableCount = computed(() => this.exercises().filter((e) => e.evaluable).length);
  protected readonly solvedCount = computed(
    () => this.exercises().filter((e) => e.evaluable && (e.solved || this.newlySolved().has(e.id))).length,
  );

  constructor() {
    effect(() => {
      const id = this.lessonId();
      this.i18n.locale();
      untracked(() => void this.load(id));
    });
  }

  protected markSolved(id: string): void {
    this.newlySolved.update((set) => new Set(set).add(id));
  }

  private async load(lessonId: string): Promise<void> {
    try {
      this.exercises.set(await this.learning.exercises(lessonId));
      this.newlySolved.set(new Set());
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }
}
