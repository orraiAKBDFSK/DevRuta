package app.plataforma.api.catalog.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Contenido de prueba pequeño que se escribe en una carpeta temporal para poder estropearlo a propósito.
 * Lenguaje {@code fx} con 2 unidades y 3 lecciones: u01 (l01 sobre la habilidad a, l02 sobre b) y u02 (l01 sobre c).
 * Habilidades: a, b (requiere a), c (requiere b).
 */
public final class FixtureContent {

	public final Path root;

	public FixtureContent(Path tempDir) {
		this.root = tempDir;
		write("fx/course.yaml", """
				id: fx.course
				language: fx
				language_name: Fixture
				position: 1
				title: {es: 'Curso de prueba', en: 'Test course'}
				description: {es: 'Descripción de prueba', en: 'Test description'}
				""");
		write("fx/skills.yaml", """
				skills:
				  - {id: fx.a, name: {es: 'Habilidad A', en: 'Skill A'}, requires: []}
				  - {id: fx.b, name: {es: 'Habilidad B', en: 'Skill B'}, requires: [fx.a]}
				  - {id: fx.c, name: {es: 'Habilidad C', en: 'Skill C'}, requires: [fx.b]}
				""");
		write("fx/u01-uno/unit.yaml", unit("fx.u01", 1, "Unidad uno", "Unit one"));
		write("fx/u02-dos/unit.yaml", unit("fx.u02", 2, "Unidad dos", "Unit two"));
		write("fx/u01-uno/01-a.yaml", lesson("fx.u01.l01", "fx.u01", 1, "published", "[fx.a]", "Lección A", "Lesson A"));
		write("fx/u01-uno/02-b.yaml", lesson("fx.u01.l02", "fx.u01", 2, "published", "[fx.b]", "Lección B", "Lesson B"));
		write("fx/u02-dos/01-c.yaml", lesson("fx.u02.l01", "fx.u02", 1, "published", "[fx.c]", "Lección C", "Lesson C"));
	}

	public static String unit(String id, int position, String es, String en) {
		return """
				id: %s
				position: %d
				title: {es: '%s', en: '%s'}
				summary: {es: 'Resumen %s', en: 'Summary %s'}
				""".formatted(id, position, es, en, es, en);
	}

	public static String lesson(String id, String unit, int position, String status, String skills, String es, String en) {
		return """
				id: %s
				language: fx
				unit: %s
				position: %d
				skills: %s
				status: %s
				reviewed_by: [qa]
				title: {es: '%s', en: '%s'}
				explanation:
				  es: |
				    ## %s

				    | a | b |
				    |---|---|
				    | 1 | 2 |
				  en: |
				    ## %s

				    Body in English.
				exercises: []
				""".formatted(id, unit, position, skills, status, es, en, es, en);
	}

	/** Un ejercicio de cada tipo, sobre la habilidad fx.a. Se coloca con {@link #setExercises}. */
	public static final String ALL_TYPES = """
			- id: fx.u01.l01.e01
			  type: multiple_choice
			  difficulty: 1
			  skills: [fx.a]
			  prompt: {es: '¿Cuánto es 2 + 2?', en: 'What is 2 + 2?'}
			  options:
			    - {es: 'Tres', en: 'Three'}
			    - {es: 'Cuatro', en: 'Four'}
			    - {es: 'Cinco', en: 'Five'}
			    - {es: 'Seis', en: 'Six'}
			  answer: 1
			  distractor_tags: {0: arith.off_by_one, 2: arith.off_by_one}
			  hints:
			    - {es: 'Cuenta con los dedos.', en: 'Count on your fingers.'}
			    - {es: 'Es un número par.', en: 'It is an even number.'}
			  explanation: {es: 'Dos más dos son cuatro.', en: 'Two plus two is four.'}
			- id: fx.u01.l01.e02
			  type: predict_output
			  difficulty: 2
			  skills: [fx.a]
			  prompt: {es: '¿Qué muestra?', en: 'What does it show?'}
			  code: |
			    print("a")
			    print("b")
			  answer: "a\\nb"
			  hints:
			    - {es: 'Una línea por print.', en: 'One line per print.'}
			  explanation: {es: 'Cada print escribe una línea.', en: 'Each print writes a line.'}
			- id: fx.u01.l01.e03
			  type: order_lines
			  difficulty: 2
			  skills: [fx.a]
			  prompt: {es: 'Ordena.', en: 'Order them.'}
			  lines:
			    - 'x = 1'
			    - 'y = 2'
			    - 'print(x + y)'
			  hints:
			    - {es: 'Primero se define.', en: 'Define first.'}
			  explanation: {es: 'Primero x, luego y, al final la suma.', en: 'First x, then y, finally the sum.'}
			- id: fx.u01.l01.e04
			  type: fill_code
			  difficulty: 3
			  skills: [fx.a]
			  prompt: {es: 'Completa.', en: 'Fill in.'}
			  code: |
			    print(___, ___)
			  blanks:
			    - ['1', '01']
			    - ['2']
			  hints:
			    - {es: 'Dos números.', en: 'Two numbers.'}
			  explanation: {es: 'Se muestran 1 y 2.', en: '1 and 2 are shown.'}
			- id: fx.u01.l01.e05
			  type: write_code
			  difficulty: 3
			  skills: [fx.a]
			  prompt: {es: 'Escribe.', en: 'Write.'}
			  starter: |
			    # tu código
			  reference_solution: |
			    print(3)
			  tests:
			    - {stdin: "", expected_stdout: "3\\n", visible: true}
			  hints:
			    - {es: 'Usa print.', en: 'Use print.'}
			  explanation: {es: 'Basta un print.', en: 'A print is enough.'}
			""";

	/** Sustituye la lista vacía de ejercicios de un archivo de lección por el bloque indicado. */
	public void setExercises(String relative, String exercisesBlock) {
		replaceInFile(relative, "exercises: []", "exercises:\n" + exercisesBlock.indent(2).stripTrailing());
	}

	public void write(String relative, String content) {
		try {
			Path file = root.resolve(relative);
			Files.createDirectories(file.getParent());
			Files.writeString(file, content, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public void delete(String relative) {
		try {
			Files.delete(root.resolve(relative));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public String read(String relative) {
		try {
			return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public void replaceInFile(String relative, String from, String to) {
		String content = read(relative);
		if (!content.contains(from)) {
			throw new IllegalStateException("No se encontró '" + from + "' en " + relative);
		}
		write(relative, content.replace(from, to));
	}

}
