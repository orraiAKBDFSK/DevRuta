import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CatalogService } from '../../core/catalog/catalog.service';
import { LessonDetail } from '../../core/catalog/models';
import { I18nService } from '../../core/i18n/i18n.service';
import { renderMarkdown } from '../../shared/markdown';
import { ExerciseList } from '../exercises/exercise-list.component';

type State = 'loading' | 'ready' | 'notFound' | 'error';

/** Una lección: texto en Markdown, habilidades y enlaces a la anterior y la siguiente. */
@Component({
  selector: 'app-lesson-page',
  imports: [RouterLink, ExerciseList],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @switch (state()) {
      @case ('loading') {
        <p role="status">{{ i18n.t('common.loading') }}</p>
      }
      @case ('notFound') {
        <p role="alert">{{ i18n.t('catalog.notFound') }}</p>
        <p>
          <a routerLink="/inicio">{{ i18n.t('nav.home') }}</a>
        </p>
      }
      @case ('error') {
        <p role="alert">{{ i18n.t('error.UNKNOWN') }}</p>
      }
      @case ('ready') {
        @if (lesson(); as lesson) {
          <nav class="breadcrumb" [attr.aria-label]="i18n.t('lesson.breadcrumb')">
            <a [routerLink]="['/ruta', lesson.courseId]">{{ lesson.courseTitle }}</a>
            <span aria-hidden="true"> › </span>
            <span>{{ lesson.unitTitle }}</span>
          </nav>

          <article class="card">
            <h2>
              {{ lesson.title }}
              @if (lesson.status === 'DRAFT') {
                <span class="badge">{{ i18n.t('lesson.draft') }}</span>
              }
            </h2>
            <!-- El HTML generado desde Markdown se sanea al asignarse a innerHTML. -->
            <div class="markdown" [innerHTML]="html()"></div>

            @if (lesson.skills.length > 0) {
              <p class="hint">
                {{ i18n.t('lesson.skills') }}:
                @for (skill of lesson.skills; track skill.id) {
                  <span class="chip">{{ skill.name }}</span>
                }
              </p>
            }
          </article>

          <app-exercise-list [lessonId]="lesson.id" />

          <nav class="pager" [attr.aria-label]="i18n.t('lesson.pager')">
            @if (lesson.previous; as previous) {
              <a [routerLink]="['/leccion', previous.id]" rel="prev">← {{ previous.title }}</a>
            } @else {
              <span></span>
            }
            @if (lesson.next; as next) {
              <a [routerLink]="['/leccion', next.id]" rel="next">{{ next.title }} →</a>
            }
          </nav>
        }
      }
    }
  `,
})
export class LessonPage {
  protected readonly i18n = inject(I18nService);
  private readonly catalog = inject(CatalogService);
  private readonly lessonId = toSignal(inject(ActivatedRoute).paramMap, { requireSync: false });

  protected readonly state = signal<State>('loading');
  protected readonly lesson = signal<LessonDetail | null>(null);
  protected readonly html = computed(() => renderMarkdown(this.lesson()?.bodyMarkdown ?? ''));

  constructor() {
    // Se reutiliza el mismo componente al pasar a la lección anterior o siguiente: reacciona al cambio de la dirección.
    effect(() => {
      this.i18n.locale();
      const id = this.lessonId()?.get('lessonId');
      if (id) {
        untracked(() => void this.load(id));
      }
    });
  }

  private async load(id: string): Promise<void> {
    try {
      this.lesson.set(await this.catalog.lesson(id));
      this.state.set('ready');
      window.scrollTo?.({ top: 0 });
    } catch (e) {
      this.state.set(e instanceof HttpErrorResponse && e.status === 404 ? 'notFound' : 'error');
    }
  }
}
