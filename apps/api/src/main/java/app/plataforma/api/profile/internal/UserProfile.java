package app.plataforma.api.profile.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profile")
class UserProfile {

	@Id
	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(nullable = false)
	private String locale;

	@Enumerated(EnumType.STRING)
	@Column(name = "experience_level", nullable = false)
	private ExperienceLevel experienceLevel = ExperienceLevel.BEGINNER;

	@Enumerated(EnumType.STRING)
	private LearningGoal goal;

	@Column(name = "daily_goal_minutes", nullable = false)
	private short dailyGoalMinutes = 10;

	@Column(nullable = false)
	private String timezone = "UTC";

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UserProfile() {
	}

	UserProfile(UUID userId, String displayName, String locale, Instant now) {
		this.userId = userId;
		this.displayName = displayName;
		this.locale = locale;
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void touch() {
		this.updatedAt = Instant.now();
	}

	UUID getUserId() {
		return userId;
	}

	String getDisplayName() {
		return displayName;
	}

	void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	String getLocale() {
		return locale;
	}

	void setLocale(String locale) {
		this.locale = locale;
	}

	ExperienceLevel getExperienceLevel() {
		return experienceLevel;
	}

	void setExperienceLevel(ExperienceLevel experienceLevel) {
		this.experienceLevel = experienceLevel;
	}

	LearningGoal getGoal() {
		return goal;
	}

	void setGoal(LearningGoal goal) {
		this.goal = goal;
	}

	short getDailyGoalMinutes() {
		return dailyGoalMinutes;
	}

	void setDailyGoalMinutes(short dailyGoalMinutes) {
		this.dailyGoalMinutes = dailyGoalMinutes;
	}

	String getTimezone() {
		return timezone;
	}

	void setTimezone(String timezone) {
		this.timezone = timezone;
	}

}
