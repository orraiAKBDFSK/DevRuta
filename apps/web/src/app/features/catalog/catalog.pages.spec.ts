import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { CourseDetail, CourseSummary, LessonDetail } from '../../core/catalog/models';
import { I18nService } from '../../core/i18n/i18n.service';
import { HomePage } from '../home/home.page';
import { CourseMapPage } from './course-map.page';
import { LessonPage } from './lesson.page';

const settle = () => new Promise<void>((resolve) => setTimeout(resolve));

const COURSES: CourseSummary[] = [
  {
    id: 'py.course',
    languageCode: 'python',
    languageName: 'Python',
    title: 'Python desde cero',
    description: 'Aprende a programar.',
    unitCount: 2,
    lessonCount: 3,
  },
];

const COURSE: CourseDetail = {
  id: 'py.course',
  languageCode: 'python',
  languageName: 'Python',
  title: 'Python desde cero',
  description: 'Aprende a programar.',
  units: [
    {
      id: 'py.u01',
      position: 1,
      title: 'Primeros pasos',
      summary: 'Mostrar texto.',
      lessons: [
        {
          id: 'py.u01.l01',
          position: 1,
          title: 'Mostrar texto',
          status: 'DRAFT',
          skills: [{ id: 'py.output', name: 'Mostrar resultados' }],
        },
        { id: 'py.u01.l02', position: 2, title: 'Variables', status: 'PUBLISHED', skills: [] },
      ],
    },
  ],
};

function lesson(overrides: Partial<LessonDetail> = {}): LessonDetail {
  return {
    id: 'py.u01.l01',
    position: 1,
    title: 'Mostrar texto',
    bodyMarkdown: '## Hola\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n```python\nprint("hola")\n```',
    status: 'DRAFT',
    courseId: 'py.course',
    courseTitle: 'Python desde cero',
    unitId: 'py.u01',
    unitTitle: 'Primeros pasos',
    skills: [{ id: 'py.output', name: 'Mostrar resultados' }],
    previous: null,
    next: { id: 'py.u01.l02', title: 'Variables' },
    ...overrides,
  };
}

function setup(params: Record<string, string>) {
  localStorage.clear();
  const route = new BehaviorSubject(convertToParamMap(params));
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { paramMap: route.asObservable() } },
    ],
  });
  const i18n = TestBed.inject(I18nService);
  i18n.use('es');
  return { http: TestBed.inject(HttpTestingController), i18n, route };
}

describe('HomePage', () => {
  it('lists the courses with their counts and a link to the learning path', async () => {
    const { http } = setup({});
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();

    http.expectOne('/api/v1/catalog/courses').flush(COURSES);
    await settle();
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Python desde cero');
    expect(root.textContent).toContain('2 unidades · 3 lecciones');
    expect(root.querySelector('a.button')?.getAttribute('href')).toBe('/ruta/py.course');
  });

  it('asks again for the courses when the interface language changes', async () => {
    const { http, i18n } = setup({});
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    http.expectOne('/api/v1/catalog/courses').flush(COURSES);
    await settle();

    i18n.use('en');
    fixture.detectChanges();
    TestBed.tick();

    http.expectOne('/api/v1/catalog/courses').flush([{ ...COURSES[0], title: 'Python from scratch' }]);
    await settle();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Python from scratch');
  });

  it('says so when there are no courses', async () => {
    const { http } = setup({});
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();

    http.expectOne('/api/v1/catalog/courses').flush([]);
    await settle();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Todavía no hay cursos disponibles.');
  });
});

