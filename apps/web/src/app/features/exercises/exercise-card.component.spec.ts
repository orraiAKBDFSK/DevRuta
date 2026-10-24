import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { I18nService } from '../../core/i18n/i18n.service';
import { Exercise } from '../../core/learning/models';
import { ExerciseCard } from './exercise-card.component';

const settle = () => new Promise<void>((resolve) => setTimeout(resolve));

function exercise(overrides: Partial<Exercise> = {}): Exercise {
  return {
    id: 'py.u01.l01.e01',
    position: 1,
    type: 'MULTIPLE_CHOICE',
    difficulty: 2,
    prompt: '¿Cuánto es **2 + 2**?',
    evaluable: true,
    options: ['Tres', 'Cuatro', 'Cinco'],
    code: null,
    lines: null,
    blankCount: null,
    starter: null,
    solved: false,
    attempts: 0,
    revealedHints: [],
    hintsTotal: 2,
    explanation: null,
    ...overrides,
  };
}

describe('ExerciseCard', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ExerciseCard>;
  let root: HTMLElement;
  let solvedEvents: { id: string; solved: boolean }[];

  const open = (e: Exercise) => {
    fixture = TestBed.createComponent(ExerciseCard);
    fixture.componentRef.setInput('exercise', e);
    fixture.componentRef.setInput('index', 1);
    solvedEvents = [];
    fixture.componentInstance.progress.subscribe((event) => solvedEvents.push(event));
    fixture.detectChanges();
    root = fixture.nativeElement as HTMLElement;
  };

  const refresh = async () => {
    await settle();
    fixture.detectChanges();
  };

  const checkButton = () => Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Comprobar')!;
  const hintButton = () => Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.includes('Pedir una pista'));

  const result = (overrides: object = {}) => ({
    correct: false,
    solved: false,
    attempts: 1,
    hintsAvailable: 2,
    explanation: null,
    ...overrides,
  });

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(I18nService).use('es');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('multiple choice', () => {
    it('renders the prompt as Markdown and the options as radio buttons, and needs a choice to check', () => {
      open(exercise());

      expect(root.querySelector('.markdown strong')?.textContent).toBe('2 + 2');
      expect(root.querySelectorAll('input[type=radio]')).toHaveLength(3);
      expect(checkButton().disabled).toBe(true);
    });

    it('sends the chosen option and shows the explanation when the answer is right', async () => {
      open(exercise());
      root.querySelectorAll<HTMLInputElement>('input[type=radio]')[1].click();
      fixture.detectChanges();
      expect(checkButton().disabled).toBe(false);

      checkButton().click();
      const request = http.expectOne('/api/v1/exercises/py.u01.l01.e01/attempts');
      expect(request.request.body).toEqual({ answer: { choice: 1 } });
      request.flush(result({ correct: true, solved: true, explanation: 'Porque **cuatro**.' }));
      await refresh();

      expect(root.textContent).toContain('¡Correcto!');
      expect(root.querySelector('.explanation strong')?.textContent).toBe('cuatro');
      expect(root.querySelector('.badge.ok')?.textContent).toContain('Resuelto');
      expect(solvedEvents).toEqual([{ id: 'py.u01.l01.e01', solved: true }]);
    });

    it('shows the XP earned and any achievement unlocked by the answer', async () => {
      open(exercise());
      root.querySelectorAll<HTMLInputElement>('input[type=radio]')[1].click();
      fixture.detectChanges();

      checkButton().click();
      http
        .expectOne('/api/v1/exercises/py.u01.l01.e01/attempts')
        .flush(result({ correct: true, solved: true, explanation: 'Ok', xpEarned: 20, newAchievements: ['first_solve'] }));
      await refresh();

      expect(root.querySelector('.xp')?.textContent).toContain('+20 XP');
      expect(root.textContent).toContain('Logro desbloqueado: Primer paso');
    });

    it('shows no XP when the exercise had already given it', async () => {
      open(exercise());
      root.querySelectorAll<HTMLInputElement>('input[type=radio]')[1].click();
      fixture.detectChanges();

      checkButton().click();
      http
        .expectOne('/api/v1/exercises/py.u01.l01.e01/attempts')
        .flush(result({ correct: true, solved: true, explanation: 'Ok', xpEarned: null, newAchievements: null }));
      await refresh();

      expect(root.querySelector('.xp')).toBeNull();
    });

    it('says "not yet" and hides the explanation when the answer is wrong', async () => {
      open(exercise());
      root.querySelectorAll<HTMLInputElement>('input[type=radio]')[0].click();
      fixture.detectChanges();

      checkButton().click();
      http.expectOne('/api/v1/exercises/py.u01.l01.e01/attempts').flush(result());
      await refresh();

      expect(root.textContent).toContain('Todavía no.');
      expect(root.querySelector('.explanation')).toBeNull();
      expect(solvedEvents).toHaveLength(0);
    });
  });

  describe('hints', () => {
    it('reveals hints one at a time and hides the button when none are left', async () => {
      open(exercise());
      expect(hintButton()).toBeDefined();

      hintButton()!.click();
      http.expectOne('/api/v1/exercises/py.u01.l01.e01/hints').flush({ level: 1, total: 2, text: 'Cuenta con `los dedos`', last: false });
      await refresh();
      expect(root.querySelector('.hint-text')?.textContent).toContain('Pista 1 de 2');
      expect(root.querySelector('.hint-text code')?.textContent).toBe('los dedos');
      expect(hintButton()).toBeDefined();

      hintButton()!.click();
      http.expectOne('/api/v1/exercises/py.u01.l01.e01/hints').flush({ level: 2, total: 2, text: 'Es par', last: true });
      await refresh();
      expect(root.querySelectorAll('.hint-text')).toHaveLength(2);
      expect(hintButton()).toBeUndefined();
    });

    it('shows the hints already revealed in a previous visit', () => {
      open(exercise({ revealedHints: ['Primera pista'], hintsTotal: 2 }));

      expect(root.querySelector('.hint-text')?.textContent).toContain('Primera pista');
      expect(hintButton()).toBeDefined();
    });
  });

  describe('predict the output', () => {
    it('sends the typed text and needs some text first', async () => {
      open(exercise({ type: 'PREDICT_OUTPUT', options: null, code: 'print("hola")' }));
      expect(root.querySelector('pre code')?.textContent).toBe('print("hola")');
      expect(checkButton().disabled).toBe(true);

      const textarea = root.querySelector('textarea')!;
      textarea.value = 'hola';
      textarea.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      checkButton().click();

      const request = http.expectOne('/api/v1/exercises/py.u01.l01.e01/attempts');
      expect(request.request.body).toEqual({ answer: { text: 'hola' } });
      request.flush(result({ correct: true, solved: true, explanation: 'Sí.' }));
      await refresh();
      expect(root.textContent).toContain('¡Correcto!');
    });
  });

  describe('order the lines', () => {
    const lines = ['x = 1', 'y = 2', 'print(x + y)'];

    it('moves lines with the up and down buttons and sends the resulting order', async () => {
      open(exercise({ type: 'ORDER_LINES', options: null, lines }));
      const buttons = () => Array.from(root.querySelectorAll<HTMLButtonElement>('.order-buttons button'));
      expect(buttons()[0].disabled).toBe(true);
      expect(buttons()[5].disabled).toBe(true);

      buttons()[1].click();
      fixture.detectChanges();
      expect(Array.from(root.querySelectorAll('.code-line')).map((c) => c.textContent)).toEqual([
        'y = 2',
        'x = 1',
        'print(x + y)',
      ]);

      checkButton().click();
      const request = http.expectOne('/api/v1/exercises/py.u01.l01.e01/attempts');
      expect(request.request.body).toEqual({ answer: { order: ['y = 2', 'x = 1', 'print(x + y)'] } });
      request.flush(result());
      await refresh();
    });

    it('gives each move button an accessible name with the line number', () => {
      open(exercise({ type: 'ORDER_LINES', options: null, lines }));

      const names = Array.from(root.querySelectorAll('.order-buttons button')).map((b) => b.getAttribute('aria-label'));
      expect(names.slice(0, 2)).toEqual(['Subir la línea 1', 'Bajar la línea 1']);
    });
  });

  describe('fill in the code', () => {
    it('shows one labelled input per blank and sends them in order', async () => {
      open(exercise({ type: 'FILL_CODE', options: null, code: 'print(___, ___)', blankCount: 2 }));
      const inputs = Array.from(root.querySelectorAll<HTMLInputElement>('input.blank'));
      expect(inputs.map((i) => i.getAttribute('aria-label'))).toEqual(['Hueco 1', 'Hueco 2']);
      expect(root.querySelector('pre')?.textContent).toBe('print(, )');
      expect(checkButton().disabled).toBe(true);

      inputs[0].value = '1';
      inputs[0].dispatchEvent(new Event('input'));
      fixture.detectChanges();
      expect(checkButton().disabled).toBe(true);
      inputs[1].value = '2';
      inputs[1].dispatchEvent(new Event('input'));
      fixture.detectChanges();
      expect(checkButton().disabled).toBe(false);

      checkButton().click();
      const request = http.expectOne('/api/v1/exercises/py.u01.l01.e01/attempts');
      expect(request.request.body).toEqual({ answer: { blanks: ['1', '2'] } });
      request.flush(result({ correct: true, solved: true, explanation: 'Ok' }));
      await refresh();
      expect(solvedEvents).toHaveLength(1);
    });
  });

  describe('write code', () => {
    const writable = (overrides: Partial<Exercise> = {}) =>
      exercise({ type: 'WRITE_CODE', options: null, starter: '# Escribe tu código aquí', ...overrides });
    const editor = () => root.querySelector<HTMLTextAreaElement>('textarea.code-editor')!;
    const type = (code: string) => {
      editor().value = code;
      editor().dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };

    it('starts with the starter code in an editor and sends what the student wrote', async () => {
      open(writable());
      expect(editor().value).toBe('# Escribe tu código aquí');
      expect(editor().disabled).toBe(false);

      type('print(3)');
      Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Ejecutar y comprobar')!.click();
      const request = http.expectOne('/api/v1/exercises/py.u01.l01.e01/attempts');
      expect(request.request.body).toEqual({ answer: { code: 'print(3)' } });
      request.flush(
        result({
          correct: true,
          solved: true,
          explanation: 'Basta un print.',
          tests: [{ number: 1, visible: true, passed: true, timedOut: false, input: '', expected: '3\n', actual: '3\n' }],
        }),
      );
      await refresh();

      expect(root.textContent).toContain('¡Correcto!');
      expect(root.textContent).toContain('Prueba 1: ✓ superada');
      expect(solvedEvents).toHaveLength(1);
    });

    it('cannot be checked while the editor is empty', () => {
      open(writable());
      type('   ');

      expect(Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.includes('Ejecutar'))!.disabled).toBe(true);
    });

    it('shows input, expected and actual output of a failed visible test, and only a note for hidden ones', async () => {
      open(writable());
      type('print(4)');
      Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.includes('Ejecutar'))!.click();
      http.expectOne('/api/v1/exercises/py.u01.l01.e01/attempts').flush(
        result({
          tests: [
            { number: 1, visible: true, passed: false, timedOut: false, input: '2\n', expected: '3\n', actual: '4\n' },
            { number: 2, visible: false, passed: false, timedOut: false, input: null, expected: null, actual: null },
            { number: 3, visible: false, passed: true, timedOut: false, input: null, expected: null, actual: null },
          ],
        }),
      );
      await refresh();

      const items = Array.from(root.querySelectorAll('.tests li'));
      expect(items).toHaveLength(3);
      expect(items[0].textContent).toContain('Salida esperada');
      expect(items[0].querySelectorAll('pre code')[2].textContent).toBe('4\n');
      expect(items[1].textContent).toContain('prueba oculta');
      expect(items[1].querySelector('pre')).toBeNull();
      expect(items[2].textContent).toContain('✓ superada');
      expect(items[0].classList.contains('fail')).toBe(true);
      expect(root.textContent).toContain('Todavía no.');
    });

    it('shows the error of the student program and says when a test timed out', async () => {
      open(writable());
      type('while True: pass');
      Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.includes('Ejecutar'))!.click();
      http.expectOne('/api/v1/exercises/py.u01.l01.e01/attempts').flush(
        result({
          error: "NameError: name 'x' is not defined",
          tests: [{ number: 1, visible: true, passed: false, timedOut: true, input: '', expected: '1', actual: '' }],
        }),
      );
      await refresh();

      expect(root.querySelector('pre.error')?.textContent).toBe("NameError: name 'x' is not defined");
      expect(root.textContent).toContain('tu programa tardó demasiado');
    });

    it('explains each unmet requirement in plain words', async () => {
      open(writable());
      type('print(1)');
      Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.includes('Ejecutar'))!.click();
      http
        .expectOne('/api/v1/exercises/py.u01.l01.e01/attempts')
        .flush(result({ unmetRequirements: ['function:es_par:returns', 'function:otra', 'assignment'] }));
      await refresh();

      const text = root.textContent ?? '';
      expect(text).toContain('Tu código debe definir la función es_par y devolver un valor con return.');
      expect(text).toContain('Tu código debe definir la función otra.');
      expect(text).toContain('Tu código debe guardar algún valor en una variable.');
    });

    it('shows a retry message when the runner is busy or down', async () => {
      open(writable());
      type('print(1)');
      const run = () => Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.includes('Ejecutar'))!.click();

      run();
      http
        .expectOne('/api/v1/exercises/py.u01.l01.e01/attempts')
        .flush({ code: 'RUNNER_BUSY' }, { status: 503, statusText: 'Service Unavailable' });
      await refresh();
      expect(root.querySelector('.form-error')?.textContent).toContain('ocupado');

      run();
      http
        .expectOne('/api/v1/exercises/py.u01.l01.e01/attempts')
        .flush({ code: 'RUNNER_UNAVAILABLE' }, { status: 503, statusText: 'Service Unavailable' });
      await refresh();
      expect(root.querySelector('.form-error')?.textContent).toContain('este intento no cuenta');
    });

    it('is read-only with an explanation when the engine is not available', () => {
      open(writable({ evaluable: false }));

      expect(editor().disabled).toBe(true);
      expect(root.textContent).toContain('motor de ejecución');
      expect(Array.from(root.querySelectorAll('button')).some((b) => b.textContent?.includes('Ejecutar'))).toBe(false);
    });
  });

  describe('state and errors', () => {
    it('opens an already solved exercise with its badge and explanation', () => {
      open(exercise({ solved: true, attempts: 3, explanation: 'Ya lo resolviste.' }));

      expect(root.querySelector('.badge.ok')).not.toBeNull();
      expect(root.querySelector('.explanation')?.textContent).toContain('Ya lo resolviste.');
    });

    it('shows a translated message when the server rejects the answer', async () => {
      open(exercise());
      root.querySelectorAll<HTMLInputElement>('input[type=radio]')[0].click();
      fixture.detectChanges();

      checkButton().click();
      http
        .expectOne('/api/v1/exercises/py.u01.l01.e01/attempts')
        .flush({ code: 'INVALID_ANSWER' }, { status: 400, statusText: 'Bad Request' });
      await refresh();

      expect(root.querySelector('.form-error')?.textContent).toContain('La respuesta no tiene el formato esperado.');
    });

    it('tells the user to wait when they are rate limited', async () => {
      open(exercise());
      root.querySelectorAll<HTMLInputElement>('input[type=radio]')[0].click();
      fixture.detectChanges();

      checkButton().click();
      http
        .expectOne('/api/v1/exercises/py.u01.l01.e01/attempts')
        .flush({ code: 'TOO_MANY_ATTEMPTS' }, { status: 429, statusText: 'Too Many Requests' });
      await refresh();

      expect(root.querySelector('.form-error')?.textContent).toContain('Demasiados intentos');
    });

    it('does not run HTML or scripts that come inside a prompt or an explanation', () => {
      open(exercise({ prompt: 'Hola <script>window.pwned = 1</script><img src=x onerror="window.pwned = 1">' }));

      expect(root.querySelector('script')).toBeNull();
      expect(root.querySelector('img')?.getAttribute('onerror') ?? null).toBeNull();
      expect((window as unknown as { pwned?: number }).pwned).toBeUndefined();
    });
  });
});
