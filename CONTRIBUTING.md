# Cómo contribuir

Somos un equipo pequeño: pocas reglas, pero se cumplen.

## Ramas

- `main` siempre debe compilar y pasar las pruebas. Nadie sube directamente a `main`.
- Trabajo en ramas cortas (idealmente de menos de 2 días) que salen de `main`:
  `feat/<tema>`, `fix/<tema>`, `docs/<tema>`, `content/<unidad>`, `chore/<tema>`.
- Se integran con *squash merge* mediante pull request.

## Commits

Formato [Conventional Commits](https://www.conventionalcommits.org/es/):

```
feat(api): agrega registro de usuarios
fix(web): corrige el redireccionamiento tras iniciar sesión
content(python): revisa la unidad 3
docs(adr): registra la decisión sobre el runner
```

Tipos: `feat`, `fix`, `docs`, `content`, `test`, `refactor`, `chore`, `ci`.

## Pull requests

- Pequeños y con un solo propósito. Usa la plantilla de `.github/pull_request_template.md`.
- Describe cómo lo probaste. No marques como hecho lo que no se ejecutó.
- Necesitan **una aprobación** de la otra persona del equipo y el CI en verde.
- Los cambios de seguridad, autenticación o del runner se revisan con especial cuidado.

## Decisiones

Toda decisión técnica relevante se registra como ADR en `docs/adr/NNNN-titulo.md` (contexto, decisión,
consecuencias). Una decisión se cambia con un ADR nuevo que reemplaza al anterior.

## Reglas del repositorio

- **Nunca** se suben secretos (contraseñas, claves, tokens). Los valores locales van en `.env` (ignorado por git).
- Las migraciones de base de datos son archivos nuevos `V<n>__<descripcion>.sql`; una migración aplicada no se edita.
- El contenido educativo no se publica sin revisión humana (`reviewed_by`) y sin pasar `scripts/validate_content.py`.
- Módulos del backend: un módulo no accede a las tablas ni a las clases internas de otro; solo a su interfaz pública.
