package app.plataforma.api.catalog.internal;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Escrituras del catálogo para el importador. SQL explícito: son upserts por {@code content_id}. */
@Repository
class ContentRepository {

	record ExistingLesson(UUID id, String hash, UUID unitId, String status) {
	}

	private final JdbcClient jdbc;

	ContentRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// ---- Lenguaje y habilidades ----

	UUID upsertLanguage(String code, String name) {
		return jdbc.sql("""
				insert into programming_language (code, name) values (:code, :name)
				on conflict (code) do update set name = excluded.name
				returning id""").param("code", code).param("name", name).query(UUID.class).single();
	}

	UUID upsertSkill(String contentId, UUID languageId, int position) {
		return jdbc.sql("""
				insert into skill (content_id, language_id, position) values (:cid, :lang, :pos)
				on conflict (content_id) do update set language_id = excluded.language_id, position = excluded.position
				returning id""").param("cid", contentId).param("lang", languageId).param("pos", position)
				.query(UUID.class).single();
	}

	void upsertSkillName(UUID skillId, String locale, String name) {
		jdbc.sql("""
				insert into skill_translation (skill_id, locale, name) values (:id, :locale, :name)
				on conflict (skill_id, locale) do update set name = excluded.name""")
				.param("id", skillId).param("locale", locale).param("name", name).update();
	}

	/** Reemplaza por completo los prerrequisitos de las habilidades de un lenguaje. */
	void replacePrerequisites(UUID languageId, Map<UUID, List<UUID>> requiresBySkill) {
		jdbc.sql("""
				delete from skill_prerequisite
				where skill_id in (select id from skill where language_id = :lang)""")
				.param("lang", languageId).update();
		requiresBySkill.forEach((skill, requires) -> requires.forEach(required -> jdbc.sql(
				"insert into skill_prerequisite (skill_id, prerequisite_id) values (:s, :p)")
				.param("s", skill).param("p", required).update()));
	}

	// ---- Curso ----

	UUID upsertCourse(String contentId, UUID languageId, int position) {
		return jdbc.sql("""
				insert into course (content_id, language_id, position) values (:cid, :lang, :pos)
				on conflict (content_id) do update
				set language_id = excluded.language_id, position = excluded.position, archived = false,
				    updated_at = now()
				returning id""").param("cid", contentId).param("lang", languageId).param("pos", position)
				.query(UUID.class).single();
	}

	void upsertCourseText(UUID courseId, String locale, String title, String description) {
		jdbc.sql("""
				insert into course_translation (course_id, locale, title, description)
				values (:id, :locale, :title, :description)
				on conflict (course_id, locale) do update
				set title = excluded.title, description = excluded.description""")
				.param("id", courseId).param("locale", locale).param("title", title)
				.param("description", description).update();
	}

	void archiveCourse(String contentId) {
		jdbc.sql("update course set archived = true, updated_at = now() where content_id = :cid and not archived")
				.param("cid", contentId).update();
	}

	// ---- Unidades ----

	UUID upsertUnit(String contentId, UUID courseId, int position) {
		return jdbc.sql("""
				insert into unit (content_id, course_id, position) values (:cid, :course, :pos)
				on conflict (content_id) do update
				set course_id = excluded.course_id, position = excluded.position, archived = false,
				    updated_at = now()
				returning id""").param("cid", contentId).param("course", courseId).param("pos", position)
				.query(UUID.class).single();
	}

	void upsertUnitText(UUID unitId, String locale, String title, String summary) {
		jdbc.sql("""
				insert into unit_translation (unit_id, locale, title, summary) values (:id, :locale, :title, :summary)
				on conflict (unit_id, locale) do update set title = excluded.title, summary = excluded.summary""")
				.param("id", unitId).param("locale", locale).param("title", title).param("summary", summary).update();
	}

	List<String> activeUnitContentIds(UUID courseId) {
		return jdbc.sql("select content_id from unit where course_id = :course and not archived")
				.param("course", courseId).query(String.class).list();
	}

	void archiveUnit(String contentId) {
		jdbc.sql("update unit set archived = true, updated_at = now() where content_id = :cid and not archived")
				.param("cid", contentId).update();
	}

	// ---- Lecciones ----

	Optional<ExistingLesson> findLesson(String contentId) {
		return jdbc.sql("select id, content_hash, unit_id, status from lesson where content_id = :cid")
				.param("cid", contentId)
				.query((rs, n) -> new ExistingLesson(rs.getObject("id", UUID.class), rs.getString("content_hash"),
						rs.getObject("unit_id", UUID.class), rs.getString("status")))
				.optional();
	}

