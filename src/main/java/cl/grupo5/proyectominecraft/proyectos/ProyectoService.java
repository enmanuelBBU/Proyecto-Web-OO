package cl.grupo5.proyectominecraft.proyectos;

import cl.grupo5.proyectominecraft.items.Slugs;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ProyectoService {
  public static final Set<String> ESTADOS_VALIDOS = Set.of(
      "PLANIFICACION", "EN_CONSTRUCCION", "COMPLETADO", "CANCELADO"
  );

  @FunctionalInterface
  public interface CatalogItemChecker {
    boolean exists(String itemId) throws Exception;
  }

  private final Firestore db;

  public ProyectoService(Firestore db) {
    this.db = db;
  }

  public List<Proyecto> list(String q, String estado, String creadorUid) throws Exception {
    var docs = db.collection("proyectos").get().get().getDocuments();
    var proyectos = docs.stream().map(d -> {
      var p = d.toObject(Proyecto.class);
      p.setId(d.getId());
      return p;
    }).toList();

    return filter(proyectos, q, estado, creadorUid);
  }

  public static List<Proyecto> filter(List<Proyecto> proyectos, String q, String estado, String creadorUid) {
    var result = proyectos;
    if (q != null && !q.isBlank()) {
      var needle = q.toLowerCase();
      result = result.stream().filter(p ->
          (p.getNombre() != null && p.getNombre().toLowerCase().contains(needle)) ||
          (p.getDescripcion() != null && p.getDescripcion().toLowerCase().contains(needle))
      ).toList();
    }
    if (estado != null && !estado.isBlank()) {
      result = result.stream().filter(p -> estado.equalsIgnoreCase(p.getEstado())).toList();
    }
    if (creadorUid != null && !creadorUid.isBlank()) {
      result = result.stream().filter(p -> creadorUid.equals(p.getCreadorUid())).toList();
    }
    return result;
  }

  public Proyecto get(String id) throws Exception {
    var snap = db.collection("proyectos").document(id).get().get();
    if (!snap.exists()) return null;
    var p = snap.toObject(Proyecto.class);
    p.setId(id);
    return p;
  }

  public Proyecto create(Proyecto proyecto, String uid, String nombreUsuario) throws Exception {
    if (proyecto.getNombre() == null || proyecto.getNombre().isBlank()) {
      throw new ProyectoValidationException("El nombre del proyecto es obligatorio.");
    }

    String id = Slugs.slugify(proyecto.getNombre());
    var existing = db.collection("proyectos").document(id).get().get();
    if (existing.exists()) {
      throw new ProyectoAlreadyExistsException(id);
    }

    validarEstado(proyecto.getEstado());
    validarItemsRequeridos(proyecto.getItemsRequeridos(),
        itemId -> db.collection("items").document(itemId).get().get().exists());

    proyecto.setId(id);
    proyecto.setCreadorUid(uid);
    proyecto.setCreadorNombre(nombreUsuario != null ? nombreUsuario : "Anónimo");
    if (proyecto.getFechaCreacion() == null) {
      proyecto.setFechaCreacion(Instant.now().toString());
    }

    db.collection("proyectos").document(id).set(proyecto).get();
    return proyecto;
  }

  public Proyecto update(String id, Proyecto proyecto, String userUid, String userRole) throws Exception {
    var snap = db.collection("proyectos").document(id).get().get();
    if (!snap.exists()) return null;

    var actual = snap.toObject(Proyecto.class);

    // Authorization check
    if (!canUserModify(actual, userUid, userRole)) {
      throw new ProyectoValidationException("No tienes permisos para modificar este proyecto.");
    }

    if (proyecto.getNombre() == null || proyecto.getNombre().isBlank()) {
      throw new ProyectoValidationException("El nombre del proyecto es obligatorio.");
    }

    validarEstado(proyecto.getEstado());
    validarItemsRequeridos(proyecto.getItemsRequeridos(),
        itemId -> db.collection("items").document(itemId).get().get().exists());

    // Preserve creator metadata and ID
    proyecto.setId(id);
    proyecto.setCreadorUid(actual.getCreadorUid());
    proyecto.setCreadorNombre(actual.getCreadorNombre());
    proyecto.setFechaCreacion(actual.getFechaCreacion());

    db.collection("proyectos").document(id).set(proyecto).get();
    return proyecto;
  }

  public boolean delete(String id, String userUid, String userRole) throws Exception {
    var snap = db.collection("proyectos").document(id).get().get();
    if (!snap.exists()) return false;

    var actual = snap.toObject(Proyecto.class);

    // Authorization check
    if (!canUserModify(actual, userUid, userRole)) {
      throw new ProyectoValidationException("No tienes permisos para eliminar este proyecto.");
    }

    db.collection("proyectos").document(id).delete().get();
    return true;
  }

  public static boolean canUserModify(Proyecto proyecto, String userUid, String userRole) {
    if (proyecto == null) return false;
    if ("ADMIN".equals(userRole)) return true;
    return userUid != null && userUid.equals(proyecto.getCreadorUid());
  }

  public static void validarEstado(String estado) {
    if (estado != null && !estado.isBlank()) {
      if (!ESTADOS_VALIDOS.contains(estado.toUpperCase())) {
        throw new ProyectoValidationException("El estado '" + estado + "' no es válido.");
      }
    }
  }

  public static void validarItemsRequeridos(List<ItemRequerido> items, CatalogItemChecker checker) throws Exception {
    if (items == null || items.isEmpty()) return;
    List<ItemRequerido> limpios = new ArrayList<>();
    for (var item : items) {
      if (item == null || item.getItemId() == null || item.getItemId().isBlank()) continue;
      if (item.getCantidad() <= 0) {
        throw new ProyectoValidationException("La cantidad para el ítem '" + item.getItemId() + "' debe ser mayor a 0.");
      }
      if (checker != null && !checker.exists(item.getItemId())) {
        throw new ProyectoValidationException("El ítem '" + item.getItemId() + "' no existe en el catálogo.");
      }
      limpios.add(item);
    }
  }
}
