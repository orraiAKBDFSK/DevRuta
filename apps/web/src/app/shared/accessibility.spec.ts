import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import axe from 'axe-core';
import { I18nService } from '../core/i18n/i18n.service';
import { Exercise, ProgressSummary } from '../core/learning/models';
import { ExerciseCard } from '../features/exercises/exercise-card.component';
import { ProgressPage } from '../features/progress/progress.page';

/**
 * Comprobación automática de accesibilidad con axe-core (nombres, etiquetas, roles, estructura). jsdom no calcula
 * colores, así que el contraste NO se comprueba aquí. Esto no sustituye probar con un lector de pantalla.
 */
const settle = () => new Promise<void>((resolve) => setTimeout(resolve));

function exercise(overrides: Partial<Exercise>): Exercise {
  return {
    id: 'py.u01.l01.e01',
    position: 1,
    type: 'MULTIPLE_CHOICE',
    difficulty: 2,
    prompt: 'Pregunta con `código`',
    evaluable: true,
    options: ['Uno', 'Dos'],
    code: null,
    lines: null,
    blankCount: null,
    starter: null,
    solved: false,
    attempts: 0,
    revealedHints: ['Una pista'],
    hintsTotal: 2,
    explanation: null,
    ...overrides,
  };
}

async function violations(element: HTMLElement): Promise<string[]> {
  const result = await axe.run(element, { rules: { 'color-contrast': { enabled: false } } });
  return result.violations.map((v) => `${v.id}: ${v.help} (${v.nodes.map((n) => n.target.join(' ')).join(' | ')})`);
}

describe('accessibility (axe-core)', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(I18nService).use('es');
  });

  const cases: [string, Partial<Exercise>][] = [
    ['multiple choice', {}],
    ['predict the output', { type: 'PREDICT_OUTPUT', options: null, code: 'print(1)' }],
    ['order the lines', { type: 'ORDER_LINES', options: null, lines: ['a = 1', 'print(a)'] }],
    ['fill in the code', { type: 'FILL_CODE', options: null, code: 'print(___, ___)', blankCount: 2 }],
    ['write code', { type: 'WRITE_CODE', options: null, starter: '# código' }],
    ['write code, engine unavailable', { type: 'WRITE_CODE', options: null, starter: '# código', evaluable: false }],
    ['solved exercise', { solved: true, explanation: 'Porque **sí**.' }],
  ];

  for (const [name, overrides] of cases) {
    it(`has no violations: ${name}`, async () => {
      const fixture = TestBed.createComponent(ExerciseCard);
      fixture.componentRef.setInput('exercise', exercise(overrides));
      fixture.componentRef.setInput('index', 1);
      fixture.detectChanges();
      await settle();

      expect(await violations(fixture.nativeElement as HTMLElement)).toEqual([]);
    });
  }

  it('has no violations: write code with test results', async () => {
    const fixture = TestBed.createComponent(ExerciseCard);
    fixture.componentRef.setInput('exercise', exercise({ type: 'WRITE_CODE', options: null, starter: '#' }));
    fixture.componentRef.setInput('index', 1);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    root.querySelector<HTMLTextAreaElement>('textarea')!.value = 'print(1)';
    root.querySelector('textarea')!.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.includes('Ejecutar'))!.click();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/exercises/py.u01.l01.e01/attempts')
      .flush({
        correct: false,
        solved: false,
        attempts: 1,
        hintsAvailable: 1,
        explanation: null,
        error: 'NameError: x',
        unmetRequirements: ['assignment'],
        tests: [
          { number: 1, visible: true, passed: false, timedOut: false, input: '', expected: '1', actual: '2' },
          { number: 2, visible: false, passed: false, timedOut: false, input: null, expected: null, actual: null },
          { number: 3, visible: false, passed: true, timedOut: false, input: null, expected: null, actual: null },
        ],
      });
    await settle();
    fixture.detectChanges();

    expect(await violations(root)).toEqual([]);
  });

  it('really detects problems (control: an input without a label is reported)', async () => {
    const host = document.createElement('div');
    host.innerHTML = '<input type="text"><button></button>';
    document.body.appendChild(host);

    const found = await violations(host);
    host.remove();

    expect(found.some((v) => v.startsWith('label'))).toBe(true);
    expect(found.some((v) => v.startsWith('button-name'))).toBe(true);
  });

  it('has no violations: progress page', async () => {
    const fixture = TestBed.createComponent(ProgressPage);
    fixture.detectChanges();
    const summary: ProgressSummary = {
      xp: 120,
      level: 2,
      levelStartXp: 50,
      nextLevelXp: 200,
      streak: 3,
      longestStreak: 5,
      activeToday: true,
      solvedExercises: 7,
      completedLessons: [],
      achievements: [{ code: 'first_solve', unlockedAt: '2026-10-23T10:00:00Z' }],
    };
    TestBed.inject(HttpTestingController).expectOne('/api/v1/progress').flush(summary);
    await settle();
    fixture.detectChanges();

    expect(await violations(fixture.nativeElement as HTMLElement)).toEqual([]);
  });
});
