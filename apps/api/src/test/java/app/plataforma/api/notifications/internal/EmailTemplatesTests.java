package app.plataforma.api.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import app.plataforma.api.platform.email.EmailMessage;

class EmailTemplatesTests {

	private static final String LINK = "http://localhost:4200/restablecer?token=abc_DEF-123&x=1";

	@Test
	void spanishVerificationEmailContainsTheLinkAndTheExpiry() {
		EmailMessage mail = EmailTemplates.build(EmailTemplates.Kind.VERIFY_EMAIL, "es", "ana@example.com",
				"Plataforma", LINK, Duration.ofHours(24));

		assertThat(mail.to()).isEqualTo("ana@example.com");
		assertThat(mail.subject()).isEqualTo("Verifica tu correo");
		assertThat(mail.text()).contains(LINK).contains("caduca en 24 horas");
	}

	@Test
	void englishResetEmailIsUsedForEnglishProfiles() {
		EmailMessage mail = EmailTemplates.build(EmailTemplates.Kind.RESET_PASSWORD, "en", "ana@example.com",
				"Platform", LINK, Duration.ofMinutes(30));

		assertThat(mail.subject()).isEqualTo("Reset your password");
		assertThat(mail.text()).contains("expires in 30 minutes").contains("works only once");
	}

	@Test
	void unknownLocalesFallBackToSpanish() {
		EmailMessage mail = EmailTemplates.build(EmailTemplates.Kind.RESET_PASSWORD, "fr", "ana@example.com",
				"Plataforma", LINK, Duration.ofMinutes(30));

		assertThat(mail.subject()).isEqualTo("Restablece tu contraseña");
	}

	@Test
	void htmlVersionEscapesTheLink() {
		EmailMessage mail = EmailTemplates.build(EmailTemplates.Kind.VERIFY_EMAIL, "es", "ana@example.com",
				"Plataforma", LINK, Duration.ofHours(24));

		assertThat(mail.html()).contains("token=abc_DEF-123&amp;x=1").doesNotContain("123&x=1");
	}

	@Test
	void emailMessageToStringDoesNotLeakTheBody() {
		EmailMessage mail = new EmailMessage("ana@example.com", "Asunto", "enlace secreto", null);

		assertThat(mail.toString()).doesNotContain("secreto").doesNotContain("ana@example.com");
	}

	@Test
	void describesDurationsInBothLanguages() {
		assertThat(EmailTemplates.describe(Duration.ofHours(1), false)).isEqualTo("1 hora");
		assertThat(EmailTemplates.describe(Duration.ofHours(2), true)).isEqualTo("2 hours");
		assertThat(EmailTemplates.describe(Duration.ofMinutes(90), false)).isEqualTo("90 minutos");
		assertThat(EmailTemplates.describe(Duration.ofMinutes(1), true)).isEqualTo("1 minute");
	}

}
