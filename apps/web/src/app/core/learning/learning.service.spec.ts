import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LearningService } from './learning.service';

describe('LearningService', () => {
  let learning: LearningService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    learning = TestBed.inject(LearningService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for the exercises of a lesson', async () => {
    const done = learning.exercises('py.u01.l01');
    http.expectOne('/api/v1/lessons/py.u01.l01/exercises').flush([]);

    expect(await done).toEqual([]);
  });

  it('sends the answer wrapped in an "answer" object', async () => {
    const done = learning.attempt('py.u01.l01.e01', { choice: 2 });
    const request = http.expectOne('/api/v1/exercises/py.u01.l01.e01/attempts');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ answer: { choice: 2 } });
    request.flush({ correct: false, solved: false, attempts: 1, hintsAvailable: 1, explanation: null });

    expect((await done).correct).toBe(false);
  });

  it('asks for the next hint', async () => {
    const done = learning.nextHint('py.u01.l01.e01');
    http.expectOne('/api/v1/exercises/py.u01.l01.e01/hints').flush({ level: 1, total: 2, text: 'Pista', last: false });

    expect((await done).text).toBe('Pista');
  });

  it('encodes ids so a crafted id cannot change the path', async () => {
    const done = learning.attempt('../me', { text: 'x' });
    const request = http.expectOne((r) => r.url.includes('/exercises/'));
    expect(request.request.url).toBe('/api/v1/exercises/..%2Fme/attempts');
    request.flush({}, { status: 404, statusText: 'Not Found' });

    await expect(done).rejects.toBeTruthy();
  });
});
