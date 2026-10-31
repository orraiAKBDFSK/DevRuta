package app.plataforma.api.learning.internal;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.plataforma.api.identity.AuthenticatedUser;
import app.plataforma.api.learning.internal.LearningDtos.AttemptRequest;
import app.plataforma.api.learning.internal.LearningDtos.AttemptResult;
import app.plataforma.api.learning.internal.LearningDtos.ExerciseView;
import app.plataforma.api.learning.internal.LearningDtos.HintResponse;
import app.plataforma.api.platform.LocaleSupport;
import app.plataforma.api.profile.ProfileDirectory;
import app.plataforma.api.platform.ZoneSupport;

/** Ejercicios de una lección, intentos y pistas. Todo depende del usuario autenticado y del idioma pedido. */
@RestController
@RequestMapping("/v1")
class LearningController {

	private final LearningService learning;

	private final ProgressService progress;

	private final ProfileDirectory profiles;

	LearningController(LearningService learning, ProgressService progress, ProfileDirectory profiles) {
		this.learning = learning;
		this.progress = progress;
		this.profiles = profiles;
	}

	/**
	 * Zona horaria con la que se decide qué día es para el usuario: la de su perfil si la ha fijado (cualquiera distinta
	 * de UTC, que es el valor por defecto), si no la que envía el navegador, y si no UTC.
	 */
	private ZoneId zoneOf(UUID userId, String header) {
		ZoneId stored = ZoneSupport.resolve(profiles.timeZoneOf(userId));
		return stored.normalized().equals(ZoneOffset.UTC) ? ZoneSupport.resolve(header) : stored;
	}

	/** Resumen de progreso del usuario autenticado. No depende del idioma: el nombre de los logros se traduce en la web. */
	@GetMapping("/progress")
	ResponseEntity<LearningDtos.ProgressView> progress(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestHeader(value = ZoneSupport.HEADER, required = false) String timeZone) {
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(progress.view(user.id(), zoneOf(user.id(), timeZone)));
	}

	@GetMapping("/lessons/{lessonId}/exercises")
	ResponseEntity<List<ExerciseView>> exercises(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable String lessonId,
			@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
		return vary(learning.exercisesOfLesson(user.id(), lessonId, LocaleSupport.resolve(acceptLanguage)));
	}

	@PostMapping("/exercises/{exerciseId}/attempts")
	ResponseEntity<AttemptResult> attempt(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable String exerciseId, @Valid @RequestBody AttemptRequest body,
			@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
			@RequestHeader(value = ZoneSupport.HEADER, required = false) String timeZone) {
		return vary(learning.submit(user.id(), exerciseId, body.answer(), LocaleSupport.resolve(acceptLanguage),
				zoneOf(user.id(), timeZone)));
	}

	@PostMapping("/exercises/{exerciseId}/hints")
	ResponseEntity<HintResponse> hint(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable String exerciseId,
			@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
		return vary(learning.nextHint(user.id(), exerciseId, LocaleSupport.resolve(acceptLanguage)));
	}

	private static <T> ResponseEntity<T> vary(T body) {
		return ResponseEntity.ok().header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE).body(body);
	}

}
