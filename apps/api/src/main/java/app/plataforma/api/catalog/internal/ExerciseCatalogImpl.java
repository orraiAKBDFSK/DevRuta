package app.plataforma.api.catalog.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import app.plataforma.api.catalog.ExerciseCatalog;
import app.plataforma.api.catalog.ExerciseDefinition;
import app.plataforma.api.catalog.ExerciseType;

/** Implementación de la API pública de ejercicios. Solo devuelve ejercicios de lecciones, unidades y cursos activos. */
@Repository
class ExerciseCatalogImpl implements ExerciseCatalog {

	private static final String SELECT = """
			select e.content_id, l.content_id as lesson_cid, pl.code as language, e.position, e.type, e.difficulty,
			       e.config::text as config, e.solution::text as solution,
			       coalesce(t.prompt, f.prompt) as prompt, coalesce(t.explanation, f.explanation) as explanation,
			       coalesce(t.options, f.options)::text as options, coalesce(t.hints, f.hints)::text as hints
			from exercise e
			join lesson l on l.id = e.lesson_id
			join unit u on u.id = l.unit_id
			join course c on c.id = u.course_id
			join programming_language pl on pl.id = c.language_id
			left join exercise_translation t on t.exercise_id = e.id and t.locale = :locale
			left join exercise_translation f on f.exercise_id = e.id and f.locale = 'es'
			where not e.archived and l.status <> 'ARCHIVED' and not u.archived and not c.archived
			""";

	private final JdbcClient jdbc;

	private final JsonMapper json;

	ExerciseCatalogImpl(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Override
	public Optional<List<ExerciseDefinition>> exercisesOfLesson(String lessonId, String locale) {
		boolean exists = jdbc.sql("""
				select count(*) from lesson l
				join unit u on u.id = l.unit_id join course c on c.id = u.course_id
				where l.content_id = :id and l.status <> 'ARCHIVED' and not u.archived and not c.archived""")
				.param("id", lessonId).query(Integer.class).single() > 0;
		if (!exists) {
			return Optional.empty();
		}
		Map<String, List<String>> skills = skills("l.content_id = :id", lessonId);
		return Optional.of(jdbc.sql(SELECT + " and l.content_id = :id order by e.position").param("locale", locale)
				.param("id", lessonId).query((rs, n) -> map(rs, skills)).list());
	}

	@Override
	public Optional<ExerciseDefinition> exercise(String exerciseId, String locale) {
		Map<String, List<String>> skills = skills("e.content_id = :id", exerciseId);
		return jdbc.sql(SELECT + " and e.content_id = :id").param("locale", locale).param("id", exerciseId)
				.query((rs, n) -> map(rs, skills)).optional();
	}

	private ExerciseDefinition map(java.sql.ResultSet rs, Map<String, List<String>> skills) throws java.sql.SQLException {
		String id = rs.getString("content_id");
		return new ExerciseDefinition(id, rs.getString("lesson_cid"), rs.getInt("position"),
				ExerciseType.valueOf(rs.getString("type")), rs.getInt("difficulty"), rs.getString("prompt"),
				rs.getString("explanation"), strings(rs.getString("options")), strings(rs.getString("hints")),
				object(rs.getString("config")), object(rs.getString("solution")),
				skills.getOrDefault(id, List.of()), rs.getString("language"));
	}

	private Map<String, List<String>> skills(String condition, String id) {
		Map<String, List<String>> result = new HashMap<>();
		jdbc.sql("""
				select e.content_id as exercise_cid, s.content_id as skill_cid
				from exercise_skill es
				join exercise e on e.id = es.exercise_id
				join lesson l on l.id = e.lesson_id
				join skill s on s.id = es.skill_id
				where %s order by s.position""".formatted(condition)).param("id", id).query((rs, n) -> {
					result.computeIfAbsent(rs.getString("exercise_cid"), k -> new ArrayList<>())
							.add(rs.getString("skill_cid"));
					return null;
				}).list();
		return result;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> object(String value) {
		return value == null ? Map.of() : json.readValue(value, Map.class);
	}

	private List<String> strings(String value) {
		return value == null ? List.of() : List.of(json.readValue(value, String[].class));
	}

}
