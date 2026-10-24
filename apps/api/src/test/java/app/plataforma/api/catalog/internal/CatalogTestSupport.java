package app.plataforma.api.catalog.internal;

import java.nio.file.Path;

import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

public final class CatalogTestSupport {

	private CatalogTestSupport() {
	}

	/**
	 * Importa contenido desde una carpeta. El importador es privado del módulo catalog; este método público de prueba
	 * permite que las pruebas de otros módulos (learning) lo usen sin abrir la API de producción.
	 */
	public static void importContent(ApplicationContext context, Path root, boolean includeDrafts) {
		context.getBean(ContentImporter.class).importFrom(root,
				includeDrafts ? ContentStatus.DRAFT : ContentStatus.PUBLISHED);
	}

	/** Vacía el catálogo y los datos de aprendizaje: las pruebas comparten la base de datos y cada una parte de cero. */
	public static void clean(JdbcTemplate jdbc) {
		for (String table : new String[] { "xp_event", "user_progress", "user_achievement", "exercise_attempt", "exercise_state", "exercise_skill",
				"exercise_translation", "exercise", "lesson_skill", "lesson_translation", "lesson",
				"unit_translation", "unit",
				"course_translation", "course", "skill_prerequisite", "skill_translation", "skill",
				"programming_language" }) {
			jdbc.update("delete from " + table);
		}
	}

	public static int count(JdbcTemplate jdbc, String sql, Object... args) {
		Integer value = jdbc.queryForObject(sql, Integer.class, args);
		return value == null ? 0 : value;
	}

}
