import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { I18nService } from '../../core/i18n/i18n.service';
import { Exercise } from '../../core/learning/models';
import { ExerciseList } from './exercise-list.component';

const settle = () => new Promise<void>((resolve) => setTimeout(resolve));

function exercise(id: string, overrides: Partial<Exercise> = {}): Exercise {
  return {
    id,
    position: 1,
    type: 'MULTIPLE_CHOICE',
    difficulty: 1,
    prompt: 'Pregunta',
    evaluable: true,
    options: ['A', 'B'],
    code: null,
    lines: null,
    blankCount: null,
    starter: null,
    solved: false,
    attempts: 0,
    revealedHints: [],
    hintsTotal: 1,
    explanation: null,
    ...overrides,
  };
}

describe('ExerciseList', () => {
  let http: HttpTestingController;
  let i18n: I18nService;

  const open = (lessonId = 'py.u01.l01') => {
    const fixture = TestBed.createComponent(ExerciseList);
    fixture.componentRef.setInput('lessonId', lessonId);
    fixture.detectChanges();
    return fixture;
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    i18n = TestBed.inject(I18nService);
    i18n.use('es');
    http = TestBed.inject(HttpTestingController);
  });

  it('shows the exercises and counts only the ones that can be solved', async () => {
    const fixture = open();

    http.expectOne('/api/v1/lessons/py.u01.l01/exercises').flush([
      exercise('e1'),
      exercise('e2', { solved: true, explanation: 'Ya' }),
      exercise('e3', { type: 'WRITE_CODE', evaluable: false, options: null, starter: '#' }),
    ]);
    await settle();
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelectorAll('app-exercise-card')).toHaveLength(3);
    expect(root.textContent).toContain('1 de 2 resueltos');
  });

  it('updates the counter when an exercise is solved', async () => {
    const fixture = open();
    http.expectOne('/api/v1/lessons/py.u01.l01/exercises').flush([exercise('e1'), exercise('e2')]);
    await settle();
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('0 de 2 resueltos');

    root.querySelector<HTMLInputElement>('app-exercise-card input[type=radio]')!.click();
    fixture.detectChanges();
    Array.from(root.querySelectorAll('app-exercise-card button'))
      .find((b) => b.textContent?.trim() === 'Comprobar')!
      .dispatchEvent(new Event('click'));
    http
      .expectOne('/api/v1/exercises/e1/attempts')
      .flush({ correct: true, solved: true, attempts: 1, hintsAvailable: 1, explanation: 'Bien' });
    await settle();
    fixture.detectChanges();

    expect(root.textContent).toContain('1 de 2 resueltos');
  });

  it('says so when the lesson has no exercises', async () => {
    const fixture = open();

    http.expectOne('/api/v1/lessons/py.u01.l01/exercises').flush([]);
    await settle();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Esta lección todavía no tiene ejercicios.');
  });

  it('reloads the exercises when the language changes', async () => {
    const fixture = open();
    http.expectOne('/api/v1/lessons/py.u01.l01/exercises').flush([exercise('e1')]);
    await settle();

    i18n.use('en');
    fixture.detectChanges();
    TestBed.tick();

    http.expectOne('/api/v1/lessons/py.u01.l01/exercises').flush([exercise('e1', { prompt: 'Question' })]);
    await settle();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Question');
  });

  it('shows an error if the exercises cannot be loaded', async () => {
    const fixture = open();

    http.expectOne('/api/v1/lessons/py.u01.l01/exercises').flush({}, { status: 500, statusText: 'Server Error' });
    await settle();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')).not.toBeNull();
  });
});
