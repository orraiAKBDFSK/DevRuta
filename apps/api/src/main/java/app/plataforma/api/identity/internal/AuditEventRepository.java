package app.plataforma.api.identity.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

	/** Anonimiza los eventos de una cuenta que se borra: conservan el tipo y la fecha, pero ya no apuntan a nadie. */
	@Modifying
	@Query("update AuditEvent e set e.userId = null where e.userId = :userId")
	int anonymize(@Param("userId") UUID userId);

}
