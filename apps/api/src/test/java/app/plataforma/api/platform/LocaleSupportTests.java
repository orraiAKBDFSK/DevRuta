package app.plataforma.api.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocaleSupportTests {

	@Test
	void picksTheFirstSupportedLanguage() {
		assertThat(LocaleSupport.resolve("en")).isEqualTo("en");
		assertThat(LocaleSupport.resolve("en-US,en;q=0.9,es;q=0.8")).isEqualTo("en");
		assertThat(LocaleSupport.resolve("fr-FR, es-PE;q=0.8, en;q=0.5")).isEqualTo("es");
		assertThat(LocaleSupport.resolve("ES")).isEqualTo("es");
	}

	@Test
	void fallsBackToSpanish() {
		assertThat(LocaleSupport.resolve(null)).isEqualTo("es");
		assertThat(LocaleSupport.resolve("")).isEqualTo("es");
		assertThat(LocaleSupport.resolve("fr, de;q=0.7")).isEqualTo("es");
		assertThat(LocaleSupport.resolve("english")).isEqualTo("es");
	}

}
