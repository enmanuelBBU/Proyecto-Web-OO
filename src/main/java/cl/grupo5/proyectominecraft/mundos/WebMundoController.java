package cl.grupo5.proyectominecraft.mundos;

import cl.grupo5.proyectominecraft.proyectos.ProyectoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
  public String detail(@PathVariable String id, Model m, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    var mundo = mundos.get(id);
    if (!MundoService.puedeGestionar(mundo, uid, rol(s))) return noEncontrado(ra);
    m.addAttribute("mundo", mundo);
    m.addAttribute("proyectos", proyectos.listarPorMundo(id));
    m.addAttribute("isAdmin", "ADMIN".equals(rol(s)));
    return "mundo-detalle";
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
