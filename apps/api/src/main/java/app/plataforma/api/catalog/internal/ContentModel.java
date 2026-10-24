package app.plataforma.api.catalog.internal;

import java.util.List;
import java.util.Map;

import app.plataforma.api.catalog.ExerciseType;

/** Contenido ya leído y validado de los archivos YAML, listo para escribirse en la base de datos. */
final class ContentModel {

	private ContentModel() {
	}

	/** Textos traducidos por idioma ({@code es}, {@code en}); ambos siempre presentes. */
	record Texts(Map<String, String> byLocale) {

		String get(String locale) {
			return byLocale.get(locale);
		}

	}

	record Skill(String id, Texts name, List<String> requires, int position) {
	}

	record Course(String id, String languageCode, String languageName, int position, Texts title,
			Texts description) {
	}

	record Unit(String id, int position, Texts title, Texts summary) {
	}

	/**
	 * Un ejercicio. {@code config} es lo que ve el estudiante (código, líneas...), {@code solution} lo que no.
	 * {@code options} solo se usa en selección múltiple (cada opción con su texto en es y en).
	 */
	record Exercise(String id, int position, ExerciseType type, int difficulty, Texts prompt, Texts explanation,
			List<Texts> hints, List<Texts> options, Map<String, Object> config, Map<String, Object> solution,
			List<String> skills) {
	}

	record Lesson(String id, String unitId, int position, ContentStatus status, Texts title, Texts body,
			List<String> skills, List<Exercise> exercises, String hash) {
	}

	/** Todo el contenido de un lenguaje. */
	record LanguageContent(Course course, List<Skill> skills, List<Unit> units, List<Lesson> lessons) {
	}

}
