package app.plataforma.api.profile.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

final class ProfileDtos {

	private ProfileDtos() {
	}

	/** Actualización parcial: los campos {@code null} no se modifican. */
	record UpdateProfileRequest(
			@Size(min = 1, max = 50) String displayName,
			@Pattern(regexp = "es|en") String locale,
			ExperienceLevel experienceLevel,
			LearningGoal goal,
			@Min(5) @Max(120) Integer dailyGoalMinutes,
			@Size(max = 64) String timezone) {
	}

	record ProfileResponse(String displayName, String locale, ExperienceLevel experienceLevel, LearningGoal goal,
			int dailyGoalMinutes, String timezone) {

		static ProfileResponse from(UserProfile profile) {
			return new ProfileResponse(profile.getDisplayName(), profile.getLocale(), profile.getExperienceLevel(),
					profile.getGoal(), profile.getDailyGoalMinutes(), profile.getTimezone());
		}

	}

}
