package cl.grupo5.proyectominecraft.auditoria;

import com.google.cloud.firestore.Firestore;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class AuditService {
  public static final String CREAR = "CREAR";
  public static final String EDITAR = "EDITAR";
  public static final String ELIMINAR = "ELIMINAR";
  public static final String ITEM = "ITEM";
  public static final String PROYECTO = "PROYECTO";
  public static final String USUARIO = "USUARIO";
  public static final String COMENTARIO = "COMENTARIO";

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);
  private static final String COLECCION = "auditoria";

  private final Firestore db;

  public AuditService(Firestore db) {
    this.db = db;
  }

  /** Registra un evento con el actor de la sesión. Nunca lanza: una falla de auditoría no debe romper la acción. */
  public void registrar(HttpSession s, String accion, String entidad, String entidadId, String entidadNombre, String detalle) {
    try {
      String actorUid = s != null ? (String) s.getAttribute("uid") : null;
      String actorNombre = s != null ? (String) s.getAttribute("nombre") : null;
      if (actorNombre == null && s != null) actorNombre = (String) s.getAttribute("email");
      var evento = construir(actorUid, actorNombre, accion, entidad, entidadId, entidadNombre, detalle,
          Instant.now().toString());
      var ref = db.collection(COLECCION).document();
      evento.setId(ref.getId());
      ref.set(evento).get();
    } catch (Exception e) {
      log.warn("No se pudo registrar el evento de auditoría {} {} {}: {}", accion, entidad, entidadId, e.getMessage());
    }
  }

  public List<EventoAuditoria> listar(String entidad, String accion, String q) throws Exception {
    var docs = db.collection(COLECCION).get().get().getDocuments();
    var eventos = docs.stream().map(d -> {
      var e = d.toObject(EventoAuditoria.class);
      e.setId(d.getId());
      return e;
    }).toList();
    return filtrar(eventos, entidad, accion, q);
  }

  static EventoAuditoria construir(String actorUid, String actorNombre, String accion, String entidad,
                                   String entidadId, String entidadNombre, String detalle, String fecha) {
    var e = new EventoAuditoria();
    e.setActorUid(actorUid);
    e.setActorNombre(actorNombre);
    e.setAccion(accion);
    e.setEntidad(entidad);
    e.setEntidadId(entidadId);
    e.setEntidadNombre(entidadNombre);
    e.setDetalle(detalle);
    e.setFecha(fecha);
    return e;
  }

  static List<EventoAuditoria> filtrar(List<EventoAuditoria> eventos, String entidad, String accion, String q) {
    var needle = q == null ? "" : q.trim().toLowerCase();
    return eventos.stream()
        .filter(e -> entidad == null || entidad.isBlank() || entidad.equals(e.getEntidad()))
        .filter(e -> accion == null || accion.isBlank() || accion.equals(e.getAccion()))
        .filter(e -> needle.isEmpty() || contiene(e.getActorNombre(), needle)
            || contiene(e.getEntidadNombre(), needle) || contiene(e.getEntidadId(), needle))
        .sorted(Comparator.comparing(EventoAuditoria::getFecha, Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();
  }

  private static boolean contiene(String valor, String needle) {
    return valor != null && valor.toLowerCase().contains(needle);
  }
}