	UUID upsertLesson(String contentId, UUID unitId, int position, String status, String hash) {
		return jdbc.sql("""
				insert into lesson (content_id, unit_id, position, status, content_hash)
				values (:cid, :unit, :pos, :status, :hash)
				on conflict (content_id) do update
				set unit_id = excluded.unit_id, position = excluded.position, status = excluded.status,
				    content_hash = excluded.content_hash, updated_at = now()
				returning id""").param("cid", contentId).param("unit", unitId).param("pos", position)
				.param("status", status).param("hash", hash).query(UUID.class).single();
	}

	void upsertLessonText(UUID lessonId, String locale, String title, String body) {
		jdbc.sql("""
				insert into lesson_translation (lesson_id, locale, title, body_md) values (:id, :locale, :title, :body)
				on conflict (lesson_id, locale) do update set title = excluded.title, body_md = excluded.body_md""")
				.param("id", lessonId).param("locale", locale).param("title", title).param("body", body).update();
	}

	void replaceLessonSkills(UUID lessonId, List<UUID> skillIds) {
		jdbc.sql("delete from lesson_skill where lesson_id = :id").param("id", lessonId).update();
		skillIds.forEach(skill -> jdbc.sql("insert into lesson_skill (lesson_id, skill_id) values (:l, :s)")
				.param("l", lessonId).param("s", skill).update());
	}

	// ---- Ejercicios ----

	UUID upsertExercise(String contentId, UUID lessonId, int position, String type, int difficulty, String configJson,
			String solutionJson) {
		return jdbc.sql("""
				insert into exercise (content_id, lesson_id, position, type, difficulty, config, solution)
				values (:cid, :lesson, :pos, :type, :difficulty, cast(:config as jsonb), cast(:solution as jsonb))
				on conflict (content_id) do update
				set lesson_id = excluded.lesson_id, position = excluded.position, type = excluded.type,
				    difficulty = excluded.difficulty, config = excluded.config, solution = excluded.solution,
				    archived = false, updated_at = now()
				returning id""").param("cid", contentId).param("lesson", lessonId).param("pos", position)
				.param("type", type).param("difficulty", difficulty).param("config", configJson)
				.param("solution", solutionJson).query(UUID.class).single();
	}

	void upsertExerciseText(UUID exerciseId, String locale, String prompt, String explanation, String optionsJson,
			String hintsJson) {
		jdbc.sql("""
				insert into exercise_translation (exercise_id, locale, prompt, explanation, options, hints)
				values (:id, :locale, :prompt, :explanation, cast(:options as jsonb), cast(:hints as jsonb))
				on conflict (exercise_id, locale) do update
				set prompt = excluded.prompt, explanation = excluded.explanation, options = excluded.options,
				    hints = excluded.hints""").param("id", exerciseId).param("locale", locale)
				.param("prompt", prompt).param("explanation", explanation).param("options", optionsJson)
				.param("hints", hintsJson).update();
	}

	void replaceExerciseSkills(UUID exerciseId, List<UUID> skillIds) {
		jdbc.sql("delete from exercise_skill where exercise_id = :id").param("id", exerciseId).update();
		skillIds.forEach(skill -> jdbc.sql("insert into exercise_skill (exercise_id, skill_id) values (:e, :s)")
				.param("e", exerciseId).param("s", skill).update());
	}

	/** Archiva los ejercicios de una lección que ya no están en su contenido. */
	void archiveExercisesNotIn(UUID lessonId, List<String> keepContentIds) {
		for (String contentId : jdbc.sql("select content_id from exercise where lesson_id = :id and not archived")
				.param("id", lessonId).query(String.class).list()) {
			if (!keepContentIds.contains(contentId)) {
				jdbc.sql("update exercise set archived = true, updated_at = now() where content_id = :cid")
						.param("cid", contentId).update();
			}
		}
	}

	/** Archiva todos los ejercicios de una lección (cuando la lección se archiva). */
	void archiveExercisesOfLesson(String lessonContentId) {
		jdbc.sql("""
				update exercise set archived = true, updated_at = now()
				where not archived and lesson_id = (select id from lesson where content_id = :cid)""")
				.param("cid", lessonContentId).update();
	}

	List<String> activeLessonContentIds(UUID courseId) {
		return jdbc.sql("""
				select l.content_id from lesson l join unit u on u.id = l.unit_id
				where u.course_id = :course and l.status <> 'ARCHIVED'""").param("course", courseId)
				.query(String.class).list();
	}

	void archiveLesson(String contentId) {
		jdbc.sql("update lesson set status = 'ARCHIVED', updated_at = now() where content_id = :cid and status <> 'ARCHIVED'")
				.param("cid", contentId).update();
		archiveExercisesOfLesson(contentId);
	}

	Optional<UUID> findCourseId(String contentId) {
		return jdbc.sql("select id from course where content_id = :cid").param("cid", contentId)
				.query(UUID.class).optional();
	}

}
