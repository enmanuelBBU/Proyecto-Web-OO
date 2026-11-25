package cl.grupo5.proyectominecraft.proyectos;

import lombok.Data;

/** Comentario en un proyecto. Vive en la subcolección proyectos/{proyectoId}/comentarios. */
@Data
public class Comentario {
  private String id;
  private String proyectoId;
  private String autorUid;
  private String autorNombre;
  private String texto;
  private String fechaCreacion;  // ISO-8601
  private String fechaEdicion;   // null si nunca se editó
}
