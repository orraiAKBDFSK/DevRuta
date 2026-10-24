package app.plataforma.api.profile.internal;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import app.plataforma.api.profile.ProfileDirectory;

@Component
class ProfileDirectoryImpl implements ProfileDirectory {

	private final UserProfileRepository profiles;

	ProfileDirectoryImpl(UserProfileRepository profiles) {
		this.profiles = profiles;
	}

	@Override
	@Transactional(readOnly = true)
	public String localeOf(UUID userId) {
		return profiles.findById(userId).map(UserProfile::getLocale).orElse("es");
	}

}
