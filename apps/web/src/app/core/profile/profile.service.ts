import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Profile, ProfileUpdate } from '../auth/models';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);

  readonly profile = signal<Profile | null>(null);

  async load(): Promise<Profile> {
    const profile = await firstValueFrom(this.http.get<Profile>('/api/v1/me/profile'));
    this.profile.set(profile);
    return profile;
  }

  async update(changes: ProfileUpdate): Promise<Profile> {
    const profile = await firstValueFrom(this.http.patch<Profile>('/api/v1/me/profile', changes));
    this.profile.set(profile);
    return profile;
  }

  clear(): void {
    this.profile.set(null);
  }
}
