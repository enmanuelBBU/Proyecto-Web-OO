package cl.grupo5.proyectominecraft.mundos;

import cl.grupo5.proyectominecraft.proyectos.ProyectoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class WebMundoController {
  private static final String NO_ENCONTRADO = "Mundo no encontrado.";

  private final MundoService mundos;
  private final ProyectoService proyectos;

  public WebMundoController(MundoService mundos, ProyectoService proyectos) {
    this.mundos = mundos;
    this.proyectos = proyectos;
  }

  @GetMapping("/mundos")
  public String list(Model m, HttpSession s) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    prepararLista(m, s, uid);
    return "mundos";
  }

  @PostMapping("/mundos")
  public String create(@RequestParam(required = false) String nombre,
                       @RequestParam(required = false) String version,
                       @RequestParam(required = false) String modoJuego,
                       @RequestParam(required = false) String semilla,
                       Model m, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    try {
      mundos.crear(desdeFormulario(nombre, version, modoJuego, semilla), uid);
    } catch (MundoValidationException e) {
      prepararLista(m, s, uid);
      m.addAttribute("error", e.getMessage());
      m.addAttribute("toastError", e.getMessage());
      return "mundos";
    }
    ra.addFlashAttribute("toastSuccess", "Mundo creado correctamente");
    return "redirect:/mundos";
  }

  @GetMapping("/mundos/{id}")
  public String detail(@PathVariable String id,
                       @RequestParam(required = false) String dimension,
                       @RequestParam(required = false) Integer x,
                       @RequestParam(required = false) Integer y,
                       @RequestParam(required = false) Integer z,
                       Model m, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    var mundo = mundos.get(id);
    if (!MundoService.puedeGestionar(mundo, uid, rol(s))) return noEncontrado(ra);
    var portales = PortalService.portales(mundo);
    Map<String, Portal> porId = new LinkedHashMap<>();
    for (var p : portales) porId.put(p.getId(), p);
    m.addAttribute("mundo", mundo);
    m.addAttribute("portales", portales);
    m.addAttribute("portalesPorId", porId);
    m.addAttribute("proyectos", proyectos.listarPorMundo(id));
    m.addAttribute("isAdmin", "ADMIN".equals(rol(s)));
    WebNetherController.exponerConversion(m, dimension, x, y, z);
    return "mundo-detalle";
  }

  // ---- Portales ----

  @PostMapping("/mundos/{id}/portales")
  public String crearPortal(@PathVariable String id,
                            @RequestParam(required = false) String nombre,
                            @RequestParam(required = false) String dimension,
                            @RequestParam(required = false) Integer x,
                            @RequestParam(required = false) Integer y,
                            @RequestParam(required = false) Integer z,
                            @RequestParam(defaultValue = "false") boolean pareado,
                            HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    var mundo = mundos.get(id);
    if (!MundoService.puedeGestionar(mundo, uid, rol(s))) return noEncontrado(ra);
    try {
      var p = PortalService.agregar(mundo, portalDesdeFormulario(nombre, dimension, x, y, z, null));
      if (pareado) PortalService.crearPareado(mundo, p.getId());
    } catch (MundoValidationException e) {
      return errorPortal(ra, id, e.getMessage());
    }
    mundos.guardarPortales(mundo);
    ra.addFlashAttribute("toastSuccess", pareado ? "Portal y su par guardados" : "Portal guardado");
    return volverAPortales(id);
  }

  @PostMapping("/mundos/{id}/portales/{pid}/pareado")
  public String pareado(@PathVariable String id, @PathVariable String pid,
                        HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    var mundo = mundos.get(id);
    if (!MundoService.puedeGestionar(mundo, uid, rol(s))) return noEncontrado(ra);
    try {
      PortalService.crearPareado(mundo, pid);
    } catch (MundoValidationException e) {
      return errorPortal(ra, id, e.getMessage());
    }
    mundos.guardarPortales(mundo);
    ra.addFlashAttribute("toastSuccess", "Portal pareado creado");
    return volverAPortales(id);
  }

  @PostMapping("/mundos/{id}/portales/{pid}/update")
  public String actualizarPortal(@PathVariable String id, @PathVariable String pid,
                                 @RequestParam(required = false) String nombre,
                                 @RequestParam(required = false) String dimension,
                                 @RequestParam(required = false) Integer x,
                                 @RequestParam(required = false) Integer y,
                                 @RequestParam(required = false) Integer z,
                                 @RequestParam(required = false) String enlazadoId,
                                 HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    var mundo = mundos.get(id);
    if (!MundoService.puedeGestionar(mundo, uid, rol(s))) return noEncontrado(ra);
    try {
      PortalService.actualizar(mundo, pid, portalDesdeFormulario(nombre, dimension, x, y, z, enlazadoId));
    } catch (MundoValidationException e) {
      return errorPortal(ra, id, e.getMessage());
    }
    mundos.guardarPortales(mundo);
    ra.addFlashAttribute("toastSuccess", "Portal actualizado");
    return volverAPortales(id);
  }

  @PostMapping("/mundos/{id}/portales/{pid}/delete")
  public String eliminarPortal(@PathVariable String id, @PathVariable String pid,
                               HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    var mundo = mundos.get(id);
    if (!MundoService.puedeGestionar(mundo, uid, rol(s))) return noEncontrado(ra);
    try {
      PortalService.eliminar(mundo, pid);
    } catch (MundoValidationException e) {
      return errorPortal(ra, id, e.getMessage());
    }
    mundos.guardarPortales(mundo);
    ra.addFlashAttribute("toastSuccess", "Portal eliminado");
    return volverAPortales(id);
  }

  static Portal portalDesdeFormulario(String nombre, String dimension, Integer x, Integer y, Integer z, String enlazadoId) {
    if (x == null || y == null || z == null) {
      throw new MundoValidationException("Las coordenadas X, Y y Z del portal son obligatorias.");
    }
    var p = new Portal();
    p.setNombre(nombre);
    p.setDimension(dimension);
    p.setX(x);
    p.setY(y);
    p.setZ(z);
    p.setEnlazadoId(enlazadoId);
    return p;
  }

  private static String volverAPortales(String id) {
    return "redirect:/mundos/" + id + "#portales";
  }

  private static String errorPortal(RedirectAttributes ra, String id, String mensaje) {
    ra.addFlashAttribute("toastError", mensaje);
    return volverAPortales(id);
  }

  @GetMapping("/mundos/{id}/edit")
  public String edit(@PathVariable String id, Model m, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    var mundo = mundos.get(id);
    if (!MundoService.puedeGestionar(mundo, uid, rol(s))) return noEncontrado(ra);
    m.addAttribute("mundo", mundo);
    m.addAttribute("modos", ModoJuego.values());
    m.addAttribute("isAdmin", "ADMIN".equals(rol(s)));
    return "mundo-edit";
  }

  @PostMapping("/mundos/{id}/update")
  public String update(@PathVariable String id,
                       @RequestParam(required = false) String nombre,
                       @RequestParam(required = false) String version,
                       @RequestParam(required = false) String modoJuego,
                       @RequestParam(required = false) String semilla,
                       Model m, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    var cambios = desdeFormulario(nombre, version, modoJuego, semilla);
    try {
      if (mundos.actualizar(id, cambios, uid, rol(s)) == null) return noEncontrado(ra);
    } catch (MundoValidationException e) {
      cambios.setId(id);
      m.addAttribute("mundo", cambios);
      m.addAttribute("modos", ModoJuego.values());
      m.addAttribute("isAdmin", "ADMIN".equals(rol(s)));
      m.addAttribute("error", e.getMessage());
      m.addAttribute("toastError", e.getMessage());
      return "mundo-edit";
    }
    ra.addFlashAttribute("toastSuccess", "Mundo actualizado correctamente");
    return "redirect:/mundos/" + id;
  }

  @PostMapping("/mundos/{id}/delete")
  public String delete(@PathVariable String id, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    try {
      if (!mundos.eliminar(id, uid, rol(s))) return noEncontrado(ra);
    } catch (MundoValidationException e) {
      ra.addFlashAttribute("toastError", e.getMessage());
      return "redirect:/mundos/" + id;
    }
    ra.addFlashAttribute("toastSuccess", "Mundo eliminado correctamente");
    return "redirect:/mundos";
  }

  private void prepararLista(Model m, HttpSession s, String uid) throws Exception {
    m.addAttribute("mundos", mundos.listar(uid));
    m.addAttribute("modos", ModoJuego.values());
    m.addAttribute("isAdmin", "ADMIN".equals(rol(s)));
  }

  private static Mundo desdeFormulario(String nombre, String version, String modoJuego, String semilla) {
    var mundo = new Mundo();
    mundo.setNombre(nombre);
    mundo.setVersion(version);
    mundo.setModoJuego(modoJuego);
    mundo.setSemilla(semilla);
    return mundo;
  }

  private static String noEncontrado(RedirectAttributes ra) {
    ra.addFlashAttribute("toastError", NO_ENCONTRADO);
    return "redirect:/mundos";
  }

  private static String uid(HttpSession s) {
    return (String) s.getAttribute("uid");
  }

  private static String rol(HttpSession s) {
    return (String) s.getAttribute("rol");
  }
}
