package app.plataforma.api.learning.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** XP, rachas y logros. Las operaciones que cambian varias tablas son atómicas. */
@Repository
class ProgressRepository {

	record Progress(int xp, int currentStreak, int longestStreak, LocalDate lastActiveDay) {

		static final Progress EMPTY = new Progress(0, 0, 0, null);

	}

	private final JdbcClient jdbc;

	ProgressRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Concede la XP de un ejercicio resuelto por primera vez y actualiza la racha. Devuelve el progreso resultante,
	 * o vacío si ese ejercicio ya había dado XP (por ejemplo, dos envíos correctos simultáneos).
	 */
	@Transactional
	Optional<Progress> award(UUID userId, UUID exerciseId, int xp, int hintsUsed, LocalDate today) {
		int inserted = jdbc.sql("""
				insert into xp_event (user_id, exercise_id, xp, hints_used) values (:user, :exercise, :xp, :hints)
				on conflict do nothing""").param("user", userId).param("exercise", exerciseId).param("xp", xp)
				.param("hints", hintsUsed).update();
		if (inserted == 0) {
			return Optional.empty();
		}
		jdbc.sql("insert into user_progress (user_id) values (:user) on conflict do nothing").param("user", userId)
				.update();
		// Bloquea la fila para que dos ejercicios resueltos a la vez no calculen la racha con datos viejos.
		Progress current = jdbc.sql("select xp, current_streak, longest_streak, last_active_day from user_progress "
				+ "where user_id = :user for update").param("user", userId).query(ProgressRepository::map).single();
		int streak = nextStreak(current, today);
		int longest = Math.max(current.longestStreak(), streak);
		jdbc.sql("""
				update user_progress set xp = xp + :xp, current_streak = :streak, longest_streak = :longest,
				    last_active_day = :today, updated_at = now() where user_id = :user""").param("xp", xp)
				.param("streak", streak).param("longest", longest).param("today", today).param("user", userId)
				.update();
		return Optional.of(new Progress(current.xp() + xp, streak, longest, today));
	}

	static int nextStreak(Progress current, LocalDate today) {
		LocalDate last = current.lastActiveDay();
		if (last == null) {
			return 1;
		}
		if (last.equals(today)) {
			return Math.max(current.currentStreak(), 1);
		}
		return last.equals(today.minusDays(1)) ? current.currentStreak() + 1 : 1;
	}

	Progress progress(UUID userId) {
		return jdbc.sql("select xp, current_streak, longest_streak, last_active_day from user_progress "
				+ "where user_id = :user").param("user", userId).query(ProgressRepository::map).optional()
				.orElse(Progress.EMPTY);
	}

	private static Progress map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
		java.sql.Date last = rs.getDate("last_active_day");
		return new Progress(rs.getInt("xp"), rs.getInt("current_streak"), rs.getInt("longest_streak"),
				last == null ? null : last.toLocalDate());
	}

	int solvedCount(UUID userId) {
		return jdbc.sql("select count(*) from xp_event where user_id = :user").param("user", userId)
				.query(Integer.class).single();
	}

	int solvedWithoutHints(UUID userId) {
		return jdbc.sql("select count(*) from xp_event where user_id = :user and hints_used = 0")
				.param("user", userId).query(Integer.class).single();
	}

	/** Contenido de las lecciones con todos sus ejercicios resueltos (solo cuentan los ejercicios no archivados). */
	List<String> completedLessons(UUID userId) {
		return jdbc.sql("""
				select l.content_id
				from lesson l
				join exercise e on e.lesson_id = l.id and not e.archived
				left join exercise_state s on s.exercise_id = e.id and s.user_id = :user and s.solved_at is not null
				where l.status <> 'ARCHIVED'
				group by l.id, l.content_id
				having count(*) = count(s.exercise_id)
				order by l.content_id""").param("user", userId).query(String.class).list();
	}

	/** Desbloquea los logros indicados; devuelve solo los que eran nuevos. */
	@Transactional
	List<String> unlock(UUID userId, List<String> codes) {
		return codes.stream().filter(code -> jdbc.sql("""
				insert into user_achievement (user_id, code) values (:user, :code) on conflict do nothing""")
				.param("user", userId).param("code", code).update() == 1).toList();
	}

	record Unlocked(String code, java.time.Instant unlockedAt) {
	}

	List<Unlocked> achievements(UUID userId) {
		return jdbc.sql("select code, unlocked_at from user_achievement where user_id = :user order by unlocked_at, code")
				.param("user", userId).query((rs, n) -> new Unlocked(rs.getString("code"),
						rs.getTimestamp("unlocked_at").toInstant()))
				.list();
	}

}
