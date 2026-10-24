package app.plataforma.api.identity.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

	Optional<EmailToken> findByTokenHash(String tokenHash);

	/**
	 * Marca el token como usado solo si sigue disponible. La condición va en la sentencia SQL para que dos
	 * peticiones simultáneas con el mismo token no puedan usarlo las dos: una obtiene 1 fila y la otra 0.
	 */
	@Modifying
	@Query("update EmailToken t set t.usedAt = :now where t.id = :id and t.usedAt is null and t.expiresAt > :now")
	int markUsed(@Param("id") UUID id, @Param("now") Instant now);

	/** Invalida los tokens aún abiertos de un usuario para un propósito (al emitir uno nuevo). */
	@Modifying
	@Query("update EmailToken t set t.usedAt = :now where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
	int invalidateOpen(@Param("userId") UUID userId, @Param("purpose") EmailToken.Purpose purpose,
			@Param("now") Instant now);

	/** Limpieza: borra tokens caducados o usados antes de la fecha indicada. */
	@Modifying
	@Query("delete from EmailToken t where t.expiresAt < :cutoff or (t.usedAt is not null and t.usedAt < :cutoff)")
	int deleteStale(@Param("cutoff") Instant cutoff);

}
