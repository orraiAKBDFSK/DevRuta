package app.plataforma.api.catalog.internal;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.transaction.annotation.Transactional;

import app.plataforma.api.catalog.internal.ContentModel.Exercise;
import app.plataforma.api.catalog.internal.ContentModel.LanguageContent;
import app.plataforma.api.catalog.internal.ContentModel.Lesson;
import app.plataforma.api.catalog.internal.ContentModel.Skill;
import app.plataforma.api.catalog.internal.ContentModel.Unit;

/**
 * Carga el contenido de {@code content/} en el catálogo. Es idempotente (importar dos veces deja lo mismo) y atómica:
 * si el contenido tiene algún error no se escribe nada. Nunca borra: lo que desaparece del contenido, o ya no alcanza
 * el estado mínimo, se archiva.
 */
@Service
class ContentImporter {

	private static final List<String> LOCALES = List.of("es", "en");

	private final ContentRepository repository;

	private final JsonMapper jsonMapper;

	ContentImporter(ContentRepository repository, JsonMapper jsonMapper) {
		this.repository = repository;
		this.jsonMapper = jsonMapper;
	}

	@Transactional
	ImportReport importFrom(Path root, ContentStatus minStatus) {
		ContentLoader.LoadResult loaded = new ContentLoader().load(root);
		if (!loaded.problems().isEmpty()) {
			throw new ContentValidationException(loaded.problems());
		}
		Counters counters = new Counters();
		for (LanguageContent language : loaded.languages()) {
			importLanguage(language, minStatus, counters);
		}
		return counters.report();
	}

	private void importLanguage(LanguageContent content, ContentStatus minStatus, Counters counters) {
		List<Lesson> lessons = content.lessons().stream().filter(l -> l.status().atLeast(minStatus)).toList();
		counters.skippedByStatus += content.lessons().size() - lessons.size();

		UUID languageId = repository.upsertLanguage(content.course().languageCode(), content.course().languageName());
		Map<String, UUID> skillIds = importSkills(content, languageId);
		counters.skills += content.skills().size();

		String courseContentId = content.course().id();
		if (lessons.isEmpty()) {
			// Nada que mostrar todavía (por ejemplo, producción sin lecciones revisadas): el curso queda archivado.
			repository.findCourseId(courseContentId).ifPresent(courseId -> archiveMissing(courseId, Set.of(), Set.of(),
					counters));
			repository.archiveCourse(courseContentId);
			return;
		}

		UUID courseId = repository.upsertCourse(courseContentId, languageId, content.course().position());
		for (String locale : LOCALES) {
			repository.upsertCourseText(courseId, locale, content.course().title().get(locale),
					content.course().description().get(locale));
		}
		counters.courses++;

		Set<String> unitsWithLessons = new HashSet<>();
		lessons.forEach(l -> unitsWithLessons.add(l.unitId()));
		Map<String, UUID> unitIds = new HashMap<>();
		for (Unit unit : content.units()) {
			if (!unitsWithLessons.contains(unit.id())) {
				continue;
			}
			UUID unitId = repository.upsertUnit(unit.id(), courseId, unit.position());
			unitIds.put(unit.id(), unitId);
			for (String locale : LOCALES) {
				repository.upsertUnitText(unitId, locale, unit.title().get(locale), unit.summary().get(locale));
			}
			counters.units++;
		}

		Set<String> importedLessonIds = new HashSet<>();
		for (Lesson lesson : lessons) {
			importLesson(lesson, unitIds.get(lesson.unitId()), skillIds, counters);
			importedLessonIds.add(lesson.id());
		}
		archiveMissing(courseId, unitsWithLessons, importedLessonIds, counters);
	}

	private Map<String, UUID> importSkills(LanguageContent content, UUID languageId) {
		Map<String, UUID> ids = new HashMap<>();
		for (Skill skill : content.skills()) {
			UUID id = repository.upsertSkill(skill.id(), languageId, skill.position());
			ids.put(skill.id(), id);
			for (String locale : LOCALES) {
				repository.upsertSkillName(id, locale, skill.name().get(locale));
			}
		}
		Map<UUID, List<UUID>> requires = new HashMap<>();
		for (Skill skill : content.skills()) {
			List<UUID> required = new ArrayList<>();
			skill.requires().forEach(r -> required.add(ids.get(r)));
			requires.put(ids.get(skill.id()), required);
		}
		repository.replacePrerequisites(languageId, requires);
		return ids;
	}

	private void importLesson(Lesson lesson, UUID unitId, Map<String, UUID> skillIds, Counters counters) {
		var existing = repository.findLesson(lesson.id());
		if (existing.isPresent() && existing.get().hash().equals(lesson.hash())
				&& existing.get().unitId().equals(unitId) && !"ARCHIVED".equals(existing.get().status())) {
			counters.lessonsUnchanged++;
			return;
		}
		UUID lessonId = repository.upsertLesson(lesson.id(), unitId, lesson.position(), lesson.status().name(),
				lesson.hash());
		for (String locale : LOCALES) {
			repository.upsertLessonText(lessonId, locale, lesson.title().get(locale), lesson.body().get(locale));
		}
		repository.replaceLessonSkills(lessonId, lesson.skills().stream().map(skillIds::get).toList());
		importExercises(lessonId, lesson, skillIds);
		counters.exercises += lesson.exercises().size();
		if (existing.isPresent()) {
			counters.lessonsUpdated++;
		}
		else {
			counters.lessonsCreated++;
		}
	}

	private void importExercises(UUID lessonId, Lesson lesson, Map<String, UUID> skillIds) {
		List<String> keep = new ArrayList<>();
		for (Exercise exercise : lesson.exercises()) {
			UUID id = repository.upsertExercise(exercise.id(), lessonId, exercise.position(), exercise.type().name(),
					exercise.difficulty(), json(exercise.config()), json(exercise.solution()));
			for (String locale : LOCALES) {
				List<String> options = exercise.options().stream().map(o -> o.get(locale)).toList();
				List<String> hints = exercise.hints().stream().map(h -> h.get(locale)).toList();
				repository.upsertExerciseText(id, locale, exercise.prompt().get(locale),
						exercise.explanation().get(locale), json(options), json(hints));
			}
			repository.replaceExerciseSkills(id, exercise.skills().stream().map(skillIds::get).toList());
			keep.add(exercise.id());
		}
		repository.archiveExercisesNotIn(lessonId, keep);
	}

	private String json(Object value) {
		return jsonMapper.writeValueAsString(value);
	}

	/** Archiva lo que existe en la base de datos y ya no está en el contenido que se acaba de importar. */
	private void archiveMissing(UUID courseId, Set<String> keepUnits, Set<String> keepLessons, Counters counters) {
		for (String contentId : repository.activeLessonContentIds(courseId)) {
			if (!keepLessons.contains(contentId)) {
				repository.archiveLesson(contentId);
				counters.lessonsArchived++;
			}
		}
		for (String contentId : repository.activeUnitContentIds(courseId)) {
			if (!keepUnits.contains(contentId)) {
				repository.archiveUnit(contentId);
			}
		}
	}

	private static final class Counters {

		int courses;

		int units;

		int skills;

		int lessonsCreated;

		int lessonsUpdated;

		int lessonsUnchanged;

		int lessonsArchived;

		int skippedByStatus;

		int exercises;

		ImportReport report() {
			return new ImportReport(courses, units, skills, lessonsCreated, lessonsUpdated, lessonsUnchanged,
					lessonsArchived, skippedByStatus, exercises);
		}

	}

}
