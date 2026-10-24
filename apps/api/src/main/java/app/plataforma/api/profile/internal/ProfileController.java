package app.plataforma.api.profile.internal;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.plataforma.api.identity.AuthenticatedUser;

@RestController
@RequestMapping("/v1/me/profile")
class ProfileController {

	private final ProfileService profiles;

	ProfileController(ProfileService profiles) {
		this.profiles = profiles;
	}

	@GetMapping
	ProfileDtos.ProfileResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
		return profiles.get(user.id());
	}

	@PatchMapping
	ProfileDtos.ProfileResponse update(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody ProfileDtos.UpdateProfileRequest body) {
		return profiles.update(user.id(), body);
	}

}