describe('CourseMapPage', () => {
  it('shows units and lessons in order with skills and the draft badge', async () => {
    const { http } = setup({ courseId: 'py.course' });
    const fixture = TestBed.createComponent(CourseMapPage);
    fixture.detectChanges();

    http.expectOne('/api/v1/catalog/courses/py.course').flush(COURSE);
    await settle();
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('h2')?.textContent).toContain('Python desde cero');
    expect(root.querySelector('h3')?.textContent).toContain('Unidad 1 · Primeros pasos');
    const links = Array.from(root.querySelectorAll<HTMLAnchorElement>('.lesson-list a')).map((a) => a.getAttribute('href'));
    expect(links).toEqual(['/leccion/py.u01.l01', '/leccion/py.u01.l02']);
    expect(root.querySelectorAll('.badge')).toHaveLength(1);
    expect(root.textContent).toContain('Mostrar resultados');
  });

  it('shows a not-found message for an unknown course', async () => {
    const { http } = setup({ courseId: 'no.existe' });
    const fixture = TestBed.createComponent(CourseMapPage);
    fixture.detectChanges();

    http.expectOne('/api/v1/catalog/courses/no.existe').flush({ code: 'NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await settle();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No se encontró lo que buscabas.');
  });
});

describe('LessonPage', () => {
  it('renders the Markdown body with tables and code blocks and links to the next lesson', async () => {
    const { http } = setup({ lessonId: 'py.u01.l01' });
    const fixture = TestBed.createComponent(LessonPage);
    fixture.detectChanges();

    http.expectOne('/api/v1/catalog/lessons/py.u01.l01').flush(lesson());
    await settle();
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const body = root.querySelector('.markdown')!;
    expect(body.querySelector('h2')?.textContent).toBe('Hola');
    expect(body.querySelector('table td')?.textContent).toBe('1');
    expect(body.querySelector('pre code')?.textContent).toContain('print("hola")');
    expect(root.querySelector('a[rel=next]')?.getAttribute('href')).toBe('/leccion/py.u01.l02');
    expect(root.querySelector('a[rel=prev]')).toBeNull();
    expect(root.querySelector('nav.breadcrumb a')?.getAttribute('href')).toBe('/ruta/py.course');
    expect(root.querySelector('.badge')?.textContent).toContain('Borrador');
  });

  it('sanitizes dangerous HTML that comes inside the Markdown', async () => {
    const { http } = setup({ lessonId: 'py.u01.l01' });
    const fixture = TestBed.createComponent(LessonPage);
    fixture.detectChanges();

    http.expectOne('/api/v1/catalog/lessons/py.u01.l01').flush(
      lesson({
        bodyMarkdown:
          'Texto\n\n<script>window.hacked = true</script>\n\n<img src="x" onerror="window.hacked = true">\n\n[enlace](javascript:alert(1))',
      }),
    );
    await settle();
    fixture.detectChanges();

    const body = (fixture.nativeElement as HTMLElement).querySelector('.markdown')!;
    expect(body.querySelector('script')).toBeNull();
    expect(body.querySelector('img')?.getAttribute('onerror') ?? null).toBeNull();
    // El saneador de Angular neutraliza el esquema peligroso anteponiéndole "unsafe:": el navegador no lo ejecuta.
    expect(body.querySelector('a')?.getAttribute('href')).toMatch(/^unsafe:/);
    expect((window as unknown as { hacked?: boolean }).hacked).toBeUndefined();
  });

  it('loads the new lesson when the address changes to the next one', async () => {
    const { http, route } = setup({ lessonId: 'py.u01.l01' });
    const fixture = TestBed.createComponent(LessonPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/catalog/lessons/py.u01.l01').flush(lesson());
    await settle();

    route.next(convertToParamMap({ lessonId: 'py.u01.l02' }));
    fixture.detectChanges();
    TestBed.tick();

    http
      .expectOne('/api/v1/catalog/lessons/py.u01.l02')
      .flush(lesson({ id: 'py.u01.l02', title: 'Variables', previous: { id: 'py.u01.l01', title: 'Mostrar texto' }, next: null }));
    await settle();
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('h2')?.textContent).toContain('Variables');
    expect(root.querySelector('a[rel=prev]')?.getAttribute('href')).toBe('/leccion/py.u01.l01');
    expect(root.querySelector('a[rel=next]')).toBeNull();
  });

  it('shows a not-found message for an unknown lesson', async () => {
    const { http } = setup({ lessonId: 'no.existe' });
    const fixture = TestBed.createComponent(LessonPage);
    fixture.detectChanges();

    http.expectOne('/api/v1/catalog/lessons/no.existe').flush({ code: 'NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await settle();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No se encontró lo que buscabas.');
  });
});
