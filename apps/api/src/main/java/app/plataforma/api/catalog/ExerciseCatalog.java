package app.plataforma.api.catalog;

import java.util.List;
import java.util.Optional;

/** API pública del módulo catalog para consultar ejercicios. Nunca devuelve ejercicios archivados. */
public interface ExerciseCatalog {

	/**
	 * Ejercicios de una lección en orden. El {@code Optional} está vacío si la lección no existe o está archivada;
	 * una lección sin ejercicios devuelve una lista vacía.
	 */
	Optional<List<ExerciseDefinition>> exercisesOfLesson(String lessonId, String locale);

	Optional<ExerciseDefinition> exercise(String exerciseId, String locale);

}
