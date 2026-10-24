import { Marked } from 'marked';

// Una instancia propia, sin extensiones: Markdown estándar con tablas y bloques de código (GFM).
const marked = new Marked({ gfm: true, breaks: false });

/**
 * Convierte Markdown en HTML. El resultado NO es de confianza: debe asignarse a `[innerHTML]`, donde Angular
 * lo sanea (quita scripts, manejadores de eventos y URL peligrosas) antes de insertarlo en la página.
 */
export function renderMarkdown(markdown: string): string {
  return marked.parse(markdown, { async: false });
}

/** Igual que {@link renderMarkdown} pero para una sola línea (sin párrafos): opciones y pistas con `código`. */
export function renderInlineMarkdown(markdown: string): string {
  return marked.parseInline(markdown, { async: false });
}
