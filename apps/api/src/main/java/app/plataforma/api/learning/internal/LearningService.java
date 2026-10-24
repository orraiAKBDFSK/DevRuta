package app.plataforma.api.learning.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import app.plataforma.api.catalog.ExerciseCatalog;
import app.plataforma.api.catalog.ExerciseDefinition;
import app.plataforma.api.catalog.ExerciseType;
import app.plataforma.api.execution.CodeExecutor;
import app.plataforma.api.learning.internal.LearningDtos.AttemptResult;
import app.plataforma.api.learning.internal.LearningDtos.ExerciseView;
import app.plataforma.api.learning.internal.LearningDtos.HintResponse;
import app.plataforma.api.learning.internal.LearningRepository.State;
import app.plataforma.api.platform.ApiException;
import app.plataforma.api.platform.AttemptLimiter;

@Service
class LearningService {

	private static final Logger log = LoggerFactory.getLogger(LearningService.class);

	private final ExerciseCatalog catalog;

	private final LearningRepository repository;

	private final JsonMapper json;

	private final AttemptLimiter limiter;

	private final AttemptLimiter codeLimiter;

	private final CodeExecutor executor;

	private final ProgressService progress;

	LearningService(ExerciseCatalog catalog, LearningRepository repository, JsonMapper json,
			@Qualifier("exerciseLimiter") AttemptLimiter limiter, @Qualifier("codeRunLimiter") AttemptLimiter codeLimiter,
			CodeExecutor executor, ProgressService progress) {
		this.catalog = catalog;
		this.repository = repository;
		this.json = json;
		this.limiter = limiter;
		this.codeLimiter = codeLimiter;
		this.executor = executor;
		this.progress = progress;
	}

	@Transactional(readOnly = true)
	List<ExerciseView> exercisesOfLesson(UUID userId, String lessonId, String locale) {
		List<ExerciseDefinition> exercises = catalog.exercisesOfLesson(lessonId, locale)
				.orElseThrow(() -> notFound("Lección no encontrada"));
		Map<String, State> states = repository.statesOfLesson(userId, lessonId);
		return exercises.stream().map(e -> view(e, states.getOrDefault(e.id(), State.EMPTY))).toList();
	}

	/**
	 * Sin transacción a propósito: corregir código puede tardar varios segundos y no debe retener una conexión de la
	 * base de datos. Solo el registro del intento (en el repositorio) es transaccional.
	 */
	AttemptResult submit(UUID userId, String exerciseId, Map<String, Object> answer, String locale, java.time.ZoneId zone) {
		throttle(userId);
		ExerciseDefinition exercise = find(exerciseId, locale);
		UUID id = repository.exerciseId(exerciseId).orElseThrow(() -> notFound("Ejercicio no encontrado"));
		if (exercise.type() == ExerciseType.WRITE_CODE) {
			return submitCode(userId, id, exercise, answer, zone);
		}
		// Lanza 400 si la respuesta no tiene la forma esperada.
		ExerciseEvaluation.Result result = ExerciseEvaluation.evaluate(exercise, answer);
		State state = repository.recordAttempt(userId, id, json.writeValueAsString(answer), result.correct(),
				result.errorTag());
		// La explicación solo se muestra cuando el ejercicio está resuelto: antes sería regalar la respuesta.
		return reward(new AttemptResult(result.correct(), state.solved(), state.attempts(),
				Math.max(exercise.hints().size() - state.hintsUsed(), 0), state.solved() ? exercise.explanation() : null),
				userId, id, exercise, state, zone);
	}

	/** XP y logros solo la primera vez que se resuelve; el resto de respuestas correctas no añaden nada. */
	private AttemptResult reward(AttemptResult result, UUID userId, UUID id, ExerciseDefinition exercise, State state,
			java.time.ZoneId zone) {
		if (!result.correct()) {
			return result;
		}
		return progress.onSolved(userId, id, exercise.difficulty(), state.hintsUsed(), zone)
				.map(a -> result.withReward(a.xp(), a.newAchievements())).orElse(result);
	}

