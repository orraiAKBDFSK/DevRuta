package app.plataforma.api.learning.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Intentos (solo se insertan) y estado por usuario y ejercicio. */
@Repository
class LearningRepository {

	record State(int attempts, int hintsUsed, boolean solved) {

		static final State EMPTY = new State(0, 0, false);

	}

	private final JdbcClient jdbc;

	LearningRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Optional<UUID> exerciseId(String contentId) {
		return jdbc.sql("select id from exercise where content_id = :cid and not archived").param("cid", contentId)
				.query(UUID.class).optional();
	}

	/** Estado del usuario para todos los ejercicios de una lección, por identificador de contenido. */
	Map<String, State> statesOfLesson(UUID userId, String lessonContentId) {
		Map<String, State> result = new HashMap<>();
		jdbc.sql("""
				select e.content_id, s.attempts, s.hints_used, s.solved_at is not null as solved
				from exercise_state s
				join exercise e on e.id = s.exercise_id
				join lesson l on l.id = e.lesson_id
				where s.user_id = :user and l.content_id = :lesson""").param("user", userId)
				.param("lesson", lessonContentId).query((rs, n) -> {
					result.put(rs.getString("content_id"),
							new State(rs.getInt("attempts"), rs.getInt("hints_used"), rs.getBoolean("solved")));
					return null;
				}).list();
		return result;
	}

	State state(UUID userId, UUID exerciseId) {
		return jdbc.sql("""
				select attempts, hints_used, solved_at is not null as solved
				from exercise_state where user_id = :user and exercise_id = :exercise""").param("user", userId)
				.param("exercise", exerciseId)
				.query((rs, n) -> new State(rs.getInt("attempts"), rs.getInt("hints_used"), rs.getBoolean("solved")))
				.optional().orElse(State.EMPTY);
	}

	/** Registra un intento (sin perder información) y actualiza el estado. Devuelve el estado resultante. */
	@Transactional
	State recordAttempt(UUID userId, UUID exerciseId, String answerJson, boolean correct, String errorTag) {
		State state = jdbc.sql("""
				insert into exercise_state (user_id, exercise_id, attempts, solved_at)
				values (:user, :exercise, 1, case when :correct then now() end)
				on conflict (user_id, exercise_id) do update
				set attempts = exercise_state.attempts + 1,
				    solved_at = coalesce(exercise_state.solved_at, case when :correct then now() end),
				    updated_at = now()
				returning attempts, hints_used, solved_at is not null as solved""").param("user", userId)
				.param("exercise", exerciseId).param("correct", correct)
				.query((rs, n) -> new State(rs.getInt("attempts"), rs.getInt("hints_used"), rs.getBoolean("solved")))
				.single();
		jdbc.sql("""
				insert into exercise_attempt (user_id, exercise_id, answer, correct, error_tag, hints_used)
				values (:user, :exercise, cast(:answer as jsonb), :correct, :tag, :hints)""")
				.param("user", userId).param("exercise", exerciseId).param("answer", answerJson)
				.param("correct", correct).param("tag", errorTag).param("hints", state.hintsUsed()).update();
		return state;
	}

	/** Revela la siguiente pista (sin pasar del total) y devuelve cuántas lleva reveladas el usuario. */
	int revealHint(UUID userId, UUID exerciseId, int total) {
		return jdbc.sql("""
				insert into exercise_state (user_id, exercise_id, hints_used)
				values (:user, :exercise, least(1, :total))
				on conflict (user_id, exercise_id) do update
				set hints_used = least(exercise_state.hints_used + 1, :total), updated_at = now()
				returning hints_used""").param("user", userId).param("exercise", exerciseId).param("total", total)
				.query(Integer.class).single();
	}

}
