package app.plataforma.api.catalog.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import app.plataforma.api.catalog.internal.CatalogDtos.CourseDetail;
import app.plataforma.api.catalog.internal.CatalogDtos.CourseSummary;
import app.plataforma.api.catalog.internal.CatalogDtos.LessonDetail;
import app.plataforma.api.catalog.internal.CatalogDtos.LessonLink;
import app.plataforma.api.catalog.internal.CatalogDtos.LessonOutline;
import app.plataforma.api.catalog.internal.CatalogDtos.SkillNode;
import app.plataforma.api.catalog.internal.CatalogDtos.SkillRef;
import app.plataforma.api.catalog.internal.CatalogDtos.UnitOutline;

/**
 * Lecturas del catálogo. Cada texto se pide en el idioma solicitado y, si falta, en español
 * ({@code coalesce(solicitado, es)}). Lo archivado nunca se devuelve.
 */
@Repository
class CatalogQueries {

	private final JdbcClient jdbc;

	CatalogQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<CourseSummary> courses(String locale) {
		return jdbc.sql("""
				select c.content_id, l.code, l.name,
				       coalesce(tr.title, tf.title) as title,
				       coalesce(tr.description, tf.description) as description,
				       (select count(distinct u.id) from unit u join lesson le on le.unit_id = u.id
				         where u.course_id = c.id and not u.archived and le.status <> 'ARCHIVED') as unit_count,
				       (select count(*) from unit u join lesson le on le.unit_id = u.id
				         where u.course_id = c.id and not u.archived and le.status <> 'ARCHIVED') as lesson_count
				from course c
				join programming_language l on l.id = c.language_id
				left join course_translation tr on tr.course_id = c.id and tr.locale = :locale
				left join course_translation tf on tf.course_id = c.id and tf.locale = 'es'
				where not c.archived
				order by c.position""").param("locale", locale)
				.query((rs, n) -> new CourseSummary(rs.getString("content_id"), rs.getString("code"),
						rs.getString("name"), rs.getString("title"), rs.getString("description"),
						rs.getInt("unit_count"), rs.getInt("lesson_count")))
				.list();
	}

	Optional<CourseDetail> course(String contentId, String locale) {
		record CourseRow(UUID id, String contentId, String code, String name, String title, String description) {
		}
		Optional<CourseRow> row = jdbc.sql("""
				select c.id, c.content_id, l.code, l.name,
				       coalesce(tr.title, tf.title) as title,
				       coalesce(tr.description, tf.description) as description
				from course c
				join programming_language l on l.id = c.language_id
				left join course_translation tr on tr.course_id = c.id and tr.locale = :locale
				left join course_translation tf on tf.course_id = c.id and tf.locale = 'es'
				where c.content_id = :cid and not c.archived""").param("locale", locale).param("cid", contentId)
				.query((rs, n) -> new CourseRow(rs.getObject("id", UUID.class), rs.getString("content_id"),
						rs.getString("code"), rs.getString("name"), rs.getString("title"),
						rs.getString("description")))
				.optional();
		if (row.isEmpty()) {
			return Optional.empty();
		}
		CourseRow course = row.get();
		Map<UUID, List<SkillRef>> skillsByLesson = lessonSkills(course.id(), locale);

		Map<String, UnitOutline> units = new LinkedHashMap<>();
		Map<String, List<LessonOutline>> lessonsByUnit = new HashMap<>();
		jdbc.sql("""
				select u.content_id as unit_id, u.position as unit_position,
				       coalesce(ut.title, uf.title) as unit_title, coalesce(ut.summary, uf.summary) as unit_summary,
				       le.id as lesson_uuid, le.content_id as lesson_id, le.position as lesson_position,
				       le.status as lesson_status, coalesce(lt.title, lf.title) as lesson_title
				from unit u
				left join unit_translation ut on ut.unit_id = u.id and ut.locale = :locale
				left join unit_translation uf on uf.unit_id = u.id and uf.locale = 'es'
				join lesson le on le.unit_id = u.id and le.status <> 'ARCHIVED'
				left join lesson_translation lt on lt.lesson_id = le.id and lt.locale = :locale
				left join lesson_translation lf on lf.lesson_id = le.id and lf.locale = 'es'
				where u.course_id = :course and not u.archived
				order by u.position, le.position""").param("locale", locale).param("course", course.id())
				.query((rs, n) -> {
					String unitId = rs.getString("unit_id");
					if (!units.containsKey(unitId)) {
						units.put(unitId, new UnitOutline(unitId, rs.getInt("unit_position"),
								rs.getString("unit_title"), rs.getString("unit_summary"), List.of()));
					}
					UUID lessonUuid = rs.getObject("lesson_uuid", UUID.class);
					lessonsByUnit.computeIfAbsent(unitId, k -> new ArrayList<>()).add(new LessonOutline(
							rs.getString("lesson_id"), rs.getInt("lesson_position"), rs.getString("lesson_title"),
							rs.getString("lesson_status"), skillsByLesson.getOrDefault(lessonUuid, List.of())));
					return null;
				}).list();

		List<UnitOutline> result = units.values().stream().map(u -> new UnitOutline(u.id(), u.position(), u.title(),
				u.summary(), lessonsByUnit.getOrDefault(u.id(), List.of()))).toList();
		return Optional.of(new CourseDetail(course.contentId(), course.code(), course.name(), course.title(),
				course.description(), result));
	}

