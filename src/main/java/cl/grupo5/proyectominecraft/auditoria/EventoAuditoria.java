package cl.grupo5.proyectominecraft.auditoria;

import lombok.Data;

@Data
public class EventoAuditoria {
  private String id;
  private String actorUid;
  private String actorNombre;
  private String accion;         // CREAR | EDITAR | ELIMINAR
  private String entidad;        // ITEM | PROYECTO | USUARIO
  private String entidadId;
  private String entidadNombre;
  private String detalle;        // opcional
  private String fecha;          // ISO-8601
}
