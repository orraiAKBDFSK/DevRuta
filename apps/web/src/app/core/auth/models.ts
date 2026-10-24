export type Locale = 'es' | 'en';
export type ExperienceLevel = 'BEGINNER' | 'SOME' | 'EXPERIENCED';
export type LearningGoal = 'CAREER' | 'STUDIES' | 'HOBBY' | 'PROJECT';

export interface Account {
  id: string;
  email: string;
  emailVerified: boolean;
}

export interface Profile {
  displayName: string;
  locale: Locale;
  experienceLevel: ExperienceLevel;
  goal: LearningGoal | null;
  dailyGoalMinutes: number;
  timezone: string;
}

export type ProfileUpdate = Partial<Omit<Profile, 'goal'>> & { goal?: LearningGoal };

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
  locale: Locale;
  acceptTerms: boolean;
}
