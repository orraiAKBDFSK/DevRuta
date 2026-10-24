package app.plataforma.api.learning.internal;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

import app.plataforma.api.catalog.ExerciseType;

/** Peticiones y respuestas del módulo learning. Ninguna respuesta incluye soluciones. */
final class LearningDtos {

	private LearningDtos() {
	}

	/**
	 * Lo que ve el estudiante de un ejercicio. Según el tipo se rellenan unos campos u otros ({@code null} en el resto):
	 * {@code options} (selección múltiple), {@code code} (predecir y completar), {@code lines} (ordenar, ya
	 * desordenadas), {@code blankCount} (completar) y {@code starter} (escribir código).
	 * {@code explanation} solo llega cuando el ejercicio ya está resuelto.
	 */
	record ExerciseView(String id, int position, ExerciseType type, int difficulty, String prompt, boolean evaluable,
			List<String> options, String code, List<String> lines, Integer blankCount, String starter, boolean solved,
			int attempts, List<String> revealedHints, int hintsTotal, String explanation) {
	}

	record AttemptRequest(@NotNull Map<String, Object> answer) {
	}

	/**
	 * {@code tests}, {@code error} y {@code unmetRequirements} solo se rellenan en los ejercicios de escribir código
	 * ({@code null} en el resto). {@code error} es la salida de error del propio programa del estudiante.
	 */
	record AttemptResult(boolean correct, boolean solved, int attempts, int hintsAvailable, String explanation,
			List<TestOutcome> tests, String error, List<String> unmetRequirements, Integer xpEarned,
			List<String> newAchievements) {

		AttemptResult(boolean correct, boolean solved, int attempts, int hintsAvailable, String explanation) {
			this(correct, solved, attempts, hintsAvailable, explanation, null, null, null, null, null);
		}

		AttemptResult(boolean correct, boolean solved, int attempts, int hintsAvailable, String explanation,
				List<TestOutcome> tests, String error, List<String> unmetRequirements) {
			this(correct, solved, attempts, hintsAvailable, explanation, tests, error, unmetRequirements, null, null);
		}

		/** {@code xpEarned} y {@code newAchievements} solo se rellenan la primera vez que se resuelve el ejercicio. */
		AttemptResult withReward(Integer xp, List<String> achievements) {
			return new AttemptResult(correct, solved, attempts, hintsAvailable, explanation, tests, error,
					unmetRequirements, xp, achievements);
		}

	}

	record AchievementView(String code, java.time.Instant unlockedAt) {
	}

	/** {@code levelStartXp}/{@code nextLevelXp}: límites de XP del nivel actual, para pintar la barra de progreso. */
	record ProgressView(int xp, int level, int levelStartXp, int nextLevelXp, int streak, int longestStreak,
			boolean activeToday, int solvedExercises, List<String> completedLessons,
			List<AchievementView> achievements) {
	}

	/**
	 * Resultado de un test. De los tests ocultos solo se sabe si pasaron: {@code input}, {@code expected} y
	 * {@code actual} son {@code null} para no revelar los casos con los que se corrige.
	 */
	record TestOutcome(int number, boolean visible, boolean passed, boolean timedOut, String input, String expected,
			String actual) {
	}

	record HintResponse(int level, int total, String text, boolean last) {
	}

}
