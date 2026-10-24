package app.plataforma.api.platform;

import java.util.Locale;

/** Elige el idioma del contenido a partir de la cabecera {@code Accept-Language}. Idiomas disponibles: es y en. */
public final class LocaleSupport {

	public static final String DEFAULT = "es";

	private LocaleSupport() {
	}

	/** Primer idioma de la cabecera que tengamos (en orden de aparición); {@code es} si ninguno. */
	public static String resolve(String acceptLanguage) {
		if (acceptLanguage == null) {
			return DEFAULT;
		}
		for (String part : acceptLanguage.split(",")) {
			String tag = part.split(";")[0].trim().toLowerCase(Locale.ROOT);
			if (tag.equals("es") || tag.startsWith("es-")) {
				return "es";
			}
			if (tag.equals("en") || tag.startsWith("en-")) {
				return "en";
			}
		}
		return DEFAULT;
	}

}
