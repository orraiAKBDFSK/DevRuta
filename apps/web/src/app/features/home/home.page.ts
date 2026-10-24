import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogService } from '../../core/catalog/catalog.service';
import { CourseSummary } from '../../core/catalog/models';
import { I18nService } from '../../core/i18n/i18n.service';
import { ProfileService } from '../../core/profile/profile.service';

type State = 'loading' | 'ready' | 'error';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>{{ i18n.t('home.title', { name: profiles.profile()?.displayName ?? '' }) }}</h2>

    @switch (state()) {
      @case ('loading') {
        <p role="status">{{ i18n.t('common.loading') }}</p>
      }
      @case ('error') {
        <p role="alert">{{ i18n.t('error.UNKNOWN') }}</p>
      }
      @case ('ready') {
        @if (courses().length === 0) {
          <p>{{ i18n.t('home.noCourses') }}</p>
        }
        @for (course of courses(); track course.id) {
          <section class="card" [attr.aria-labelledby]="'course-' + course.id">
            <h3 [id]="'course-' + course.id">{{ course.title }}</h3>
            <p>{{ course.description }}</p>
            <p class="hint">
              {{ i18n.t('course.counts', { units: '' + course.unitCount, lessons: '' + course.lessonCount }) }}
            </p>
            <a class="button" [routerLink]="['/ruta', course.id]">{{ i18n.t('course.open') }}</a>
          </section>
        }
      }
    }
  `,
})
export class HomePage {
  protected readonly i18n = inject(I18nService);
  protected readonly profiles = inject(ProfileService);
  private readonly catalog = inject(CatalogService);

  protected readonly state = signal<State>('loading');
  protected readonly courses = signal<CourseSummary[]>([]);

  constructor() {
    // Vuelve a pedir los cursos cuando cambia el idioma de la interfaz.
    effect(() => {
      this.i18n.locale();
      untracked(() => void this.load());
    });
  }

  private async load(): Promise<void> {
    try {
      this.courses.set(await this.catalog.courses());
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }
}
