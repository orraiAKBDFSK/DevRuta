package app.plataforma.api.catalog.internal;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Importa el contenido al arrancar cuando {@code app.content.import-on-startup=true} (desarrollo, pruebas y el
 * Compose completo). Si el contenido es inválido el arranque falla con la lista de problemas: mejor eso que servir
 * un catálogo a medias.
 */
@Component
@EnableConfigurationProperties(ContentProperties.class)
@ConditionalOnProperty(prefix = "app.content", name = "import-on-startup", havingValue = "true")
class ContentImportRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ContentImportRunner.class);

	private final ContentImporter importer;

	private final ContentProperties properties;

	ContentImportRunner(ContentImporter importer, ContentProperties properties) {
		this.importer = importer;
		this.properties = properties;
	}

	@Override
	public void run(ApplicationArguments args) {
		Path root = Path.of(properties.path()).toAbsolutePath().normalize();
		ImportReport report = importer.importFrom(root, properties.minStatus());
		log.info("Contenido importado desde {} (estado mínimo {}): {}", root, properties.minStatus(), report);
	}

}
