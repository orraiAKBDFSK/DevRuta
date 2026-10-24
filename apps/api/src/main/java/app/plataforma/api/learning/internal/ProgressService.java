package app.plataforma.api.learning.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import app.plataforma.api.learning.internal.LearningDtos.AchievementView;
import app.plataforma.api.learning.internal.LearningDtos.ProgressView;
import app.plataforma.api.learning.internal.ProgressRepository.Progress;

/**
 * Reglas de gamificación. Son deliberadamente simples y están todas aquí para poder cambiarlas en un sitio:
 * <ul>
 * <li>XP de un ejercicio = dificultad × 10, un 20 % menos por cada pista usada, con un mínimo del 50 %. Solo la primera
 * vez que se resuelve; repetirlo no da más.</li>
 * <li>Nivel = 1 + raíz cuadrada de (XP / 50): cada nivel pide más que el anterior.</li>
 * <li>Racha = días seguidos (en la zona horaria del usuario, que envía la web; UTC si no la envía) con al menos un
 * ejercicio resuelto por primera vez.</li>
 * </ul>
 */
@Service
class ProgressService {

	static final List<String> ACHIEVEMENTS = List.of("first_solve", "solved_10", "solved_50", "streak_3", "streak_7",
			"lesson_complete", "no_hints_5");

	private final ProgressRepository repository;

	private final Clock clock;

	ProgressService(ProgressRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/** El día de hoy para el usuario: la misma hora UTC puede ser ayer o mañana según su zona. */
	private LocalDate today(ZoneId zone) {
		return LocalDate.ofInstant(clock.instant(), zone);
	}

	static int xpFor(int difficulty, int hintsUsed) {
		double factor = Math.max(0.5, 1 - 0.2 * hintsUsed);
		return (int) Math.round(difficulty * 10 * factor);
	}

	static int levelOf(int xp) {
		return 1 + (int) Math.floor(Math.sqrt(xp / 50.0));
	}

	/** Lo que ocurrió al resolver un ejercicio por primera vez. */
	record Awarded(int xp, int totalXp, int level, int streak, List<String> newAchievements) {
	}

	/** Concede XP y logros por un ejercicio recién resuelto; vacío si ya los había concedido. */
	java.util.Optional<Awarded> onSolved(UUID userId, UUID exerciseId, int difficulty, int hintsUsed, ZoneId zone) {
		int xp = xpFor(difficulty, hintsUsed);
		return repository.award(userId, exerciseId, xp, hintsUsed, today(zone)).map(progress -> {
			List<String> earned = new ArrayList<>();
			int solved = repository.solvedCount(userId);
			if (solved >= 1) {
				earned.add("first_solve");
			}
			if (solved >= 10) {
				earned.add("solved_10");
			}
			if (solved >= 50) {
				earned.add("solved_50");
			}
			if (progress.currentStreak() >= 3) {
				earned.add("streak_3");
			}
			if (progress.currentStreak() >= 7) {
				earned.add("streak_7");
			}
			if (!repository.completedLessons(userId).isEmpty()) {
				earned.add("lesson_complete");
			}
			if (repository.solvedWithoutHints(userId) >= 5) {
				earned.add("no_hints_5");
			}
			return new Awarded(xp, progress.xp(), levelOf(progress.xp()), progress.currentStreak(),
					repository.unlock(userId, earned));
		});
	}

	ProgressView view(UUID userId, ZoneId zone) {
		Progress progress = repository.progress(userId);
		LocalDate today = today(zone);
		LocalDate last = progress.lastActiveDay();
		boolean activeToday = today.equals(last);
		// La racha sigue viva hoy o ayer; si pasó más tiempo, se ha roto aunque la fila aún guarde el número viejo.
		int streak = last != null && !last.isBefore(today.minusDays(1)) ? progress.currentStreak() : 0;
		int level = levelOf(progress.xp());
		return new ProgressView(progress.xp(), level, (int) (Math.pow(level, 2) - 2 * level + 1) * 50,
				(int) Math.pow(level, 2) * 50, streak, progress.longestStreak(), activeToday,
				repository.solvedCount(userId), repository.completedLessons(userId),
				repository.achievements(userId).stream().map(a -> new AchievementView(a.code(), a.unlockedAt())).toList());
	}

}
