package cl.grupo5.proyectominecraft.proyectos;

import cl.grupo5.proyectominecraft.auth.AuthService;
import cl.grupo5.proyectominecraft.items.Slugs;
import cl.grupo5.proyectominecraft.mundos.Mundo;
import cl.grupo5.proyectominecraft.mundos.MundoService;
import cl.grupo5.proyectominecraft.perfil.UserProfile;
import cl.grupo5.proyectominecraft.perfil.UserProfileService;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ProyectoService {


  @FunctionalInterface
  public interface CatalogItemChecker {
    boolean exists(String itemId) throws Exception;
  }

  @FunctionalInterface
  public interface MundoLookup {
    Mundo get(String id) throws Exception;
  }

  public static final String LECTURA = "LECTURA";
  public static final String EDICION = "EDICION";
  static final String SIN_PERMISO_MIEMBROS = "No tienes permisos para gestionar los miembros de este proyecto.";

  private final Firestore db;
  private final MundoService mundoService;
  private final UserProfileService userProfiles;
  private final AuthService auth;

  public ProyectoService(Firestore db, MundoService mundoService, UserProfileService userProfiles, AuthService auth) {
    this.db = db;
    this.mundoService = mundoService;
    this.userProfiles = userProfiles;
    this.auth = auth;
  }

  @FunctionalInterface
  interface UidPorEmail {
    String uid(String email) throws Exception;
  }

  @FunctionalInterface
  interface PerfilPorUid {
    UserProfile get(String uid) throws Exception;
  }

  /**
   * Resuelve al invitado por su cuenta de Firebase Auth (email único), no por el email editable del perfil:
   * así nadie recibe invitaciones dirigidas a otra persona poniéndose su email en el perfil.
   */
  static UserProfile resolverInvitado(String email, UidPorEmail cuentas, PerfilPorUid perfiles) throws Exception {
    var limpio = email == null ? "" : email.trim();
    var uid = limpio.isEmpty() ? null : cuentas.uid(limpio);
    if (uid == null) throw new ProyectoValidationException("No existe un usuario registrado con ese email.");
    var perfil = perfiles.get(uid);
    var invitado = new UserProfile();
    invitado.setUid(uid);
    invitado.setEmail(limpio.toLowerCase());
    invitado.setNombre(perfil != null && perfil.getNombre() != null ? perfil.getNombre() : limpio.split("@")[0]);
    return invitado;
  }

  public List<Proyecto> list(String q, String estado, String creadorUid, String viewerUid, String viewerRol) throws Exception {
    return filter(visiblePara(todos(), viewerUid, viewerRol), q, estado, creadorUid);
  }

  private List<Proyecto> todos() throws Exception {
    var docs = db.collection("proyectos").get().get().getDocuments();
    return docs.stream().map(d -> {
      var p = d.toObject(Proyecto.class);
      p.setId(d.getId());
      return p;
    }).toList();
  }

  private List<Proyecto> delCreador(String creadorUid) throws Exception {
    var docs = db.collection("proyectos").whereEqualTo("creadorUid", creadorUid).get().get().getDocuments();
    return docs.stream().map(d -> {
      var p = d.toObject(Proyecto.class);
      p.setId(d.getId());
      return p;
    }).toList();
  }

  public Proyecto getVisible(String id, String uid, String rol) throws Exception {
    var p = get(id);
    return canView(p, uid, rol) ? p : null;
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

  public List<Proyecto> listarPorMundo(String mundoId) throws Exception {
    var docs = db.collection("proyectos").whereEqualTo("mundoId", mundoId).get().get().getDocuments();
    return docs.stream().map(d -> {
      var p = d.toObject(Proyecto.class);
      p.setId(d.getId());
      return p;
    }).toList();
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

    if (nombreDuplicado(delCreador(uid), proyecto.getNombre(), null)) {
      throw new ProyectoAlreadyExistsException(proyecto.getNombre().trim());
    }

    validarEstado(proyecto.getEstado());
    validarItemsRequeridos(proyecto.getItemsRequeridos(),
        itemId -> db.collection("items").document(itemId).get().get().exists());
    validarUbicacion(proyecto, uid, mundoService::get);

    var ref = db.collection("proyectos").document();
    String id = ref.getId();
    proyecto.setId(id);
    proyecto.setCreadorUid(uid);
    proyecto.setMiembros(new ArrayList<>());
    proyecto.setMiembroUids(new ArrayList<>());
    proyecto.setCreadorNombre(nombreUsuario != null ? nombreUsuario : "Anónimo");
    if (proyecto.getFechaCreacion() == null) {
      proyecto.setFechaCreacion(Instant.now().toString());
    }

    ref.set(proyecto).get();
    return proyecto;
  }

  public Proyecto update(String id, Proyecto proyecto, String userUid, String userRole) throws Exception {
    var snap = db.collection("proyectos").document(id).get().get();
    if (!snap.exists()) return null;

    var actual = snap.toObject(Proyecto.class);
    actual.setId(id);

    if (!canEdit(actual, userUid, userRole)) {
      throw new ProyectoValidationException("No tienes permisos para modificar este proyecto.");
    }

    if (proyecto.getNombre() == null || proyecto.getNombre().isBlank()) {
      throw new ProyectoValidationException("El nombre del proyecto es obligatorio.");
    }

    validarEstado(proyecto.getEstado());
    validarItemsRequeridos(proyecto.getItemsRequeridos(),
        itemId -> db.collection("items").document(itemId).get().get().exists());
    validarUbicacion(proyecto, actual.getCreadorUid(), mundoService::get);
    if (nombreDuplicado(delCreador(actual.getCreadorUid()), proyecto.getNombre(), id)) {
      throw new ProyectoAlreadyExistsException(proyecto.getNombre().trim());
    }

    var aGuardar = prepararActualizacion(actual, proyecto, id);
    db.collection("proyectos").document(id).set(aGuardar).get();
    return aGuardar;
  }

  public boolean delete(String id, String userUid, String userRole) throws Exception {
    var snap = db.collection("proyectos").document(id).get().get();
    if (!snap.exists()) return false;

    var actual = snap.toObject(Proyecto.class);

    if (!canManage(actual, userUid, userRole)) {
      throw new ProyectoValidationException("No tienes permisos para eliminar este proyecto.");
    }

    // El proyecto y sus comentarios se borran juntos (Firestore no borra subcolecciones por sí solo).
    var ref = db.collection("proyectos").document(id);
    var batch = db.batch();
    for (var c : ref.collection(ComentarioService.SUBCOLECCION).get().get().getDocuments()) {
      batch.delete(c.getReference());
    }
    batch.delete(ref);
    batch.commit().get();
    return true;
  }

  public Proyecto transicionarEstado(String id, String nuevoEstado, String motivo, String userUid, String userNombre, String userRole) throws Exception {
    var snap = db.collection("proyectos").document(id).get().get();
    if (!snap.exists()) return null;

    var actual = snap.toObject(Proyecto.class);
    actual.setId(id);

    if (!canEdit(actual, userUid, userRole)) {
      throw new ProyectoValidationException("No tienes permisos para cambiar el estado de este proyecto.");
    }

    String estadoActual = actual.getEstado();
    if (estadoActual == null) estadoActual = "PLANIFICACION";

    EstadoProyecto estadoEnum = EstadoProyecto.fromString(estadoActual);
    if (!estadoEnum.puedeTransicionarA(nuevoEstado)) {
      throw new ProyectoValidationException("Transición inválida: de " + estadoActual + " a " + nuevoEstado);
    }

    EstadoProyecto nuevoEstadoEnum = EstadoProyecto.fromString(nuevoEstado);

    if (actual.getHistorialEstados() == null) {
      actual.setHistorialEstados(new ArrayList<>());
    }

    HistorialEstado historial = new HistorialEstado(
        estadoActual,
        nuevoEstadoEnum.name(),
        userUid,
        userNombre != null ? userNombre : "Anónimo",
        Instant.now().toString(),
        motivo
    );

    actual.getHistorialEstados().add(historial);
    actual.setEstado(nuevoEstadoEnum.name());

    db.collection("proyectos").document(id).set(actual).get();
    return actual;
  }

  public static void validarUbicacion(Proyecto p, String creadorUid, MundoLookup mundos) throws Exception {
    if (p.getMundoId() == null || p.getMundoId().isBlank()) {
      p.setMundoId(null);
      p.setX(null);
      p.setY(null);
      p.setZ(null);
      return;
    }
    p.setMundoId(p.getMundoId().trim());
    var mundo = mundos.get(p.getMundoId());
    if (mundo == null) throw new ProyectoValidationException("El mundo seleccionado no existe.");
    if (creadorUid == null || !creadorUid.equals(mundo.getUid())) {
      throw new ProyectoValidationException("El mundo debe pertenecer al creador del proyecto.");
    }
    if (p.getY() != null && (p.getY() < -64 || p.getY() > 320)) {
      throw new ProyectoValidationException("La coordenada Y debe estar entre -64 y 320.");
    }
    if (fueraDeLimite(p.getX()) || fueraDeLimite(p.getZ())) {
      throw new ProyectoValidationException("Las coordenadas X y Z deben estar entre -30.000.000 y 30.000.000.");
    }
  }

  private static boolean fueraDeLimite(Integer c) {
    return c != null && (c < -30_000_000 || c > 30_000_000);
  }

  // ---- Acceso ----

  public static boolean canView(Proyecto p, String uid, String rol) {
    if (p == null) return false;
    return canManage(p, uid, rol) || permisoDe(p, uid) != null;
  }

  public static boolean canEdit(Proyecto p, String uid, String rol) {
    if (p == null) return false;
    return canManage(p, uid, rol) || EDICION.equals(permisoDe(p, uid));
  }

  public static boolean canManage(Proyecto p, String uid, String rol) {
    if (p == null) return false;
    if ("ADMIN".equals(rol)) return true;
    return uid != null && uid.equals(p.getCreadorUid());
  }

  public static String permisoDe(Proyecto p, String uid) {
    if (p == null || uid == null || p.getMiembros() == null) return null;
    return p.getMiembros().stream()
        .filter(m -> m != null && uid.equals(m.getUid()))
        .map(Miembro::getPermiso)
        .findFirst().orElse(null);
  }

  public static List<Proyecto> visiblePara(List<Proyecto> todos, String uid, String rol) {
    return todos.stream().filter(p -> canView(p, uid, rol)).toList();
  }

  public static boolean nombreDuplicado(List<Proyecto> delCreador, String nombre, String excluirId) {
    if (nombre == null) return false;
    var slug = Slugs.slugify(nombre);
    return delCreador.stream()
        .filter(p -> excluirId == null || !excluirId.equals(p.getId()))
        .anyMatch(p -> p.getNombre() != null && Slugs.slugify(p.getNombre()).equals(slug));
  }

  /** Arma el documento a guardar: datos editables de {@code cambios}; metadatos, historial y miembros de {@code actual}. */
  static Proyecto prepararActualizacion(Proyecto actual, Proyecto cambios, String id) {
    cambios.setId(id);
    cambios.setCreadorUid(actual.getCreadorUid());
    cambios.setCreadorNombre(actual.getCreadorNombre());
    cambios.setFechaCreacion(actual.getFechaCreacion());
    cambios.setHistorialEstados(actual.getHistorialEstados() != null ? actual.getHistorialEstados() : new ArrayList<>());
    cambios.setMiembros(actual.getMiembros() != null ? actual.getMiembros() : new ArrayList<>());
    cambios.setMiembroUids(actual.getMiembroUids() != null ? actual.getMiembroUids() : new ArrayList<>());
    return cambios;
  }

  // ---- Miembros ----

  static String normalizarPermiso(String permiso) {
    var p = permiso == null ? "" : permiso.trim().toUpperCase();
    if (!LECTURA.equals(p) && !EDICION.equals(p)) throw new ProyectoValidationException("Permiso inválido.");
    return p;
  }

  static void agregarMiembro(Proyecto p, UserProfile u, String permiso, String fecha) {
    var normalizado = normalizarPermiso(permiso);
    if (u.getUid() != null && u.getUid().equals(p.getCreadorUid())) {
      throw new ProyectoValidationException("No puedes invitar al creador del proyecto.");
    }
    if (p.getMiembros() == null) p.setMiembros(new ArrayList<>());
    var existente = p.getMiembros().stream().filter(m -> u.getUid().equals(m.getUid())).findFirst();
    if (existente.isPresent()) {
      existente.get().setPermiso(normalizado);
    } else {
      p.getMiembros().add(new Miembro(u.getUid(), u.getNombre(), u.getEmail(), normalizado, fecha));
    }
    sincronizarUids(p);
  }

  static void cambiarPermiso(Proyecto p, String uid, String permiso) {
    var normalizado = normalizarPermiso(permiso);
    buscarMiembro(p, uid).setPermiso(normalizado);
  }

  static void quitarMiembro(Proyecto p, String uid) {
    p.getMiembros().remove(buscarMiembro(p, uid));
    sincronizarUids(p);
  }

  private static Miembro buscarMiembro(Proyecto p, String uid) {
    if (p.getMiembros() == null) throw new ProyectoValidationException("El usuario no es miembro del proyecto.");
    return p.getMiembros().stream().filter(m -> m.getUid() != null && m.getUid().equals(uid)).findFirst()
        .orElseThrow(() -> new ProyectoValidationException("El usuario no es miembro del proyecto."));
  }

  private static void sincronizarUids(Proyecto p) {
    p.setMiembroUids(new ArrayList<>(p.getMiembros().stream().map(Miembro::getUid).toList()));
  }

  public Proyecto invitar(String id, String email, String permiso, String uid, String rol) throws Exception {
    var p = get(id);
    if (p == null) return null;
    if (!canManage(p, uid, rol)) throw new ProyectoValidationException(SIN_PERMISO_MIEMBROS);
    var usuario = resolverInvitado(email, auth::uidPorEmail, userProfiles::get);
    agregarMiembro(p, usuario, permiso, Instant.now().toString());
    db.collection("proyectos").document(id).set(p).get();
    return p;
  }

  public Proyecto cambiarPermisoMiembro(String id, String miembroUid, String permiso, String uid, String rol) throws Exception {
    var p = get(id);
    if (p == null) return null;
    if (!canManage(p, uid, rol)) throw new ProyectoValidationException(SIN_PERMISO_MIEMBROS);
    cambiarPermiso(p, miembroUid, permiso);
    db.collection("proyectos").document(id).set(p).get();
    return p;
  }

  public Proyecto quitarMiembro(String id, String miembroUid, String uid, String rol) throws Exception {
    var p = get(id);
    if (p == null) return null;
    if (!canManage(p, uid, rol)) throw new ProyectoValidationException(SIN_PERMISO_MIEMBROS);
    quitarMiembro(p, miembroUid);
    db.collection("proyectos").document(id).set(p).get();
    return p;
  }


  public static void validarEstado(String estado) {
    if (estado != null && !estado.isBlank()) {
      try {
        EstadoProyecto.fromString(estado);
      } catch (IllegalArgumentException e) {
        throw new ProyectoValidationException(e.getMessage());
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
