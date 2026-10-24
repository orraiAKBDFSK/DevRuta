import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ProgressService } from './progress.service';

describe('ProgressService', () => {
  it('asks the server for the summary', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const done = TestBed.inject(ProgressService).summary();

    http.expectOne('/api/v1/progress').flush({ xp: 20, level: 1, achievements: [] });

    expect((await done).xp).toBe(20);
    http.verify();
  });
});
