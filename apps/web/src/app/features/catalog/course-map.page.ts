import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CatalogService } from '../../core/catalog/catalog.service';
import { CourseDetail } from '../../core/catalog/models';
import { I18nService } from '../../core/i18n/i18n.service';

type State = 'loading' | 'ready' | 'notFound' | 'error';

/** Mapa de aprendizaje: las unidades del curso con sus lecciones en orden. */
@Component({
  selector: 'app-course-map-page',
  imports: [RouterLink],
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
        @if (course(); as course) {
          <h2>{{ course.title }}</h2>
          <p>{{ course.description }}</p>

          @for (unit of course.units; track unit.id) {
            <section class="card" [attr.aria-labelledby]="'unit-' + unit.id">
              <h3 [id]="'unit-' + unit.id">{{ i18n.t('unit.heading', { n: '' + unit.position, title: unit.title }) }}</h3>
              <p class="hint">{{ unit.summary }}</p>
              <ol class="lesson-list">
                @for (lesson of unit.lessons; track lesson.id) {
                  <li>
                    <a [routerLink]="['/leccion', lesson.id]">{{ lesson.title }}</a>
                    @if (lesson.status === 'DRAFT') {
                      <span class="badge">{{ i18n.t('lesson.draft') }}</span>
                    }
                    @if (lesson.skills.length > 0) {
                      <span class="hint skills">{{ skillNames(lesson.skills) }}</span>
                    }
                  </li>
                }
              </ol>
            </section>
          }
        }
      }
    }
  `,
})
export class CourseMapPage {
  protected readonly i18n = inject(I18nService);
  private readonly catalog = inject(CatalogService);
  private readonly courseId = toSignal(inject(ActivatedRoute).paramMap, { requireSync: false });

  protected readonly state = signal<State>('loading');
  protected readonly course = signal<CourseDetail | null>(null);

  constructor() {
    // Carga el curso al abrir la página y cada vez que cambia el idioma o el curso de la dirección.
    effect(() => {
      this.i18n.locale();
      const id = this.courseId()?.get('courseId');
      if (id) {
        untracked(() => void this.load(id));
      }
    });
  }

  protected skillNames(skills: { name: string }[]): string {
    return skills.map((s) => s.name).join(' · ');
  }

  private async load(id: string): Promise<void> {
    try {
      this.course.set(await this.catalog.course(id));
      this.state.set('ready');
    } catch (e) {
      this.state.set(e instanceof HttpErrorResponse && e.status === 404 ? 'notFound' : 'error');
    }
  }
}
