package cl.grupo5.proyectominecraft.proyectos;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Comentarios de proyecto en la subcolección {@code proyectos/{id}/comentarios}.
 * Los permisos sobre el proyecto los resuelve quien llama con {@link ProyectoService#canView}.
 */
@Service
public class ComentarioService {
  public static final String SUBCOLECCION = "comentarios";
  public static final int MAX_LARGO = 1000;

  private final Firestore db;

  public ComentarioService(Firestore db) {
    this.db = db;
  }

  public List<Comentario> listar(String proyectoId) throws Exception {
    var docs = col(proyectoId).get().get().getDocuments();
    return ordenar(docs.stream().map(d -> {
      var c = d.toObject(Comentario.class);
      c.setId(d.getId());
      return c;
    }).toList());
  }

  public Comentario get(String proyectoId, String cid) throws Exception {
    if (cid == null || cid.isBlank()) return null;
    var snap = col(proyectoId).document(cid).get().get();
    if (!snap.exists()) return null;
    var c = snap.toObject(Comentario.class);
    c.setId(cid);
    return c;
  }

  public Comentario crear(String proyectoId, String uid, String nombre, String texto) throws Exception {
    var c = nuevo(proyectoId, uid, nombre, texto, Instant.now().toString());
    var ref = col(proyectoId).document();
    c.setId(ref.getId());
    ref.set(c).get();
    return c;
  }

  public Comentario editar(String proyectoId, Comentario c, String texto) throws Exception {
    c.setTexto(normalizarTexto(texto));
    c.setFechaEdicion(Instant.now().toString());
    col(proyectoId).document(c.getId()).set(c).get();
    return c;
  }

  public void eliminar(String proyectoId, String cid) throws Exception {
    col(proyectoId).document(cid).delete().get();
  }

  private CollectionReference col(String proyectoId) {
    return db.collection("proyectos").document(proyectoId).collection(SUBCOLECCION);
  }

  // ---- Reglas (puras) ----

  static String normalizarTexto(String texto) {
    var t = texto == null ? "" : texto.trim();
    if (t.isEmpty()) throw new ComentarioValidationException("El comentario no puede estar vacío.");
    if (t.length() > MAX_LARGO) {
      throw new ComentarioValidationException("El comentario no puede superar los " + MAX_LARGO + " caracteres.");
    }
    return t;
  }

  static Comentario nuevo(String proyectoId, String uid, String nombre, String texto, String fecha) {
    var c = new Comentario();
    c.setProyectoId(proyectoId);
    c.setAutorUid(uid);
    c.setAutorNombre(nombre != null && !nombre.isBlank() ? nombre : "Anónimo");
    c.setTexto(normalizarTexto(texto));
    c.setFechaCreacion(fecha);
    return c;
  }

  public static boolean puedeComentar(Proyecto p, String uid, String rol) {
    return ProyectoService.canView(p, uid, rol);
  }

  public static boolean puedeEditar(Comentario c, String uid) {
    return c != null && uid != null && uid.equals(c.getAutorUid());
  }

  /** Autor, creador del proyecto o admin. */
  public static boolean puedeBorrar(Comentario c, Proyecto p, String uid, String rol) {
    return c != null && (puedeEditar(c, uid) || ProyectoService.canManage(p, uid, rol));
  }

  /** Borrar un comentario ajeno: se registra en auditoría. */
  public static boolean esModeracion(Comentario c, String uid) {
    return !puedeEditar(c, uid);
  }

  static List<Comentario> ordenar(List<Comentario> comentarios) {
    return comentarios.stream()
        .sorted(Comparator.comparing(Comentario::getFechaCreacion, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }
}
