package app.plataforma.api.learning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import app.plataforma.api.learning.internal.ProgressRepository.Progress;

class ProgressRulesTests {

	@Test
	void xpGrowsWithDifficultyAndShrinksWithHintsDownToHalf() {
		assertThat(ProgressService.xpFor(1, 0)).isEqualTo(10);
		assertThat(ProgressService.xpFor(5, 0)).isEqualTo(50);
		assertThat(ProgressService.xpFor(1, 1)).isEqualTo(8);
		assertThat(ProgressService.xpFor(3, 2)).isEqualTo(18);
		assertThat(ProgressService.xpFor(3, 9)).isEqualTo(15);
		assertThat(ProgressService.xpFor(1, 9)).isEqualTo(5);
	}

	@Test
	void everySolvedExerciseGivesAtLeastSomeXp() {
		for (int difficulty = 1; difficulty <= 5; difficulty++) {
			for (int hints = 0; hints <= 10; hints++) {
				assertThat(ProgressService.xpFor(difficulty, hints)).isPositive();
			}
		}
	}

	@Test
	void levelsGetFartherApart() {
		assertThat(ProgressService.levelOf(0)).isEqualTo(1);
		assertThat(ProgressService.levelOf(49)).isEqualTo(1);
		assertThat(ProgressService.levelOf(50)).isEqualTo(2);
		assertThat(ProgressService.levelOf(199)).isEqualTo(2);
		assertThat(ProgressService.levelOf(200)).isEqualTo(3);
		assertThat(ProgressService.levelOf(450)).isEqualTo(4);
	}

	@Test
	void streakStartsContinuesStaysAndBreaks() {
		LocalDate today = LocalDate.of(2026, 10, 23);

		assertThat(ProgressRepository.nextStreak(Progress.EMPTY, today)).isEqualTo(1);
		assertThat(ProgressRepository.nextStreak(new Progress(10, 4, 4, today.minusDays(1)), today)).isEqualTo(5);
		assertThat(ProgressRepository.nextStreak(new Progress(10, 4, 4, today), today)).isEqualTo(4);
		assertThat(ProgressRepository.nextStreak(new Progress(10, 4, 9, today.minusDays(2)), today)).isEqualTo(1);
	}

}
