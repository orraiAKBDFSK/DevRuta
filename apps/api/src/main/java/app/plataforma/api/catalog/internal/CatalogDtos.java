package app.plataforma.api.catalog.internal;

import java.util.List;

/** Respuestas del catálogo. Los identificadores son los {@code content_id} estables del contenido (p. ej. {@code py.u01.l01}). */
final class CatalogDtos {

	private CatalogDtos() {
	}

	record SkillRef(String id, String name) {
	}

	record CourseSummary(String id, String languageCode, String languageName, String title, String description,
			int unitCount, int lessonCount) {
	}

	record LessonOutline(String id, int position, String title, String status, List<SkillRef> skills) {
	}

	record UnitOutline(String id, int position, String title, String summary, List<LessonOutline> lessons) {
	}

	record CourseDetail(String id, String languageCode, String languageName, String title, String description,
			List<UnitOutline> units) {
	}

	record LessonLink(String id, String title) {
	}

	record LessonDetail(String id, int position, String title, String bodyMarkdown, String status, String courseId,
			String courseTitle, String unitId, String unitTitle, List<SkillRef> skills, LessonLink previous,
			LessonLink next) {
	}

	record SkillNode(String id, String name, List<String> requires) {
	}

}
