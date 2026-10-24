import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CourseDetail, CourseSummary, LessonDetail } from './models';

/**
 * Lectura del catálogo. El idioma del contenido lo decide la cabecera Accept-Language, que añade
 * `languageInterceptor`; cuando el usuario cambia de idioma las pantallas vuelven a pedir los datos.
 */
@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);

  courses(): Promise<CourseSummary[]> {
    return firstValueFrom(this.http.get<CourseSummary[]>('/api/v1/catalog/courses'));
  }

  course(courseId: string): Promise<CourseDetail> {
    return firstValueFrom(this.http.get<CourseDetail>(`/api/v1/catalog/courses/${encodeURIComponent(courseId)}`));
  }

  lesson(lessonId: string): Promise<LessonDetail> {
    return firstValueFrom(this.http.get<LessonDetail>(`/api/v1/catalog/lessons/${encodeURIComponent(lessonId)}`));
  }
}
