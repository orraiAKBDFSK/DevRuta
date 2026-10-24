export type LessonStatus = 'DRAFT' | 'REVIEWED' | 'PUBLISHED';

export interface SkillRef {
  id: string;
  name: string;
}

export interface CourseSummary {
  id: string;
  languageCode: string;
  languageName: string;
  title: string;
  description: string;
  unitCount: number;
  lessonCount: number;
}

export interface LessonOutline {
  id: string;
  position: number;
  title: string;
  status: LessonStatus;
  skills: SkillRef[];
}

export interface UnitOutline {
  id: string;
  position: number;
  title: string;
  summary: string;
  lessons: LessonOutline[];
}

export interface CourseDetail {
  id: string;
  languageCode: string;
  languageName: string;
  title: string;
  description: string;
  units: UnitOutline[];
}

export interface LessonLink {
  id: string;
  title: string;
}

export interface LessonDetail {
  id: string;
  position: number;
  title: string;
  bodyMarkdown: string;
  status: LessonStatus;
  courseId: string;
  courseTitle: string;
  unitId: string;
  unitTitle: string;
  skills: SkillRef[];
  previous: LessonLink | null;
  next: LessonLink | null;
}
