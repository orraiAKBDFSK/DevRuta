package app.plataforma.api.catalog.internal;

import java.util.Locale;

/** Estado editorial de una lección. El orden importa: cada estado incluye a los posteriores. */
enum ContentStatus {
	DRAFT, REVIEWED, PUBLISHED;

	static ContentStatus parse(String value) {
		return valueOf(value.trim().toUpperCase(Locale.ROOT));
	}

	boolean atLeast(ContentStatus minimum) {
		return compareTo(minimum) >= 0;
	}
}
