package app.plataforma.api.catalog.internal;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import app.plataforma.api.catalog.ExerciseType;
import app.plataforma.api.catalog.internal.ContentModel.Course;
import app.plataforma.api.catalog.internal.ContentModel.Exercise;
import app.plataforma.api.catalog.internal.ContentModel.LanguageContent;
import app.plataforma.api.catalog.internal.ContentModel.Lesson;
import app.plataforma.api.catalog.internal.ContentModel.Skill;
import app.plataforma.api.catalog.internal.ContentModel.Texts;
import app.plataforma.api.catalog.internal.ContentModel.Unit;

/**
 * Lee y valida el contenido de {@code content/} (formato descrito en {@code content/SCHEMA.md}). No toca la base de
 * datos. Acumula TODOS los problemas encontrados en lugar de parar en el primero, para poder corregirlos de una vez.
 * Solo valida la estructura del catálogo; los ejercicios los valida {@code scripts/validate_content.py}.
 */
class ContentLoader {

	record LoadResult(List<LanguageContent> languages, List<String> problems) {
	}

	private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,79}");

	private static final List<String> LOCALES = List.of("es", "en");

	private static final int MAX_BODY_LENGTH = 100_000;

	private static final Set<String> META_FILES = Set.of("course.yaml", "skills.yaml", "unit.yaml");

	private final List<String> problems = new ArrayList<>();

	private Path root;

	LoadResult load(Path contentRoot) {
		this.root = contentRoot;
		List<LanguageContent> languages = new ArrayList<>();
		if (!Files.isDirectory(contentRoot)) {
			problems.add("La carpeta de contenido no existe: " + contentRoot);
			return new LoadResult(languages, problems);
		}
		for (Path dir : children(contentRoot, true)) {
			if (Files.exists(dir.resolve("course.yaml"))) {
				LanguageContent language = loadLanguage(dir);
				if (language != null) {
					languages.add(language);
				}
			}
		}
		Set<String> languageCodes = new HashSet<>();
		for (LanguageContent l : languages) {
			if (!languageCodes.add(l.course().languageCode())) {
				problems.add("Código de lenguaje repetido: " + l.course().languageCode());
			}
		}
		return new LoadResult(languages, problems);
	}

	private LanguageContent loadLanguage(Path dir) {
		int before = problems.size();
		Course course = loadCourse(dir.resolve("course.yaml"));
		List<Skill> skills = loadSkills(dir.resolve("skills.yaml"));
		List<Unit> units = new ArrayList<>();
		List<Lesson> lessons = new ArrayList<>();
		for (Path unitDir : children(dir, true)) {
			if (!Files.exists(unitDir.resolve("unit.yaml"))) {
				continue;
			}
			Unit unit = loadUnit(unitDir.resolve("unit.yaml"));
			if (unit == null) {
				continue;
			}
			units.add(unit);
			try (Stream<Path> files = Files.list(unitDir)) {
				for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".yaml"))
						.filter(f -> !META_FILES.contains(f.getFileName().toString()))
						.sorted().toList()) {
					Lesson lesson = loadLesson(file, unit.id());
					if (lesson != null) {
						lessons.add(lesson);
					}
				}
			}
			catch (IOException ex) {
				problems.add(rel(unitDir) + ": no se puede leer la carpeta (" + ex.getMessage() + ")");
			}
		}
		if (course == null) {
			return null;
		}
		crossValidate(rel(dir), skills, units, lessons);
		return problems.size() > before ? null : new LanguageContent(course, skills, units, lessons);
	}

	// ---- Archivos individuales ----

	private Course loadCourse(Path file) {
		Map<String, Object> m = readMap(file);
		if (m == null) {
			return null;
		}
		String where = rel(file);
		String id = id(m, "id", where);
		String language = id(m, "language", where);
		String languageName = text(m, "language_name", where);
		Integer position = integer(m, "position", where);
		Texts title = texts(m, "title", where);
		Texts description = texts(m, "description", where);
		return id == null || language == null || languageName == null || position == null || title == null
				|| description == null ? null : new Course(id, language, languageName, position, title, description);
	}

	private List<Skill> loadSkills(Path file) {
		Map<String, Object> m = readMap(file);
		List<Skill> skills = new ArrayList<>();
		if (m == null) {
			return skills;
		}
		String where = rel(file);
		Object raw = m.get("skills");
		if (!(raw instanceof List<?> list)) {
			problems.add(where + ": falta la lista 'skills'");
			return skills;
		}
		int position = 0;
		for (Object item : list) {
			position++;
			Map<String, Object> s = asMap(item, where + " (habilidad " + position + ")");
			if (s == null) {
				continue;
			}
			String id = id(s, "id", where);
			Texts name = texts(s, "name", where + "::" + id);
			List<String> requires = strings(s, "requires", where + "::" + id, true);
			if (id != null && name != null && requires != null) {
				skills.add(new Skill(id, name, requires, position));
			}
		}
		return skills;
	}

	private Unit loadUnit(Path file) {
		Map<String, Object> m = readMap(file);
		if (m == null) {
			return null;
		}
		String where = rel(file);
		String id = id(m, "id", where);
		Integer position = integer(m, "position", where);
		Texts title = texts(m, "title", where);
		Texts summary = texts(m, "summary", where);
		return id == null || position == null || title == null || summary == null ? null
				: new Unit(id, position, title, summary);
	}

	private Lesson loadLesson(Path file, String folderUnitId) {
		Map<String, Object> m = readMap(file);
		if (m == null) {
			return null;
		}
		String where = rel(file);
		String id = id(m, "id", where);
		String unitId = id(m, "unit", where);
		Integer position = integer(m, "position", where);
		Texts title = texts(m, "title", where);
		Texts body = texts(m, "explanation", where);
		List<String> skills = strings(m, "skills", where, false);
		String statusText = text(m, "status", where);
		ContentStatus status = null;
		if (statusText != null) {
			try {
				status = ContentStatus.parse(statusText);
			}
			catch (IllegalArgumentException ex) {
				problems.add(where + ": status desconocido '" + statusText + "' (draft, reviewed o published)");
			}
		}
		if (status == ContentStatus.PUBLISHED) {
			Object reviewers = m.get("reviewed_by");
			if (!(reviewers instanceof List<?> list) || list.isEmpty()) {
				problems.add(where + ": una lección 'published' necesita 'reviewed_by' (revisión humana)");
			}
		}
		if (unitId != null && folderUnitId != null && !unitId.equals(folderUnitId)) {
			problems.add(where + ": declara la unidad " + unitId + " pero su carpeta es la de " + folderUnitId);
		}
		if (body != null) {
			for (String locale : LOCALES) {
				if (body.get(locale).length() > MAX_BODY_LENGTH) {
					problems.add(where + ": el texto '" + locale + "' supera " + MAX_BODY_LENGTH + " caracteres");
				}
			}
		}
		List<Exercise> exercises = loadExercises(m, where);
		if (id == null || unitId == null || position == null || title == null || body == null || skills == null
				|| status == null || exercises == null) {
			return null;
		}
		return new Lesson(id, unitId, position, status, title, body, skills, exercises,
				hash(unitId, position, status, skills, title, body, exercises));
	}

	// ---- Ejercicios ----

	private List<Exercise> loadExercises(Map<String, Object> lesson, String where) {
		if (!(lesson.get("exercises") instanceof List<?> list)) {
			problems.add(where + ": falta la lista 'exercises'");
			return null;
		}
		List<Exercise> result = new ArrayList<>();
		int position = 0;
		for (Object item : list) {
			position++;
			Map<String, Object> m = asMap(item, where + " (ejercicio " + position + ")");
			Exercise exercise = m == null ? null : loadExercise(m, position, where);
			if (exercise != null) {
				result.add(exercise);
			}
		}
		return result;
	}

	private Exercise loadExercise(Map<String, Object> m, int position, String lessonWhere) {
		String id = id(m, "id", lessonWhere + " (ejercicio " + position + ")");
		String where = lessonWhere + "::" + (id == null ? "ejercicio " + position : id);
		ExerciseType type = null;
		String typeText = text(m, "type", where);
		if (typeText != null) {
			try {
				type = ExerciseType.fromContent(typeText);
			}
			catch (IllegalArgumentException ex) {
				problems.add(where + ": tipo de ejercicio desconocido '" + typeText + "'");
			}
		}
		Integer difficulty = m.get("difficulty") instanceof Integer d && d >= 1 && d <= 5 ? d : null;
		if (difficulty == null) {
			problems.add(where + ": 'difficulty' debe ser un entero entre 1 y 5");
		}
		Texts prompt = texts(m, "prompt", where);
		Texts explanation = texts(m, "explanation", where);
		List<Texts> hints = textsList(m, "hints", where, 1);
		List<String> skills = strings(m, "skills", where, false);
		Map<String, Object> config = new LinkedHashMap<>();
		Map<String, Object> solution = new LinkedHashMap<>();
		List<Texts> options = List.of();
		boolean typeOk = false;
		if (type != null) {
			switch (type) {
				case MULTIPLE_CHOICE -> {
					options = textsList(m, "options", where, 2);
					typeOk = options != null && multipleChoice(m, options.size(), solution, where);
				}
				case PREDICT_OUTPUT -> typeOk = predictOutput(m, config, solution, where);
				case ORDER_LINES -> typeOk = orderLines(m, config, solution, where);
				case FILL_CODE -> typeOk = fillCode(m, config, solution, where);
				case WRITE_CODE -> typeOk = writeCode(m, config, solution, where);
			}
		}
		if (id == null || type == null || difficulty == null || prompt == null || explanation == null || hints == null
				|| skills == null || !typeOk || options == null) {
			return null;
		}
		return new Exercise(id, position, type, difficulty, prompt, explanation, hints, options, config, solution,
				skills);
	}

	private boolean multipleChoice(Map<String, Object> m, int optionCount, Map<String, Object> solution, String where) {
		if (!(m.get("answer") instanceof Integer answer) || answer < 0 || answer >= optionCount) {
			problems.add(where + ": 'answer' debe ser el índice (desde 0) de una de las opciones");
			return false;
		}
		Map<String, String> tags = new LinkedHashMap<>();
		if (m.get("distractor_tags") instanceof Map<?, ?> raw) {
			for (Map.Entry<?, ?> entry : raw.entrySet()) {
				if (entry.getKey() instanceof Integer index && index >= 0 && index < optionCount
						&& entry.getValue() instanceof String tag && !tag.isBlank()) {
					tags.put(String.valueOf(index), tag);
				}
				else {
					problems.add(where + ": 'distractor_tags' tiene una entrada inválida: " + entry);
					return false;
				}
			}
		}
		solution.put("answer", answer);
		solution.put("distractorTags", tags);
		return true;
	}

	private boolean predictOutput(Map<String, Object> m, Map<String, Object> config, Map<String, Object> solution,
			String where) {
		String code = text(m, "code", where);
		if (!(m.get("answer") instanceof String answer)) {
			problems.add(where + ": 'answer' debe ser un texto (escríbelo entre comillas)");
			return false;
		}
		if (code == null) {
			return false;
		}
		config.put("code", code);
		solution.put("answer", answer);
		return true;
	}

	private boolean orderLines(Map<String, Object> m, Map<String, Object> config, Map<String, Object> solution,
			String where) {
		List<String> lines = strings(m, "lines", where, false);
		if (lines == null) {
			return false;
		}
		if (lines.size() < 2) {
			problems.add(where + ": 'lines' necesita al menos 2 líneas");
			return false;
		}
		config.put("lineCount", lines.size());
		solution.put("lines", lines);
		return true;
	}

	private boolean fillCode(Map<String, Object> m, Map<String, Object> config, Map<String, Object> solution,
			String where) {
		String code = text(m, "code", where);
		if (!(m.get("blanks") instanceof List<?> raw) || raw.isEmpty()) {
			problems.add(where + ": 'blanks' debe ser una lista con las alternativas de cada hueco");
			return false;
		}
		List<List<String>> blanks = new ArrayList<>();
		for (Object item : raw) {
			if (!(item instanceof List<?> alternatives) || alternatives.isEmpty()
					|| !alternatives.stream().allMatch(a -> a instanceof String s && !s.isBlank())) {
				problems.add(where + ": cada hueco de 'blanks' debe ser una lista de textos no vacíos");
				return false;
			}
			blanks.add(alternatives.stream().map(String::valueOf).toList());
		}
		if (code == null) {
			return false;
		}
		int holes = code.split("___", -1).length - 1;
		if (holes != blanks.size()) {
			problems.add(where + ": el código tiene " + holes + " huecos (___) y 'blanks' tiene " + blanks.size());
			return false;
		}
		config.put("code", code);
		config.put("blankCount", holes);
		solution.put("blanks", blanks);
		return true;
	}

	private boolean writeCode(Map<String, Object> m, Map<String, Object> config, Map<String, Object> solution,
			String where) {
		String starter = text(m, "starter", where);
		String reference = text(m, "reference_solution", where);
		if (!(m.get("tests") instanceof List<?> rawTests) || rawTests.isEmpty()) {
			problems.add(where + ": 'tests' debe ser una lista con al menos un caso");
			return false;
		}
		List<Map<String, Object>> tests = new ArrayList<>();
		for (Object item : rawTests) {
			if (!(item instanceof Map<?, ?> t) || !(t.get("stdin") instanceof String stdin)
					|| !(t.get("expected_stdout") instanceof String expected)) {
				problems.add(where + ": cada test necesita 'stdin' y 'expected_stdout' como texto");
				return false;
			}
			Map<String, Object> test = new LinkedHashMap<>();
			test.put("stdin", stdin);
			test.put("expectedStdout", expected);
			test.put("visible", Boolean.TRUE.equals(t.get("visible")));
			tests.add(test);
		}
		if (starter == null || reference == null) {
			return false;
		}
		List<Object> staticChecks = m.get("static_checks") instanceof List<?> checks ? new ArrayList<>(checks)
				: List.of();
		config.put("starter", starter);
		solution.put("referenceSolution", reference);
		solution.put("tests", tests);
		solution.put("staticChecks", staticChecks);
		return true;
	}

	private List<Texts> textsList(Map<String, Object> m, String key, String where, int minimum) {
		if (!(m.get(key) instanceof List<?> list) || list.size() < minimum) {
			problems.add(where + ": '" + key + "' debe ser una lista con al menos " + minimum + " elemento(s)");
			return null;
		}
		List<Texts> result = new ArrayList<>();
		int n = 0;
		for (Object item : list) {
			n++;
			Texts texts = texts(Map.of("v", item), "v", where + " (" + key + " " + n + ")");
			if (texts == null) {
				return null;
			}
			result.add(texts);
		}
		return result;
	}

	// ---- Reglas que cruzan archivos ----

	private void crossValidate(String where, List<Skill> skills, List<Unit> units, List<Lesson> lessons) {
		Set<String> skillIds = new HashSet<>();
		for (Skill s : skills) {
			if (!skillIds.add(s.id())) {
				problems.add(where + "/skills.yaml: habilidad repetida " + s.id());
			}
		}
		Map<String, List<String>> requires = new LinkedHashMap<>();
		for (Skill s : skills) {
			requires.put(s.id(), s.requires());
			for (String r : s.requires()) {
				if (!skillIds.contains(r)) {
					problems.add(where + "/skills.yaml: " + s.id() + " requiere una habilidad inexistente: " + r);
				}
			}
		}
		List<String> cycle = findCycle(requires);
		if (cycle != null) {
			problems.add(where + "/skills.yaml: ciclo de prerrequisitos: " + String.join(" -> ", cycle));
		}

		Set<String> unitIds = new HashSet<>();
		for (Unit u : units) {
			if (!unitIds.add(u.id())) {
				problems.add(where + ": unidad repetida " + u.id());
			}
		}
		List<Integer> unitPositions = units.stream().map(Unit::position).sorted().toList();
		if (!unitPositions.equals(sequence(units.size()))) {
			problems.add(where + ": las posiciones de las unidades deben ser 1.." + units.size() + " y son "
					+ unitPositions);
		}

		Set<String> lessonIds = new HashSet<>();
		Set<String> allExerciseIds = new HashSet<>();
		Map<String, List<Integer>> positionsByUnit = new HashMap<>();
		for (Lesson l : lessons) {
			if (!lessonIds.add(l.id())) {
				problems.add(where + ": lección repetida " + l.id());
			}
			if (!unitIds.contains(l.unitId())) {
				problems.add(where + ": la lección " + l.id() + " usa la unidad inexistente " + l.unitId());
			}
			positionsByUnit.computeIfAbsent(l.unitId(), k -> new ArrayList<>()).add(l.position());
			for (String s : l.skills()) {
				if (!skillIds.contains(s)) {
					problems.add(where + ": la lección " + l.id() + " usa la habilidad inexistente " + s);
				}
			}
			for (Exercise e : l.exercises()) {
				if (!allExerciseIds.add(e.id())) {
					problems.add(where + ": ejercicio repetido " + e.id() + " (lección " + l.id() + ")");
				}
				for (String s : e.skills()) {
					if (!skillIds.contains(s)) {
						problems.add(where + ": el ejercicio " + e.id() + " usa la habilidad inexistente " + s);
					}
				}
			}
		}
		for (Unit u : units) {
			List<Integer> positions = positionsByUnit.getOrDefault(u.id(), List.of()).stream().sorted().toList();
			if (positions.isEmpty()) {
				problems.add(where + ": la unidad " + u.id() + " no tiene lecciones");
			}
			else if (!positions.equals(sequence(positions.size()))) {
				problems.add(where + ": las posiciones de las lecciones de " + u.id() + " deben ser 1.."
						+ positions.size() + " y son " + positions);
			}
		}
	}

	private static List<Integer> sequence(int size) {
		return java.util.stream.IntStream.rangeClosed(1, size).boxed().toList();
	}

	/** Devuelve un ciclo de prerrequisitos (la lista termina donde empieza) o null si el grafo es acíclico. */
	static List<String> findCycle(Map<String, List<String>> requires) {
		Map<String, Integer> state = new HashMap<>();
		List<String> path = new ArrayList<>();
		for (String node : requires.keySet()) {
			if (!state.containsKey(node)) {
				List<String> found = visit(node, requires, state, path);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static List<String> visit(String node, Map<String, List<String>> requires, Map<String, Integer> state,
			List<String> path) {
		state.put(node, 1);
		path.add(node);
		for (String next : requires.getOrDefault(node, List.of())) {
			Integer s = state.get(next);
			if (s != null && s == 1) {
				List<String> cycle = new ArrayList<>(path.subList(path.indexOf(next), path.size()));
				cycle.add(next);
				return cycle;
			}
			if (s == null && requires.containsKey(next)) {
				List<String> found = visit(next, requires, state, path);
				if (found != null) {
					return found;
				}
			}
		}
		path.removeLast();
		state.put(node, 2);
		return null;
	}

	// ---- Lectura de YAML ----

	private Map<String, Object> readMap(Path file) {
		LoaderOptions options = new LoaderOptions();
		options.setMaxAliasesForCollections(10);
		options.setCodePointLimit(2_000_000);
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return asMap(new Yaml(new SafeConstructor(options)).load(reader), rel(file));
		}
		catch (IOException | RuntimeException ex) {
			problems.add(rel(file) + ": no se puede leer el YAML (" + firstLine(ex.getMessage()) + ")");
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> asMap(Object value, String where) {
		if (value instanceof Map<?, ?> map) {
			return (Map<String, Object>) map;
		}
		problems.add(where + ": se esperaba un objeto YAML");
		return null;
	}

	private String text(Map<String, Object> m, String key, String where) {
		Object value = m.get(key);
		if (value instanceof String s && !s.isBlank()) {
			return s;
		}
		problems.add(where + ": falta el texto '" + key + "'");
		return null;
	}

	private String id(Map<String, Object> m, String key, String where) {
		String value = text(m, key, where);
		if (value != null && !ID.matcher(value).matches()) {
			problems.add(where + ": '" + key + "' no es un identificador válido: " + value);
			return null;
		}
		return value;
	}

	private Integer integer(Map<String, Object> m, String key, String where) {
		if (m.get(key) instanceof Integer i && i >= 1 && i <= 10_000) {
			return i;
		}
		problems.add(where + ": '" + key + "' debe ser un entero entre 1 y 10000");
		return null;
	}

	private List<String> strings(Map<String, Object> m, String key, String where, boolean optional) {
		Object value = m.get(key);
		if (value == null && optional) {
			return List.of();
		}
		if (!(value instanceof List<?> list)) {
			problems.add(where + ": '" + key + "' debe ser una lista");
			return null;
		}
		List<String> result = new ArrayList<>();
		for (Object item : list) {
			if (item instanceof String s && !s.isBlank()) {
				result.add(s);
			}
			else {
				problems.add(where + ": '" + key + "' contiene un elemento que no es texto");
				return null;
			}
		}
		return result;
	}

	private Texts texts(Map<String, Object> m, String key, String where) {
		Map<String, Object> map = m.get(key) instanceof Map<?, ?> ? asMap(m.get(key), where) : null;
		if (map == null) {
			problems.add(where + ": '" + key + "' debe tener el texto en es y en en");
			return null;
		}
		Map<String, String> byLocale = new LinkedHashMap<>();
		for (String locale : LOCALES) {
			if (map.get(locale) instanceof String s && !s.isBlank()) {
				byLocale.put(locale, s.strip());
			}
			else {
				problems.add(where + ": '" + key + "' no tiene texto en '" + locale + "'");
			}
		}
		return byLocale.size() == LOCALES.size() ? new Texts(byLocale) : null;
	}

	// ---- Utilidades ----

	private List<Path> children(Path dir, boolean directoriesOnly) {
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(p -> !p.getFileName().toString().startsWith("."))
					.filter(p -> !directoriesOnly || Files.isDirectory(p))
					.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
		}
		catch (IOException ex) {
			problems.add(rel(dir) + ": no se puede leer la carpeta (" + ex.getMessage() + ")");
			return List.of();
		}
	}

	private String rel(Path path) {
		try {
			return root.relativize(path).toString().replace('\\', '/');
		}
		catch (IllegalArgumentException ex) {
			return path.toString();
		}
	}

	private static String firstLine(String message) {
		if (message == null) {
			return "sin detalle";
		}
		int end = message.indexOf('\n');
		return end < 0 ? message : message.substring(0, end);
	}

	private static String hash(String unitId, int position, ContentStatus status, List<String> skills, Texts title,
			Texts body, List<Exercise> exercises) {
		StringBuilder sb = new StringBuilder();
		sb.append(unitId).append('\0').append(position).append('\0').append(status).append('\0');
		skills.stream().sorted().forEach(s -> sb.append(s).append(','));
		for (String locale : LOCALES) {
			sb.append('\0').append(title.get(locale)).append('\0').append(body.get(locale));
		}
		// Los ejercicios forman parte de la lección: si cambian, la lección se vuelve a importar.
		sb.append('\0').append(exercises);
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 no disponible", ex);
		}
	}

}
