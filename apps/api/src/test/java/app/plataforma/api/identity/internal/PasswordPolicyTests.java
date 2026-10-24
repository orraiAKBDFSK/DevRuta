package app.plataforma.api.identity.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import app.plataforma.api.platform.ApiException;

class PasswordPolicyTests {

	@Test
	void acceptsLongEnoughPasswords() {
		assertThatCode(() -> PasswordPolicy.validate("una-clave-larga", "ana@example.com")).doesNotThrowAnyException();
	}

	@Test
	void rejectsShortPasswords() {
		assertThatThrownBy(() -> PasswordPolicy.validate("123456789", "ana@example.com"))
				.isInstanceOfSatisfying(ApiException.class, e -> org.assertj.core.api.Assertions.assertThat(e.getCode())
						.isEqualTo("PASSWORD_TOO_SHORT"));
	}

	@Test
	void rejectsTooLongPasswords() {
		assertThatThrownBy(() -> PasswordPolicy.validate("a".repeat(129), "ana@example.com"))
				.isInstanceOfSatisfying(ApiException.class, e -> org.assertj.core.api.Assertions.assertThat(e.getCode())
						.isEqualTo("PASSWORD_TOO_LONG"));
	}

	@Test
	void rejectsPasswordEqualToEmailIgnoringCase() {
		assertThatThrownBy(() -> PasswordPolicy.validate("Ana@Example.com", "ana@example.com"))
				.isInstanceOfSatisfying(ApiException.class, e -> org.assertj.core.api.Assertions.assertThat(e.getCode())
						.isEqualTo("PASSWORD_EQUALS_EMAIL"));
	}

}
