import { renderMarkdown } from './markdown';

describe('renderMarkdown', () => {
  it('renders headings, emphasis, tables and code blocks', () => {
    const html = renderMarkdown('## Título\n\nTexto con `código` y **negrita**.\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n```python\nprint(1)\n```');

    expect(html).toContain('<h2>Título</h2>');
    expect(html).toContain('<code>código</code>');
    expect(html).toContain('<strong>negrita</strong>');
    expect(html).toContain('<table>');
    expect(html).toContain('<pre><code class="language-python">');
  });

  it('returns an empty string for an empty text', () => {
    expect(renderMarkdown('')).toBe('');
  });
});
