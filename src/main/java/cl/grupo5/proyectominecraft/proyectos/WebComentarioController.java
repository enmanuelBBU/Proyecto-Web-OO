package cl.grupo5.proyectominecraft.proyectos;

import cl.grupo5.proyectominecraft.auditoria.AuditService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebComentarioController {
  static final String NO_ENCONTRADO = "Comentario no encontrado.";
  static final String SOLO_AUTOR = "Solo el autor puede editar este comentario.";
  static final String SIN_PERMISO_BORRAR = "No tienes permisos para borrar este comentario.";

  private final ProyectoService proyectos;
  private final ComentarioService comentarios;
  private final AuditService audit;

  public WebComentarioController(ProyectoService proyectos, ComentarioService comentarios, AuditService audit) {
    this.proyectos = proyectos;
    this.comentarios = comentarios;
    this.audit = audit;
  }

  @PostMapping("/proyectos/{id}/comentarios")
  public String crear(@PathVariable String id, @RequestParam(required = false) String texto,
                      HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    var p = proyectos.getVisible(id, uid, rol(s));
    if (p == null) return proyectoNoEncontrado(ra);
    try {
      comentarios.crear(id, uid, nombre(s), texto);
    } catch (ComentarioValidationException e) {
      ra.addFlashAttribute("toastError", e.getMessage());
      return volver(id);
    }
    ra.addFlashAttribute("toastSuccess", "Comentario publicado");
    return volver(id);
  }

  @PostMapping("/proyectos/{id}/comentarios/{cid}/update")
  public String editar(@PathVariable String id, @PathVariable String cid,
                       @RequestParam(required = false) String texto,
                       HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    var p = proyectos.getVisible(id, uid, rol(s));
    if (p == null) return proyectoNoEncontrado(ra);
    var c = comentarios.get(id, cid);
    if (c == null) return error(ra, id, NO_ENCONTRADO);
    if (!ComentarioService.puedeEditar(c, uid)) return error(ra, id, SOLO_AUTOR);
    try {
      comentarios.editar(id, c, texto);
    } catch (ComentarioValidationException e) {
      return error(ra, id, e.getMessage());
    }
    ra.addFlashAttribute("toastSuccess", "Comentario actualizado");
    return volver(id);
  }

  @PostMapping("/proyectos/{id}/comentarios/{cid}/delete")
  public String eliminar(@PathVariable String id, @PathVariable String cid,
                         HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    var p = proyectos.getVisible(id, uid, rol(s));
    if (p == null) return proyectoNoEncontrado(ra);
    var c = comentarios.get(id, cid);
    if (c == null) return error(ra, id, NO_ENCONTRADO);
    if (!ComentarioService.puedeBorrar(c, p, uid, rol(s))) return error(ra, id, SIN_PERMISO_BORRAR);
    comentarios.eliminar(id, cid);
    if (ComentarioService.esModeracion(c, uid)) {
      audit.registrar(s, AuditService.ELIMINAR, AuditService.COMENTARIO, cid, p.getNombre(), detalleModeracion(c));
    }
    ra.addFlashAttribute("toastSuccess", "Comentario eliminado");
    return volver(id);
  }

  static String detalleModeracion(Comentario c) {
    return "Comentario de " + (c.getAutorNombre() != null ? c.getAutorNombre() : c.getAutorUid());
  }

  private static String volver(String id) {
    return "redirect:/proyectos/" + id + "#comentarios";
  }

  private static String error(RedirectAttributes ra, String id, String mensaje) {
    ra.addFlashAttribute("toastError", mensaje);
    return volver(id);
  }

  private static String proyectoNoEncontrado(RedirectAttributes ra) {
    ra.addFlashAttribute("toastError", "Proyecto no encontrado.");
    return "redirect:/proyectos";
  }

  private static String uid(HttpSession s) {
    return (String) s.getAttribute("uid");
  }

  private static String rol(HttpSession s) {
    return (String) s.getAttribute("rol");
  }

  private static String nombre(HttpSession s) {
    String nombre = (String) s.getAttribute("nombre");
    return nombre != null ? nombre : (String) s.getAttribute("usuario");
  }
}