	Optional<LessonDetail> lesson(String contentId, String locale) {
		record LessonRow(UUID id, UUID courseUuid, String contentId, int position, String status, String title,
				String body, String unitId, String unitTitle, String courseId, String courseTitle) {
		}
		Optional<LessonRow> row = jdbc.sql("""
				select le.id, le.content_id, le.position, le.status,
				       coalesce(lt.title, lf.title) as title, coalesce(lt.body_md, lf.body_md) as body,
				       u.content_id as unit_cid, coalesce(ut.title, uf.title) as unit_title,
				       c.id as course_uuid, c.content_id as course_cid, coalesce(ct.title, cf.title) as course_title
				from lesson le
				join unit u on u.id = le.unit_id
				join course c on c.id = u.course_id
				left join lesson_translation lt on lt.lesson_id = le.id and lt.locale = :locale
				left join lesson_translation lf on lf.lesson_id = le.id and lf.locale = 'es'
				left join unit_translation ut on ut.unit_id = u.id and ut.locale = :locale
				left join unit_translation uf on uf.unit_id = u.id and uf.locale = 'es'
				left join course_translation ct on ct.course_id = c.id and ct.locale = :locale
				left join course_translation cf on cf.course_id = c.id and cf.locale = 'es'
				where le.content_id = :cid and le.status <> 'ARCHIVED' and not u.archived and not c.archived""")
				.param("locale", locale).param("cid", contentId)
				.query((rs, n) -> new LessonRow(rs.getObject("id", UUID.class),
						rs.getObject("course_uuid", UUID.class), rs.getString("content_id"), rs.getInt("position"),
						rs.getString("status"), rs.getString("title"), rs.getString("body"),
						rs.getString("unit_cid"), rs.getString("unit_title"), rs.getString("course_cid"),
						rs.getString("course_title")))
				.optional();
		if (row.isEmpty()) {
			return Optional.empty();
		}
		LessonRow lesson = row.get();
		List<SkillRef> skills = lessonSkills(lesson.courseUuid(), locale).getOrDefault(lesson.id(), List.of());

		// Orden de lectura de todo el curso, para enlazar con la lección anterior y la siguiente.
		List<LessonLink> order = jdbc.sql("""
				select le.content_id, coalesce(lt.title, lf.title) as title
				from lesson le
				join unit u on u.id = le.unit_id
				left join lesson_translation lt on lt.lesson_id = le.id and lt.locale = :locale
				left join lesson_translation lf on lf.lesson_id = le.id and lf.locale = 'es'
				where u.course_id = :course and not u.archived and le.status <> 'ARCHIVED'
				order by u.position, le.position""").param("locale", locale).param("course", lesson.courseUuid())
				.query((rs, n) -> new LessonLink(rs.getString("content_id"), rs.getString("title"))).list();
		int index = -1;
		for (int i = 0; i < order.size(); i++) {
			if (order.get(i).id().equals(lesson.contentId())) {
				index = i;
			}
		}
		LessonLink previous = index > 0 ? order.get(index - 1) : null;
		LessonLink next = index >= 0 && index < order.size() - 1 ? order.get(index + 1) : null;
		return Optional.of(new LessonDetail(lesson.contentId(), lesson.position(), lesson.title(), lesson.body(),
				lesson.status(), lesson.courseId(), lesson.courseTitle(), lesson.unitId(), lesson.unitTitle(),
				skills, previous, next));
	}

	/** Habilidades del lenguaje de un curso con sus prerrequisitos; vacío si el curso no existe. */
	Optional<List<SkillNode>> skills(String courseContentId, String locale) {
		Optional<UUID> languageId = jdbc.sql("select language_id from course where content_id = :cid and not archived")
				.param("cid", courseContentId).query(UUID.class).optional();
		if (languageId.isEmpty()) {
			return Optional.empty();
		}
		Map<String, List<String>> requires = new HashMap<>();
		jdbc.sql("""
				select s.content_id as skill_id, p.content_id as required_id
				from skill_prerequisite sp
				join skill s on s.id = sp.skill_id
				join skill p on p.id = sp.prerequisite_id
				where s.language_id = :lang order by p.position""").param("lang", languageId.get())
				.query((rs, n) -> {
					requires.computeIfAbsent(rs.getString("skill_id"), k -> new ArrayList<>())
							.add(rs.getString("required_id"));
					return null;
				}).list();
		return Optional.of(jdbc.sql("""
				select s.content_id, coalesce(st.name, sf.name) as name
				from skill s
				left join skill_translation st on st.skill_id = s.id and st.locale = :locale
				left join skill_translation sf on sf.skill_id = s.id and sf.locale = 'es'
				where s.language_id = :lang order by s.position""").param("locale", locale)
				.param("lang", languageId.get())
				.query((rs, n) -> new SkillNode(rs.getString("content_id"), rs.getString("name"),
						requires.getOrDefault(rs.getString("content_id"), List.of())))
				.list());
	}

	private Map<UUID, List<SkillRef>> lessonSkills(UUID courseId, String locale) {
		Map<UUID, List<SkillRef>> result = new HashMap<>();
		jdbc.sql("""
				select ls.lesson_id, s.content_id, coalesce(st.name, sf.name) as name
				from lesson_skill ls
				join skill s on s.id = ls.skill_id
				left join skill_translation st on st.skill_id = s.id and st.locale = :locale
				left join skill_translation sf on sf.skill_id = s.id and sf.locale = 'es'
				where ls.lesson_id in (select le.id from lesson le join unit u on u.id = le.unit_id
				                       where u.course_id = :course)
				order by s.position""").param("locale", locale).param("course", courseId)
				.query((rs, n) -> {
					result.computeIfAbsent(rs.getObject("lesson_id", UUID.class), k -> new ArrayList<>())
							.add(new SkillRef(rs.getString("content_id"), rs.getString("name")));
					return null;
				}).list();
		return result;
	}

}