	private AttemptResult submitCode(UUID userId, UUID id, ExerciseDefinition exercise, Map<String, Object> answer,
			java.time.ZoneId zone) {
		if (!executor.isAvailable()) {
			throw unavailable(false);
		}
		codeLimiter.checkAllowed("code:" + userId);
		codeLimiter.record("code:" + userId);
		CodeEvaluation.Verdict verdict;
		try {
			verdict = CodeEvaluation.evaluate(exercise, answer, executor);
		}
		catch (CodeExecutor.Unavailable e) {
			// Un fallo del runner no es culpa del estudiante: no se registra el intento.
			log.warn("Runner no disponible: {}", e.getMessage());
			throw unavailable(e.isBusy());
		}
		State state = repository.recordAttempt(userId, id, json.writeValueAsString(answer), verdict.correct(),
				verdict.errorTag());
		return reward(new AttemptResult(verdict.correct(), state.solved(), state.attempts(),
				Math.max(exercise.hints().size() - state.hintsUsed(), 0), state.solved() ? exercise.explanation() : null,
				verdict.tests(), verdict.error(), verdict.unmet()), userId, id, exercise, state, zone);
	}

	private static ApiException unavailable(boolean busy) {
		return busy ? new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RUNNER_BUSY", "El motor de ejecución está ocupado", 5L)
				: new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RUNNER_UNAVAILABLE", "El motor de ejecución no está disponible");
	}

	@Transactional
	HintResponse nextHint(UUID userId, String exerciseId, String locale) {
		throttle(userId);
		ExerciseDefinition exercise = find(exerciseId, locale);
		int total = exercise.hints().size();
		if (total == 0) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NO_HINTS", "Este ejercicio no tiene pistas");
		}
		UUID id = repository.exerciseId(exerciseId).orElseThrow(() -> notFound("Ejercicio no encontrado"));
		int level = repository.revealHint(userId, id, total);
		return new HintResponse(level, total, exercise.hints().get(level - 1), level >= total);
	}

	private ExerciseView view(ExerciseDefinition e, State state) {
		Map<String, Object> config = e.config();
		boolean evaluable = e.type().isStaticallyEvaluable() || (e.type() == ExerciseType.WRITE_CODE && executor.isAvailable());
		List<String> lines = switch (e.type()) {
			case ORDER_LINES -> shuffled(e);
			default -> null;
		};
		List<String> revealed = e.hints().subList(0, Math.min(state.hintsUsed(), e.hints().size()));
		return new ExerciseView(e.id(), e.position(), e.type(), e.difficulty(), e.prompt(), evaluable,
				e.options().isEmpty() ? null : e.options(), (String) config.get("code"), lines,
				config.get("blankCount") instanceof Number n ? n.intValue() : null, (String) config.get("starter"),
				state.solved(), state.attempts(), revealed, e.hints().size(), state.solved() ? e.explanation() : null);
	}

	/**
	 * Líneas en un orden barajado pero estable (la misma semilla para cada ejercicio), y siempre distinto del correcto
	 * para que el ejercicio no llegue ya resuelto.
	 */
	@SuppressWarnings("unchecked")
	private static List<String> shuffled(ExerciseDefinition e) {
		List<String> correct = ((List<Object>) e.solution().get("lines")).stream().map(String::valueOf).toList();
		List<String> lines = new ArrayList<>(correct);
		Collections.shuffle(lines, new Random(e.id().hashCode()));
		if (lines.equals(correct)) {
			Collections.rotate(lines, 1);
		}
		return lines;
	}

	private ExerciseDefinition find(String exerciseId, String locale) {
		return catalog.exercise(exerciseId, locale).orElseThrow(() -> notFound("Ejercicio no encontrado"));
	}

	private void throttle(UUID userId) {
		String key = "learning:" + userId;
		limiter.checkAllowed(key);
		limiter.record(key);
	}

	private static ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
	}

}
