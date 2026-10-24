export type ExerciseType = 'MULTIPLE_CHOICE' | 'PREDICT_OUTPUT' | 'ORDER_LINES' | 'FILL_CODE' | 'WRITE_CODE';

/**
 * Un ejercicio tal como lo envía el servidor. Según el tipo se rellenan unos campos u otros (el resto es `null`).
 * Nunca incluye la solución; la `explanation` solo llega cuando el ejercicio está resuelto.
 */
export interface Exercise {
  id: string;
  position: number;
  type: ExerciseType;
  difficulty: number;
  prompt: string;
  evaluable: boolean;
  options: string[] | null;
  code: string | null;
  lines: string[] | null;
  blankCount: number | null;
  starter: string | null;
  solved: boolean;
  attempts: number;
  revealedHints: string[];
  hintsTotal: number;
  explanation: string | null;
}

/** Respuesta que se envía al servidor; la forma depende del tipo de ejercicio. */
export type Answer =
  | { choice: number }
  | { text: string }
  | { order: string[] }
  | { blanks: string[] }
  | { code: string };

/** Resultado de una prueba de un ejercicio de escribir código. En las ocultas, `input`/`expected`/`actual` son `null`. */
export interface TestOutcome {
  number: number;
  visible: boolean;
  passed: boolean;
  timedOut: boolean;
  input: string | null;
  expected: string | null;
  actual: string | null;
}

export interface AttemptResult {
  correct: boolean;
  solved: boolean;
  attempts: number;
  hintsAvailable: number;
  explanation: string | null;
  /** Solo en ejercicios de escribir código. */
  tests?: TestOutcome[] | null;
  /** Salida de error del propio programa del estudiante (solo escribir código). */
  error?: string | null;
  /** Requisitos del código que faltan: `assignment`, `function:nombre` o `function:nombre:returns`. */
  unmetRequirements?: string[] | null;
  /** XP ganada; solo llega la primera vez que se resuelve el ejercicio (`null` después). */
  xpEarned?: number | null;
  /** Códigos de los logros desbloqueados con esta respuesta. */
  newAchievements?: string[] | null;
}

export interface Achievement {
  code: string;
  unlockedAt: string;
}

/** Resumen de progreso. `levelStartXp` y `nextLevelXp` delimitan el nivel actual para pintar la barra. */
export interface ProgressSummary {
  xp: number;
  level: number;
  levelStartXp: number;
  nextLevelXp: number;
  streak: number;
  longestStreak: number;
  activeToday: boolean;
  solvedExercises: number;
  completedLessons: string[];
  achievements: Achievement[];
}

/** Todos los logros que existen (el servidor solo envía los desbloqueados). Mismo orden que se muestran. */
export const ACHIEVEMENT_CODES = [
  'first_solve',
  'solved_10',
  'solved_50',
  'streak_3',
  'streak_7',
  'lesson_complete',
  'no_hints_5',
] as const;

export interface Hint {
  level: number;
  total: number;
  text: string;
  last: boolean;
}
