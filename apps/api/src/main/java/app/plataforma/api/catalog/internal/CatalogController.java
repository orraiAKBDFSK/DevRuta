package app.plataforma.api.catalog.internal;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.plataforma.api.catalog.internal.CatalogDtos.CourseDetail;
import app.plataforma.api.catalog.internal.CatalogDtos.CourseSummary;
import app.plataforma.api.catalog.internal.CatalogDtos.LessonDetail;
import app.plataforma.api.catalog.internal.CatalogDtos.SkillNode;
import app.plataforma.api.platform.ApiException;
import app.plataforma.api.platform.LocaleSupport;

/**
 * Catálogo de lectura: cursos, mapa de unidades y lecciones, y grafo de habilidades. Requiere sesión. El idioma sale de
 * la cabecera {@code Accept-Language}, por eso las respuestas llevan {@code Vary: Accept-Language}.
 */
@RestController
@RequestMapping("/v1/catalog")
class CatalogController {

	private final CatalogQueries queries;

	CatalogController(CatalogQueries queries) {
		this.queries = queries;
	}

	@GetMapping("/courses")
	ResponseEntity<List<CourseSummary>> courses(
			@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
		return varyByLanguage(queries.courses(LocaleSupport.resolve(acceptLanguage)));
	}

	@GetMapping("/courses/{courseId}")
	ResponseEntity<CourseDetail> course(@PathVariable String courseId,
			@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
		return varyByLanguage(queries.course(courseId, LocaleSupport.resolve(acceptLanguage))
				.orElseThrow(() -> notFound("Curso no encontrado")));
	}

	@GetMapping("/courses/{courseId}/skills")
	ResponseEntity<List<SkillNode>> skills(@PathVariable String courseId,
			@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
		return varyByLanguage(queries.skills(courseId, LocaleSupport.resolve(acceptLanguage))
				.orElseThrow(() -> notFound("Curso no encontrado")));
	}

	@GetMapping("/lessons/{lessonId}")
	ResponseEntity<LessonDetail> lesson(@PathVariable String lessonId,
			@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
		return varyByLanguage(queries.lesson(lessonId, LocaleSupport.resolve(acceptLanguage))
				.orElseThrow(() -> notFound("Lección no encontrada")));
	}

	private static <T> ResponseEntity<T> varyByLanguage(T body) {
		return ResponseEntity.ok().header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE).body(body);
	}

	private static ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
	}

}
