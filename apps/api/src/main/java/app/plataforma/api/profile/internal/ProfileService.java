package app.plataforma.api.profile.internal;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.plataforma.api.platform.ApiException;

@Service
class ProfileService {

	private final UserProfileRepository profiles;

	ProfileService(UserProfileRepository profiles) {
		this.profiles = profiles;
	}

	@Transactional(readOnly = true)
	ProfileDtos.ProfileResponse get(UUID userId) {
		return ProfileDtos.ProfileResponse.from(find(userId));
	}

	@Transactional
	ProfileDtos.ProfileResponse update(UUID userId, ProfileDtos.UpdateProfileRequest request) {
		UserProfile profile = find(userId);
		if (request.displayName() != null) {
			String name = request.displayName().trim();
			if (name.isEmpty()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "El nombre no puede estar vacío");
			}
			profile.setDisplayName(name);
		}
		if (request.locale() != null) {
			profile.setLocale(request.locale());
		}
		if (request.experienceLevel() != null) {
			profile.setExperienceLevel(request.experienceLevel());
		}
		if (request.goal() != null) {
			profile.setGoal(request.goal());
		}
		if (request.dailyGoalMinutes() != null) {
			profile.setDailyGoalMinutes(request.dailyGoalMinutes().shortValue());
		}
		if (request.timezone() != null) {
			profile.setTimezone(validTimezone(request.timezone()));
		}
		return ProfileDtos.ProfileResponse.from(profile);
	}

	private UserProfile find(UUID userId) {
		return profiles.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Perfil no encontrado"));
	}

	private static String validTimezone(String timezone) {
		try {
			return ZoneId.of(timezone).getId();
		}
		catch (DateTimeException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE", "Zona horaria no válida");
		}
	}

}
