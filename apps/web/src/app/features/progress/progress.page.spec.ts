import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { I18nService } from '../../core/i18n/i18n.service';
import { ProgressSummary } from '../../core/learning/models';
import { ProgressPage } from './progress.page';

const settle = () => new Promise<void>((resolve) => setTimeout(resolve));

function summary(overrides: Partial<ProgressSummary> = {}): ProgressSummary {
  return {
    xp: 120,
    level: 2,
    levelStartXp: 50,
    nextLevelXp: 200,
    streak: 3,
    longestStreak: 5,
    activeToday: true,
    solvedExercises: 7,
    completedLessons: ['py.u01.l01'],
    achievements: [{ code: 'first_solve', unlockedAt: '2026-10-23T10:00:00Z' }],
    ...overrides,
  };
}

describe('ProgressPage', () => {
  let http: HttpTestingController;

  const open = async (locale: 'es' | 'en' = 'es') => {
    TestBed.inject(I18nService).use(locale);
    const fixture = TestBed.createComponent(ProgressPage);
    fixture.detectChanges();
    return fixture;
  };

  const flush = async (fixture: Awaited<ReturnType<typeof open>>, body: ProgressSummary) => {
    http.expectOne('/api/v1/progress').flush(body);
    await settle();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  it('shows level, XP with an accessible bar, streak and totals', async () => {
    const root = await flush(await open(), summary());

    expect(root.textContent).toContain('Nivel 2');
    expect(root.textContent).toContain('120 XP en total');
    expect(root.textContent).toContain('Te faltan 80 XP para el nivel 3.');
    const bar = root.querySelector('progress')!;
    expect(bar.getAttribute('aria-label')).toBe('Progreso hacia el siguiente nivel');
    expect(bar.value).toBe(70);
    expect(bar.max).toBe(150);
    expect(root.textContent).toContain('3 días seguidos');
    expect(root.textContent).toContain('Ya has practicado hoy.');
    expect(root.textContent).toContain('Mejor racha: 5');
    expect(root.textContent).toContain('Ejercicios resueltos: 7');
    expect(root.textContent).toContain('Lecciones completadas: 1');
  });

  it('uses the singular for a streak of one day', async () => {
    const root = await flush(await open(), summary({ streak: 1 }));

    expect(root.querySelector('.big')?.textContent).toBe('1 día seguido');
  });

  it('asks to practice today when the streak is alive but there was no practice yet', async () => {
    const root = await flush(await open(), summary({ activeToday: false }));

    expect(root.textContent).toContain('Resuelve un ejercicio hoy para mantener la racha.');
  });

  it('explains how to start a streak when there is none', async () => {
    const root = await flush(await open(), summary({ streak: 0, activeToday: false }));

    expect(root.textContent).toContain('Sin racha.');
    expect(root.querySelector('.big')).toBeNull();
  });

  it('lists every achievement, unlocked ones with their date and the rest as locked', async () => {
    const root = await flush(await open(), summary());

    const items = Array.from(root.querySelectorAll('.achievements li'));
    expect(items).toHaveLength(7);
    expect(items[0].classList.contains('unlocked')).toBe(true);
    expect(items[0].textContent).toContain('Primer paso');
    expect(items[0].textContent).toContain('Desbloqueado el');
    expect(items[1].classList.contains('unlocked')).toBe(false);
    expect(items[1].textContent).toContain('Por desbloquear');
  });

  it('is translated to English', async () => {
    const root = await flush(await open('en'), summary());

    expect(root.textContent).toContain('Level 2');
    expect(root.textContent).toContain('3 days in a row');
    expect(root.textContent).toContain('First step');
  });

  it('shows an error when the progress cannot be loaded', async () => {
    const fixture = await open();

    http.expectOne('/api/v1/progress').flush({}, { status: 500, statusText: 'Server Error' });
    await settle();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')).not.toBeNull();
  });
});
