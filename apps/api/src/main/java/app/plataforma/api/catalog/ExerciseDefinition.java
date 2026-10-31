package app.plataforma.api.catalog;

import java.util.List;
import java.util.Map;

/**
 * Un ejercicio tal como lo necesita otro módulo, con los textos en un idioma.
 * <p>
 * <b>Contiene la solución.</b> Es solo para uso del servidor (evaluación): nunca debe serializarse tal cual hacia el
 * navegador. {@code config} guarda lo que se muestra al estudiante (código, líneas...) y {@code solution}, lo que no.
 *
 * @param id identificador estable del contenido (por ejemplo {@code py.u01.l01.e01})
 * @param lessonId identificador de la lección a la que pertenece
 * @param options opciones de un ejercicio de selección múltiple (vacío en los demás tipos)
 * @param hints pistas de menos a más reveladora
 * @param language código del lenguaje del curso (por ejemplo {@code python} o {@code cpp})
 */
public record ExerciseDefinition(String id, String lessonId, int position, ExerciseType type, int difficulty,
		String prompt, String explanation, List<String> options, List<String> hints, Map<String, Object> config,
		Map<String, Object> solution, List<String> skillIds, String language) {

	/** Ejercicio de Python (el lenguaje por defecto). */
	public ExerciseDefinition(String id, String lessonId, int position, ExerciseType type, int difficulty, String prompt,
			String explanation, List<String> options, List<String> hints, Map<String, Object> config,
			Map<String, Object> solution, List<String> skillIds) {
		this(id, lessonId, position, type, difficulty, prompt, explanation, options, hints, config, solution, skillIds,
				"python");
	}

}
