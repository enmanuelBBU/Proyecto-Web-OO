# Módulo Proyectos → Export PDF

Documentación del esquema de datos que usa el **Exportar PDF** sobre el módulo de
Proyectos (`/proyectos/reportes`, `/proyectos/reporte-pdf?id={id}`).

## Colección

- **Colección Firestore:** `proyectos`
- **Id del documento:** slug del proyecto (criterio igual que `items`), ej. `casa-de-roble`.

## Estructura del documento

| Campo             | Tipo                    | Obligatorio | Descripción                                                    |
|-------------------|-------------------------|-------------|----------------------------------------------------------------|
| `nombre`          | `string`                | Sí          | Nombre visible del proyecto.                                   |
| `descripcion`     | `string`                | No          | Descripción del proyecto.                                      |
| `estado`          | `string`                | No          | Estado del proyecto. Default `PLANIFICACION`.                  |
| `itemsRequeridos` | `array<object>`         | No          | Materiales/ítems requeridos por el proyecto.                   |
| `creadorUid`      | `string`                | No          | `uid` de Firestore Auth del creador.                           |
| `creadorNombre`   | `string`                | No          | Nombre legible del creador (fallback en el PDF: `creadorUid`). |
| `fechaCreacion`   | `string`                | No          | Instant ISO de creación.                                       |
| `historialEstados`| `array<object>`         | No          | Historial de transiciones de estado.                           |

### Sub-documento de cada entrada en `itemsRequeridos`

| Campo      | Tipo     | Obligatorio | Descripción                                              |
|------------|----------|-------------|----------------------------------------------------------|
| `itemId`   | `string` | Sí          | Id (slug) del ítem en la colección `items` del catálogo. |
| `cantidad` | `number` | Sí          | Cantidad requerida de ese material.                      |

### Valores de `estado`

`PLANIFICACION`, `EN_CONSTRUCCION`, `COMPLETADO`, `CANCELADO`.
El PDF los muestra con etiqueta legible (En planificación, En construcción, Completado, Cancelado).

```json
{
  "nombre": "Casa de Roble",
  "descripcion": "Vivienda de supervivencia de nivel iniciación.",
  "estado": "EN_CONSTRUCCION",
  "creadorUid": "uid-del-usuario",
  "creadorNombre": "Andrés",
  "fechaCreacion": "2026-09-09T16:00:00Z",
  "itemsRequeridos": [
    { "itemId": "tablones_de_roble", "cantidad": 64 },
    { "itemId": "piedra", "cantidad": 32 }
  ],
  "historialEstados": []
}
```

## Notas

1. **`itemId` referencia el catálogo**, no guarda nombre/categoría duplicados. El export
   resuelve el nombre y la categoría reales desde `items` al generar el PDF.
2. Si un `itemId` ya no existe en el catálogo, el reporte muestra el `itemId` como nombre.
3. Si la colección `proyectos` no existe o está vacía, `/proyectos/reportes` muestra un
   estado vacío amigable.
4. El autor del reporte se toma de `creadorNombre` (fallback: `creadorUid`).
5. El módulo no define campo `objetivo`: el reporte omitirá esa sección.

## Puntos de entrada

| Ruta                             | Método | Descripción                                            |
|----------------------------------|--------|--------------------------------------------------------|
| `/proyectos`                     | GET    | Lista del módulo (el compañero) + botón "Exportar PDF" por proyecto. |
| `/proyectos/{id}`                | GET    | Detalle del proyecto + botón "Exportar PDF".           |
| `/proyectos/reportes`            | GET    | Página con todos los proyectos y su botón de export.   |
| `/proyectos/reporte-pdf?id={id}` | GET    | Genera y descarga el PDF de un proyecto (`inline`).    |