import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Answer, AttemptResult, Exercise, Hint } from './models';

/** Ejercicios de una lección, intentos y pistas. El servidor corrige; el navegador nunca conoce la solución. */
@Injectable({ providedIn: 'root' })
export class LearningService {
  private readonly http = inject(HttpClient);

  exercises(lessonId: string): Promise<Exercise[]> {
    return firstValueFrom(
      this.http.get<Exercise[]>(`/api/v1/lessons/${encodeURIComponent(lessonId)}/exercises`),
    );
  }

  attempt(exerciseId: string, answer: Answer): Promise<AttemptResult> {
    return firstValueFrom(
      this.http.post<AttemptResult>(`/api/v1/exercises/${encodeURIComponent(exerciseId)}/attempts`, { answer }),
    );
  }

  nextHint(exerciseId: string): Promise<Hint> {
    return firstValueFrom(
      this.http.post<Hint>(`/api/v1/exercises/${encodeURIComponent(exerciseId)}/hints`, {}),
    );
  }
}
