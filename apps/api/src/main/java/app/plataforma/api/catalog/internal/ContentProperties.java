package app.plataforma.api.catalog.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del importador de contenido.
 *
 * @param path carpeta con el contenido (la que contiene una carpeta por lenguaje)
 * @param minStatus estado mínimo de una lección para importarla. En producción {@code PUBLISHED}: nada sin revisión
 * humana llega a los estudiantes. En desarrollo {@code DRAFT}.
 * @param importOnStartup si es {@code true}, importa al arrancar la aplicación (desarrollo y pruebas)
 */
@ConfigurationProperties(prefix = "app.content")
record ContentProperties(@DefaultValue("content") String path, @DefaultValue("PUBLISHED") ContentStatus minStatus,
		@DefaultValue("false") boolean importOnStartup) {
}
