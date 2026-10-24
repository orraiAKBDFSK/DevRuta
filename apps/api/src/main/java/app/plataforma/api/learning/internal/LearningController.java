package app.plataforma.api.learning.internal;

import java.util.List;

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
import app.plataforma.api.platform.ZoneSupport;

/** Ejercicios de una lección, intentos y pistas. Todo depende del usuario autenticado y del idioma pedido. */
@RestController
@RequestMapping("/v1")
class LearningController {

	private final LearningService learning;

	private final ProgressService progress;

	LearningController(LearningService learning, ProgressService progress) {
		this.learning = learning;
		this.progress = progress;
	}

	/** Resumen de progreso del usuario autenticado. No depende del idioma: el nombre de los logros se traduce en la web. */
	@GetMapping("/progress")
	ResponseEntity<LearningDtos.ProgressView> progress(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestHeader(value = ZoneSupport.HEADER, required = false) String timeZone) {
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(progress.view(user.id(), ZoneSupport.resolve(timeZone)));
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
				ZoneSupport.resolve(timeZone)));
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
