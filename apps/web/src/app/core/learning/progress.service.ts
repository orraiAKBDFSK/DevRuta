import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ProgressSummary } from './models';

/** XP, racha y logros del usuario. Los calcula el servidor; la web solo los muestra. */
@Injectable({ providedIn: 'root' })
export class ProgressService {
  private readonly http = inject(HttpClient);

  summary(): Promise<ProgressSummary> {
    return firstValueFrom(this.http.get<ProgressSummary>('/api/v1/progress'));
  }
}
