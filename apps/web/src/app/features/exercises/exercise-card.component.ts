import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import { problemOf } from '../../core/api/problem';
import { I18nService } from '../../core/i18n/i18n.service';
import { LearningService } from '../../core/learning/learning.service';
import { Answer, AttemptResult, Exercise } from '../../core/learning/models';
import { renderInlineMarkdown, renderMarkdown } from '../../shared/markdown';

type Feedback = 'none' | 'correct' | 'wrong';

/**
 * Un ejercicio interactivo. El navegador solo recoge la respuesta; la corrige el servidor. Todo se puede usar con
 * teclado: opciones con botones de radio, líneas con botones de subir y bajar (sin arrastrar), huecos con campos de texto.
 */
@Component({
  selector: 'app-exercise-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="card exercise" [attr.aria-labelledby]="'ex-' + exercise().id">
      <header class="exercise-header">
        <h4 [id]="'ex-' + exercise().id">{{ i18n.t('exercise.n', { n: '' + index() }) }}</h4>
        <span class="hint">{{ i18n.t('exercise.difficulty', { n: '' + exercise().difficulty }) }}</span>
        @if (solved()) {
          <span class="badge ok">{{ i18n.t('exercise.solved') }}</span>
        }
      </header>

      <div class="markdown" [innerHTML]="promptHtml()"></div>

      @switch (exercise().type) {
        @case ('MULTIPLE_CHOICE') {
          <div role="radiogroup" [attr.aria-labelledby]="'ex-' + exercise().id" class="options">
            @for (option of exercise().options ?? []; track $index) {
              <label class="option">
                <input
                  type="radio"
                  [name]="'choice-' + exercise().id"
                  [checked]="choice() === $index"
                  (change)="choice.set($index)"
                />
                <span [innerHTML]="inline(option)"></span>
              </label>
            }
          </div>
        }
        @case ('PREDICT_OUTPUT') {
          <pre class="code"><code>{{ exercise().code }}</code></pre>
          <label class="field-label" [for]="'out-' + exercise().id">{{ i18n.t('exercise.yourOutput') }}</label>
          <textarea
            [id]="'out-' + exercise().id"
            rows="3"
            spellcheck="false"
            autocomplete="off"
            [value]="text()"
            (input)="text.set($any($event.target).value)"
          ></textarea>
        }
        @case ('ORDER_LINES') {
          <p class="hint">{{ i18n.t('exercise.orderHelp') }}</p>
          <ol class="order-list">
            @for (line of order(); track line; let i = $index; let first = $first; let last = $last) {
              <li>
                <code class="code-line">{{ line }}</code>
                <span class="order-buttons">
                  <button
                    type="button"
                    class="small"
                    [disabled]="first"
                    [attr.aria-label]="i18n.t('exercise.up', { n: '' + (i + 1) })"
                    (click)="move(i, -1)"
                  >
                    ↑
                  </button>
                  <button
                    type="button"
                    class="small"
                    [disabled]="last"
                    [attr.aria-label]="i18n.t('exercise.down', { n: '' + (i + 1) })"
                    (click)="move(i, 1)"
                  >
                    ↓
                  </button>
                </span>
              </li>
            }
          </ol>
        }
        @case ('FILL_CODE') {
          <!-- Sin espacios entre las etiquetas: dentro de <pre> todo se vería. -->
          <pre class="code fill"><code>@for (part of parts(); track $index; let i = $index; let last = $last) {<span>{{ part }}</span>@if (!last) {<input type="text" class="blank" spellcheck="false" autocomplete="off" [attr.aria-label]="i18n.t('exercise.blank', { n: '' + (i + 1) })" [value]="blanks()[i] ?? ''" (input)="setBlank(i, $any($event.target).value)" />}}</code></pre>
        }
        @case ('WRITE_CODE') {
          <label class="field-label" [for]="'code-' + exercise().id">{{ i18n.t('exercise.codeLabel') }}</label>
          <textarea
            class="code-editor"
            [id]="'code-' + exercise().id"
            rows="10"
            wrap="off"
            spellcheck="false"
            autocomplete="off"
            autocapitalize="off"
            [disabled]="!exercise().evaluable"
            [value]="code()"
            (input)="code.set($any($event.target).value)"
          ></textarea>
          @if (!exercise().evaluable) {
            <p class="hint" role="note">{{ i18n.t('exercise.writeSoon') }}</p>
          }
        }
      }

      @for (hint of hints(); track $index) {
        <p class="hint-text">
          <strong>{{ i18n.t('exercise.hintLabel', { n: '' + ($index + 1), total: '' + exercise().hintsTotal }) }}:</strong>
          <span [innerHTML]="inline(hint)"></span>
        </p>
      }

      @if (exercise().evaluable) {
        <div class="actions">
          <button type="button" [disabled]="busy() || !canCheck()" (click)="check()">
            @if (exercise().type === 'WRITE_CODE') {
              {{ busy() ? i18n.t('exercise.running') : i18n.t('exercise.run') }}
            } @else {
              {{ i18n.t('exercise.check') }}
            }
          </button>
          @if (!solved() && hintsLeft() > 0) {
            <button type="button" class="secondary" [disabled]="busy()" (click)="askHint()">
              {{ i18n.t('exercise.hint') }}
            </button>
          }
        </div>
      }

      <div class="feedback" role="status" aria-live="polite">
        @if (error()) {
          <p class="form-error">{{ error() }}</p>
        } @else if (feedback() === 'correct') {
          <p class="form-ok">{{ i18n.t('exercise.correct') }}</p>
        } @else if (feedback() === 'wrong') {
          <p class="form-error">{{ i18n.t('exercise.wrong') }}</p>
        }
        @if (result()?.xpEarned; as xp) {
          <p class="form-ok xp">{{ i18n.t('exercise.xp', { xp: '' + xp }) }}</p>
        }
        @for (code of result()?.newAchievements ?? []; track code) {
          <p class="form-ok">{{ i18n.t('exercise.newAchievement', { name: i18n.t('achievement.' + code + '.name') }) }}</p>
        }
        @for (need of unmet(); track need) {
          <p class="form-error">{{ needText(need) }}</p>
        }
        @if (result()?.error; as programError) {
          <p class="field-label">{{ i18n.t('exercise.programError') }}</p>
          <pre class="code error"><code>{{ programError }}</code></pre>
        }
        @if (explanation(); as text) {
          <div class="markdown explanation" [innerHTML]="html(text)"></div>
        }
      </div>

      @if (result()?.tests?.length) {
        <section [attr.aria-label]="i18n.t('exercise.tests')">
          <ul class="tests">
            @for (t of result()!.tests!; track t.number) {
              <li [class.pass]="t.passed" [class.fail]="!t.passed">
                <strong>{{ i18n.t('exercise.test', { n: '' + t.number }) }}:</strong>
                {{ t.passed ? '✓ ' + i18n.t('exercise.testPassed') : '✗ ' + i18n.t('exercise.testFailed') }}
                @if (t.timedOut) {
                  <span>({{ i18n.t('exercise.testTimedOut') }})</span>
                }
                @if (!t.passed && t.visible) {
                  @if (t.input) {
                    <p class="field-label">{{ i18n.t('exercise.input') }}</p>
                    <pre class="code"><code>{{ t.input }}</code></pre>
                  }
                  <p class="field-label">{{ i18n.t('exercise.expected') }}</p>
                  <pre class="code"><code>{{ t.expected }}</code></pre>
                  <p class="field-label">{{ i18n.t('exercise.actual') }}</p>
                  <pre class="code"><code>{{ t.actual }}</code></pre>
                } @else if (!t.passed) {
                  <span class="hint">{{ i18n.t('exercise.testHidden') }}</span>
                }
              </li>
            }
          </ul>
        </section>
      }
    </article>
  `,
})
export class ExerciseCard {
  readonly exercise = input.required<Exercise>();
  readonly index = input.required<number>();
  /** Avisa de que el ejercicio quedó resuelto, para que la lista actualice su contador. */
  readonly progress = output<{ id: string; solved: boolean }>();

  protected readonly i18n = inject(I18nService);
  private readonly learning = inject(LearningService);

  // Estado local. Se reinicia solo si el servidor envía otra versión del ejercicio (p. ej. al cambiar de idioma).
  protected readonly solved = linkedSignal(() => this.exercise().solved);
  protected readonly explanation = linkedSignal(() => this.exercise().explanation);
  protected readonly hints = linkedSignal(() => this.exercise().revealedHints);
  protected readonly hintsLeft = linkedSignal(() => this.exercise().hintsTotal - this.exercise().revealedHints.length);
  protected readonly choice = linkedSignal<number | null>(() => (this.exercise(), null));
  protected readonly text = linkedSignal(() => (this.exercise(), ''));
  protected readonly order = linkedSignal<string[]>(() => this.exercise().lines ?? []);
  protected readonly blanks = linkedSignal<string[]>(() => Array<string>(this.exercise().blankCount ?? 0).fill(''));
  protected readonly code = linkedSignal(() => this.exercise().starter ?? '');
  protected readonly result = linkedSignal<AttemptResult | null>(() => (this.exercise(), null));

  protected readonly feedback = signal<Feedback>('none');
  protected readonly error = signal('');
  protected readonly busy = signal(false);

  protected readonly promptHtml = computed(() => renderMarkdown(this.exercise().prompt));
  protected readonly parts = computed(() => (this.exercise().code ?? '').split('___'));
  protected readonly unmet = computed(() => this.result()?.unmetRequirements ?? []);
  protected readonly canCheck = computed(() => {
    switch (this.exercise().type) {
      case 'MULTIPLE_CHOICE':
        return this.choice() !== null;
      case 'PREDICT_OUTPUT':
        return this.text().trim().length > 0;
      case 'ORDER_LINES':
        return true;
      case 'FILL_CODE':
        return this.blanks().every((b) => b.trim().length > 0);
      case 'WRITE_CODE':
        return this.code().trim().length > 0;
      default:
        return false;
    }
  });

  protected inline(markdown: string): string {
    return renderInlineMarkdown(markdown);
  }

  protected html(markdown: string): string {
    return renderMarkdown(markdown);
  }

  /** Traduce un requisito del código (`assignment`, `function:nombre[:returns]`). */
  protected needText(need: string): string {
    const [kind, name, returns] = need.split(':');
    if (kind === 'function') {
      return this.i18n.t(returns ? 'exercise.needs.functionReturns' : 'exercise.needs.function', { name });
    }
    return this.i18n.t('exercise.needs.assignment');
  }

  protected setBlank(index: number, value: string): void {
    this.blanks.update((blanks) => blanks.map((b, i) => (i === index ? value : b)));
  }

  protected move(index: number, delta: number): void {
    this.order.update((lines) => {
      const target = index + delta;
      if (target < 0 || target >= lines.length) {
        return lines;
      }
      const copy = [...lines];
      [copy[index], copy[target]] = [copy[target], copy[index]];
      return copy;
    });
  }

  protected async check(): Promise<void> {
    const answer = this.buildAnswer();
    if (!answer) {
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.result.set(null);
    try {
      const result = await this.learning.attempt(this.exercise().id, answer);
      this.result.set(result);
      this.feedback.set(result.correct ? 'correct' : 'wrong');
      this.hintsLeft.set(result.hintsAvailable);
      if (result.solved) {
        this.solved.set(true);
        this.explanation.set(result.explanation);
        this.progress.emit({ id: this.exercise().id, solved: true });
      }
    } catch (e) {
      this.feedback.set('none');
      this.error.set(this.i18n.t(`error.${problemOf(e).code}`));
    } finally {
      this.busy.set(false);
    }
  }

  protected async askHint(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const hint = await this.learning.nextHint(this.exercise().id);
      this.hints.update((shown) => (shown.length >= hint.level ? shown : [...shown, hint.text]));
      this.hintsLeft.set(hint.total - hint.level);
    } catch (e) {
      this.error.set(this.i18n.t(`error.${problemOf(e).code}`));
    } finally {
      this.busy.set(false);
    }
  }

  private buildAnswer(): Answer | null {
    switch (this.exercise().type) {
      case 'MULTIPLE_CHOICE': {
        const choice = this.choice();
        return choice === null ? null : { choice };
      }
      case 'PREDICT_OUTPUT':
        return { text: this.text() };
      case 'ORDER_LINES':
        return { order: this.order() };
      case 'FILL_CODE':
        return { blanks: this.blanks() };
      case 'WRITE_CODE':
        return { code: this.code() };
      default:
        return null;
    }
  }
}
