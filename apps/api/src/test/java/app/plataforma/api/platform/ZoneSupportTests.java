package app.plataforma.api.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class ZoneSupportTests {

	@Test
	void usesTheZoneWhenItIsValid() {
		assertThat(ZoneSupport.resolve("America/Bogota")).isEqualTo(ZoneId.of("America/Bogota"));
		assertThat(ZoneSupport.resolve(" Europe/Madrid ")).isEqualTo(ZoneId.of("Europe/Madrid"));
	}

	@Test
	void fallsBackToUtcForAnythingElse() {
		for (String bad : new String[] { null, "", "  ", "no/existe", "x".repeat(65), "../../etc", "UTC+99:00" }) {
			assertThat(ZoneSupport.resolve(bad)).as(String.valueOf(bad)).isEqualTo(ZoneOffset.UTC);
		}
	}

}
