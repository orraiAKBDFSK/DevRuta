package app.plataforma.api.identity.internal;

import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuerpos de petición y respuesta del módulo identity. Los mensajes de validación se traducen en el frontend por código. */
final class AuthDtos {

	private AuthDtos() {
	}

	record RegisterRequest(
			@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(max = 128) String password,
			@NotBlank @Size(max = 50) String displayName,
			@Pattern(regexp = "es|en") String locale,
			@AssertTrue boolean acceptTerms) {
	}

	record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {
	}

	record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
			@NotBlank @Size(max = 128) String newPassword) {
	}

	record DeleteAccountRequest(@NotBlank @Size(max = 128) String password) {
	}

	record VerifyEmailRequest(@NotBlank @Size(max = 200) String token) {
	}

	record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {
	}

	record ResetPasswordRequest(@NotBlank @Size(max = 200) String token,
			@NotBlank @Size(max = 128) String newPassword) {
	}

	record AccountResponse(UUID id, String email, boolean emailVerified) {
	}

}
