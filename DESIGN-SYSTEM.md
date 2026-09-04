# Ore & Basalt — Lenguaje de diseño

Sistema de diseño de **Proyecto-Web-OO**, app de gestión de crafteo, inventario y construcción (temática Minecraft). Fuente de verdad: `Ore-Basalt-paleta.pdf`. Tokens ya implementados en `src/main/resources/static/style.css`.

## 1. Color

Seis tokens, cada uno con un trabajo específico. No usar hex sueltos en HTML/CSS — siempre las variables `--amber`, `--red`, `--green`, `--blue`, `--basalt`, `--ash`.

| Token | Hex | Uso |
|---|---|---|
| `--amber` (Ore Amber) | `#D69A2D` | Acento principal: botones primarios, links, foco |
| `--red` (Redstone) | `#C1442D` | Error, eliminar, receta inválida |
| `--green` (Emerald) | `#3E9B5C` | Éxito: guardado, completado |
| `--blue` (Lapis) | `#2F6FB0` | Info, estado "pendiente"/"en progreso" |
| `--basalt` | `#23262B` | Texto principal, superficies oscuras, fondo tema oscuro |
| `--ash` | `#EDEFF2` | Fondo base tema claro |

Tema oscuro/claro resuelven desde el mismo set vía `--bg`/`--fg`/`--card`, ya cableado con `prefers-color-scheme`. No crear una paleta paralela para dark mode.

Contraste mínimo 4.5:1 para texto normal — verificar cualquier combinación nueva antes de usarla (p.ej. texto sobre `--amber` va oscuro, no `--ash`).

## 2. Tipografía

| Familia | Uso |
|---|---|
| **Big Shoulders Display** (700) | Títulos (`h1`, `h2` de panel) — condensada, tono "cantera/industrial" |
| **Karla** (400/700) | Cuerpo de texto, labels de formulario, botones |
| **IBM Plex Mono** (400/700) | Datos: badges de estado, cantidades, tabla de ítems, labels de campo en mayúscula — refuerza el tono "ficha técnica de crafteo" |

Body mínimo 16px en inputs (evita zoom automático en mobile). Line-height 1.5–1.75 en párrafos.

## 3. Espaciado y forma

- Radios: `6px` en inputs/botones, `10px` en cards/tabla, `14px` en paneles grandes (auth shell). Nunca 0px (no es brutalismo) ni pill excepto en `.badge`.
- Bordes: `1px solid color-mix(in srgb, var(--fg) 12%, transparent)` — nunca un gris fijo que rompa el tema oscuro.
- Sombra solo en elementos flotantes/elevados (auth shell, modales futuros): `0 12px 32px color-mix(in srgb, var(--basalt) 18%, transparent)`.

## 4. Componentes

**Botones** — `.btn-primary`/`button[type=submit]` (amber), `.btn-danger` (red), `.btn-info` (blue), `.btn-ghost` (outline). Estado disabled: `opacity:.65; cursor:not-allowed`. Acción async → deshabilitar + spinner (`.spinner`) + texto de progreso ("Entrando…", "Guardando…"), nunca dejar el botón clickeable dos veces.

**Badges de estado** (pendiente / en progreso / completado) — mapeo fijo:
- Pendiente / en progreso → `--blue`
- Completado / éxito → `--green`
- Error / inválido → `--red`

No inventar colores nuevos para nuevos estados: reusar esta terna semántica.

**Toasts** — franja de color a la izquierda (border-left 3–4px) + fondo tenue (`color-mix` 10-12%) del color semántico, texto en `--fg`. Ejemplos reales del proyecto: "Ítem agregado al inventario" (green), "Faltan materiales para craftear" (red), "Sesión iniciada" (blue). *(Aún no implementado en código — usar `.ok`/`.error` como base al construirlos.)*

**Cards** — fondo `--card`, borde sutil, padding 16px, radio 10px. Es el contenedor por defecto de toda sección (formularios, tablas, paneles).

**Formularios** — `label` real con `for`, no placeholder-only. Label en IBM Plex Mono mayúscula pequeña (ver `.field label`) cuando el formulario es de tipo "ficha" (auth, perfil). Autocomplete correcto (`username`, `current-password`, `new-password`).

**Pantallas de autenticación** (`.auth-shell`) — panel izquierdo oscuro (`--basalt`) con crest/wordmark + tagline + lista real de funcionalidades (nunca stats inventadas); panel derecho claro con el formulario. Colapsa a una columna bajo 640px.

## 5. Iconografía

SVG inline, estilo *stroke* (outline, `stroke-width:1.5`, `stroke-linecap/linejoin:round`), 24×24, `currentColor` — nunca emojis como ícono de UI (rompen consistencia entre plataformas/fuentes). El emoji ⛏️ que existía en `h1` fue reemplazado por un SVG de gema/mineral en el crest de auth. Mantener esa línea: si se agrega un ícono de pico, cofre, yunque, etc., dibujarlo o traerlo en el mismo estilo outline, no como emoji ni como PNG.

## 6. Motion

Transiciones de 150–300ms en hover/focus (colores, no layout). Nada de `scale()` en hover que mueva contenido alrededor. Respetar siempre `prefers-reduced-motion: reduce` (ya global en `style.css`) — cualquier animación nueva debe caer dentro de ese media query o usar duraciones ínfimas cuando esté activo.

## 7. Accesibilidad (no negociable)

- Foco visible en todo elemento interactivo: `outline: 3px solid var(--amber)` (ya global).
- Todo input con `label for=` asociado.
- `role="alert"` en mensajes de error de formulario.
- Touch targets ≥ 44×44px en botones.
- Nunca color como único indicador de estado — badges y toasts llevan texto, no solo color.

## 8. Voz y tono (temática Minecraft)

Vocabulario del dominio, en español, consistente en toda la copy:

- **Ítem**, no "producto"/"artículo".
- **Craftear**, **receta**, **inventario**, **materiales** — vocabulario de crafteo.
- **Constructor** para referirse al usuario/perfil, no "cliente".
- Mensajes de error concretos y accionables ("Faltan materiales para craftear", no "Error 400").
- Tono directo, funcional, sin humor forzado — es una herramienta de gestión, no el juego en sí. El toque temático vive en el vocabulario y la iconografía (picos, minerales, cantera), no en literatura.

## 9. Checklist antes de mandar una pantalla nueva

- [ ] Solo usa los 6 tokens de color (vía variable, no hex suelto)
- [ ] Sin emojis como ícono — SVG outline 24×24
- [ ] Labels reales en todo input, autocomplete correcto
- [ ] Foco visible, contraste ≥ 4.5:1 en texto
- [ ] Botón async: disabled + spinner mientras carga
- [ ] Responsive en 375 / 768 / 1024 / 1440px, sin scroll horizontal
- [ ] Copy usa vocabulario de crafteo/inventario, no genérico de e-commerce
