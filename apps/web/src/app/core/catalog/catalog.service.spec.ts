import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CatalogService } from './catalog.service';

describe('CatalogService', () => {
  let catalog: CatalogService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    catalog = TestBed.inject(CatalogService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for the course list', async () => {
    const done = catalog.courses();
    http.expectOne('/api/v1/catalog/courses').flush([]);

    expect(await done).toEqual([]);
  });

  it('asks for a course and a lesson by their content id', async () => {
    const course = catalog.course('py.course');
    http.expectOne('/api/v1/catalog/courses/py.course').flush({ id: 'py.course' });
    expect((await course).id).toBe('py.course');

    const lesson = catalog.lesson('py.u01.l01');
    http.expectOne('/api/v1/catalog/lessons/py.u01.l01').flush({ id: 'py.u01.l01' });
    expect((await lesson).id).toBe('py.u01.l01');
  });

  it('encodes ids so a crafted id cannot change the path', async () => {
    const done = catalog.lesson('../auth/me');
    const request = http.expectOne((r) => r.url.startsWith('/api/v1/catalog/lessons/'));
    expect(request.request.url).toBe('/api/v1/catalog/lessons/..%2Fauth%2Fme');
    request.flush({}, { status: 404, statusText: 'Not Found' });

    await expect(done).rejects.toBeTruthy();
  });
});
