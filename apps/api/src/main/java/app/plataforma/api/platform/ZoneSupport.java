package app.plataforma.api.platform;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Zona horaria del usuario a partir de la cabecera {@code X-Time-Zone} (la web envía la del navegador). Se usa para
 * decidir qué día es para él (rachas). Cualquier valor ausente o no válido se trata como UTC.
 */
public final class ZoneSupport {

	public static final String HEADER = "X-Time-Zone";

	private ZoneSupport() {
	}

	public static ZoneId resolve(String header) {
		if (header == null || header.isBlank() || header.length() > 64) {
			return ZoneOffset.UTC;
		}
		try {
			return ZoneId.of(header.trim());
		}
		catch (DateTimeException e) {
			return ZoneOffset.UTC;
		}
	}

}
