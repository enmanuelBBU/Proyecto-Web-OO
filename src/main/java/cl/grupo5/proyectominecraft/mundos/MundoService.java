package cl.grupo5.proyectominecraft.mundos;

import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MundoService {
  static final String PERMISO_DENEGADO = "No tienes permisos sobre este mundo.";

  private final Firestore db;

  public MundoService(Firestore db) {
    this.db = db;
  }

  public List<Mundo> listar(String uid) throws Exception {
    var docs = db.collection("mundos").whereEqualTo("uid", uid).get().get().getDocuments();
    var mundos = docs.stream().map(d -> {
      var m = d.toObject(Mundo.class);
      m.setId(d.getId());
      return m;
    }).toList();
    return delUsuario(mundos, uid);
  }

  public Mundo get(String id) throws Exception {
    if (id == null || id.isBlank()) return null;
    var snap = db.collection("mundos").document(id).get().get();
    if (!snap.exists()) return null;
    var m = snap.toObject(Mundo.class);
    m.setId(id);
    return m;
  }

  public Mundo crear(Mundo m, String uid) throws Exception {
    validar(m);
    var ref = db.collection("mundos").document();
    m.setId(ref.getId());
    m.setUid(uid);
    m.setFechaCreacion(Instant.now().toString());
    ref.set(m).get();
    return m;
  }

  public Mundo actualizar(String id, Mundo cambios, String uid, String rol) throws Exception {
    var actual = get(id);
    if (actual == null) return null;
    if (!puedeGestionar(actual, uid, rol)) throw new MundoValidationException(PERMISO_DENEGADO);
    validar(cambios);
    var aGuardar = prepararActualizacion(actual, cambios, id);
    db.collection("mundos").document(id).set(aGuardar).get();
    return aGuardar;
  }

  /** Datos editables de {@code cambios}; dueño, fecha y portales de {@code actual} (el formulario no los trae). */
  static Mundo prepararActualizacion(Mundo actual, Mundo cambios, String id) {
    cambios.setId(id);
    cambios.setUid(actual.getUid());
    cambios.setFechaCreacion(actual.getFechaCreacion());
    cambios.setPortales(actual.getPortales() != null ? actual.getPortales() : new ArrayList<>());
    return cambios;
  }

  /** Persiste el mundo completo tras modificar sus portales con {@link PortalService}. */
  public Mundo guardarPortales(Mundo m) throws Exception {
    if (m.getPortales() == null) m.setPortales(new ArrayList<>());
    db.collection("mundos").document(m.getId()).set(m).get();
    return m;
  }

  public boolean eliminar(String id, String uid, String rol) throws Exception {
    var actual = get(id);
    if (actual == null) return false;
    if (!puedeGestionar(actual, uid, rol)) throw new MundoValidationException(PERMISO_DENEGADO);
    int asignados = db.collection("proyectos").whereEqualTo("mundoId", id).get().get().size();
    if (asignados > 0) {
      throw new MundoValidationException("El mundo tiene " + asignados + " proyecto(s) asignado(s).");
    }
    db.collection("mundos").document(id).delete().get();
    return true;
  }

  static void validar(Mundo m) {
    if (m.getNombre() == null || m.getNombre().isBlank()) {
      throw new MundoValidationException("El nombre del mundo es obligatorio.");
    }
    if (m.getVersion() == null || m.getVersion().isBlank()) {
      throw new MundoValidationException("La versión es obligatoria.");
    }
    m.setNombre(m.getNombre().trim());
    m.setVersion(m.getVersion().trim());
    if (m.getModoJuego() == null || m.getModoJuego().isBlank()) {
      m.setModoJuego(ModoJuego.SUPERVIVENCIA.name());
    } else {
      try {
        m.setModoJuego(ModoJuego.fromString(m.getModoJuego()).name());
      } catch (IllegalArgumentException e) {
        throw new MundoValidationException(e.getMessage());
      }
    }
    m.setSemilla(m.getSemilla() == null || m.getSemilla().isBlank() ? null : m.getSemilla().trim());
  }

  public static boolean puedeGestionar(Mundo m, String uid, String rol) {
    if (m == null) return false;
    if ("ADMIN".equals(rol)) return true;
    return uid != null && uid.equals(m.getUid());
  }

  static List<Mundo> delUsuario(List<Mundo> todos, String uid) {
    return todos.stream()
        .filter(m -> uid != null && uid.equals(m.getUid()))
        .sorted(Comparator.comparing(m -> m.getNombre() == null ? "" : m.getNombre().toLowerCase()))
        .toList();
  }
}
