import { TestBed } from '@angular/core/testing';
import { EN } from './en';
import { ES } from './es';
import { I18nService } from './i18n.service';

describe('I18nService', () => {
  let service: I18nService;

  beforeEach(() => {
    localStorage.clear();
    service = TestBed.inject(I18nService);
  });

  it('translates and interpolates parameters', () => {
    service.use('es');
    expect(service.t('home.title', { name: 'Ana' })).toBe('Hola, Ana');

    service.use('en');
    expect(service.t('home.title', { name: 'Ana' })).toBe('Hello, Ana');
  });

  it('returns the key when a translation is missing', () => {
    expect(service.t('no.existe')).toBe('no.existe');
  });

  it('keeps the document language in sync', async () => {
    service.use('en');
    TestBed.tick();
    expect(document.documentElement.lang).toBe('en');
  });

  it('has the same keys in both languages', () => {
    expect(Object.keys(EN).sort()).toEqual(Object.keys(ES).sort());
  });
});
