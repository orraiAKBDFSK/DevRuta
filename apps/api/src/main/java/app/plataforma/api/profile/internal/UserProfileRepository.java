package app.plataforma.api.profile.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
}
