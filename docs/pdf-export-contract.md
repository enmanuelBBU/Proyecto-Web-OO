# Contrato Firestore · Módulo Proyectos → Export PDF

Este documento define el **contrato de datos** entre el módulo de Proyectos (CRUD) y la
funcionalidad de **Exportar PDF** (`/proyectos/reportes`, `/proyectos/reporte-pdf?id={id}`).

El equipo que construya el formulario/CRUD de proyectos debe persistir los documentos con
este esquema exacto para que el reporte PDF los pueda leer sin cambios.

## Colección

- **Colección Firestore:** `proyectos`
- **Id del documento:** slug legible del proyecto (mismo criterio que `items`), ej. `casa-de-roble`.

## Estructura del documento

| Campo        | Tipo                    | Obligatorio | Descripción                                              |
|--------------|-------------------------|-------------|----------------------------------------------------------|
| `nombre`     | `string`                | Sí          | Nombre visible del proyecto.                             |
| `descripcion`| `string`                | No          | Descripción del proyecto.                                |
| `objetivo`   | `string`                | No          | Objetivo de construcción.                                |
| `estado`     | `string`                | No          | Estado del proyecto (ej. `En planificación`, `En curso`).|
| `autorUid`   | `string`                | No          | `uid` de Firestore Auth del dueño/creador del proyecto.  |
| `materiales` | `array<object>`         | No          | Lista de materiales requeridos por el proyecto.          |

### Sub-documento de cada entrada en `materiales`

| Campo     | Tipo     | Obligatorio | Descripción                                             |
|-----------|----------|-------------|---------------------------------------------------------|
| `itemId`  | `string` | Sí          | Id (slug) del item en la colección `items` del catálogo.|
| `cantidad`| `number` | Sí          | Cantidad requerida de ese material.                     |

```json
{
  "nombre": "Casa de Roble",
  "descripcion": "Vivienda de supervivencia de nivel iniciación.",
  "objetivo": "Levantar una estructura habitable de 3 pisos.",
  "estado": "En planificación",
  "autorUid": "uid-del-usuario",
  "materiales": [
    { "itemId": "tablones_de_roble", "cantidad": 64 },
    { "itemId": "piedra", "cantidad": 32 }
  ]
}
```

## Notas importantes

1. **`itemId` referencia el catálogo**, no guarda nombre/categoría duplicados. El export
   resuelve el nombre y la categoría reales desde `items` en el momento de generar el PDF.
   Esto evita datos desincronizados si un item se renombra.
2. Si un `itemId` ya no existe en el catálogo, el reporte muestra el `itemId` como nombre
   para que el dato quede visible.
3. Si la colección `proyectos` no existe o está vacía, la página `/proyectos/reportes`
   muestra un estado vacío ("No hay proyectos registrados aún") en lugar de fallar.
4. El export usa el `autorUid` para mostrar el nombre real del autor desde la colección
   `users` (fallback: el propio `uid`).

## Puntos de entrada

| Ruta                             | Método | Descripción                                             |
|----------------------------------|--------|---------------------------------------------------------|
| `/proyectos/reportes`            | GET    | Lista los proyectos con botón "Exportar PDF" por fila.  |
| `/proyectos/reporte-pdf?id={id}` | GET    | Genera y descarga el PDF de un proyecto (`inline`).     |
