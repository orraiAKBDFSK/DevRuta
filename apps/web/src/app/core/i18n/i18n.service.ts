import { DOCUMENT } from '@angular/common';
import { Injectable, effect, inject, signal } from '@angular/core';
import { Locale } from '../auth/models';
import { EN } from './en';
import { ES } from './es';

const DICTIONARIES: Record<Locale, Record<string, string>> = { es: ES, en: EN };
const STORAGE_KEY = 'locale';

/**
 * Traducciones de la interfaz (es/en). Es deliberadamente simple: un diccionario por idioma y `t(clave)`.
 * El idioma sale del perfil del usuario; sin sesión, de la última elección guardada o del navegador.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly document = inject(DOCUMENT);

  readonly locale = signal<Locale>(this.initialLocale());

  constructor() {
    effect(() => {
      const locale = this.locale();
      this.document.documentElement.lang = locale;
      try {
        localStorage.setItem(STORAGE_KEY, locale);
      } catch {
        // El almacenamiento puede estar bloqueado: la aplicación sigue funcionando sin recordar el idioma.
      }
    });
  }

  use(locale: Locale): void {
    this.locale.set(locale);
  }

  t(key: string, params: Record<string, string> = {}): string {
    const text = DICTIONARIES[this.locale()][key] ?? key;
    return Object.entries(params).reduce((acc, [name, value]) => acc.replaceAll(`{${name}}`, value), text);
  }

  private initialLocale(): Locale {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved === 'es' || saved === 'en') {
        return saved;
      }
    } catch {
      // Ignorado: se usa el idioma del navegador.
    }
    return typeof navigator !== 'undefined' && navigator.language?.toLowerCase().startsWith('en') ? 'en' : 'es';
  }
}
