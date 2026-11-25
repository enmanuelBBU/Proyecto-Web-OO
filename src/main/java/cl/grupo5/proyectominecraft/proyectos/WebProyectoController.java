package cl.grupo5.proyectominecraft.proyectos;

import cl.grupo5.proyectominecraft.auditoria.AuditService;
import cl.grupo5.proyectominecraft.inventario.CraftingCalculatorService;
import cl.grupo5.proyectominecraft.inventario.InventoryException;
import cl.grupo5.proyectominecraft.items.Item;
import cl.grupo5.proyectominecraft.items.ItemService;
import cl.grupo5.proyectominecraft.mundos.Dimension;
import cl.grupo5.proyectominecraft.mundos.Mundo;
import cl.grupo5.proyectominecraft.mundos.MundoService;
import cl.grupo5.proyectominecraft.mundos.MundoValidationException;
import cl.grupo5.proyectominecraft.mundos.NetherCalculator;
import cl.grupo5.proyectominecraft.mundos.PortalService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class WebProyectoController {
  private static final String NO_ENCONTRADO = "Proyecto no encontrado.";
  private static final List<String> ESTADOS = List.of("PLANIFICACION", "EN_CONSTRUCCION", "COMPLETADO", "CANCELADO");

  private final ProyectoService proyectoService;
  private final ItemService itemService;
  private final MundoService mundoService;
  private final AuditService audit;
  private final CraftingCalculatorService calculator;
  private final ComentarioService comentarioService;

  public WebProyectoController(ProyectoService proyectoService, ItemService itemService, MundoService mundoService,
                               AuditService audit, CraftingCalculatorService calculator,
                               ComentarioService comentarioService) {
    this.comentarioService = comentarioService;
    this.proyectoService = proyectoService;
    this.itemService = itemService;
    this.mundoService = mundoService;
    this.audit = audit;
    this.calculator = calculator;
  }

  @GetMapping("/proyectos")
  public String list(@RequestParam(required = false) String q,
                     @RequestParam(required = false) String estado,
                     @RequestParam(required = false) Boolean misProyectos,
                     Model m, HttpSession s) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";

    String filterUid = Boolean.TRUE.equals(misProyectos) ? uid : null;
    List<Proyecto> lista = proyectoService.list(q, estado, filterUid, uid, rol(s));

    exponerLista(m, s, lista);
    m.addAttribute("q", q);
    m.addAttribute("estado", estado);
    m.addAttribute("misProyectos", misProyectos);
    return "proyectos";
  }

  @PostMapping("/proyectos")
  public String create(@RequestParam(required = false) String nombre,
                       @RequestParam(required = false) String descripcion,
                       @RequestParam(required = false) String estado,
                       @RequestParam(required = false) List<String> itemId,
                       @RequestParam(required = false) List<Integer> cantidad,
                       @RequestParam(required = false) String mundoId,
                       @RequestParam(required = false) Integer x,
                       @RequestParam(required = false) Integer y,
                       @RequestParam(required = false) Integer z,
                       Model m, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";

    Proyecto p = new Proyecto();
    p.setNombre(nombre);
    p.setDescripcion(descripcion);
    p.setEstado(estado != null && !estado.isBlank() ? estado : "PLANIFICACION");
    p.setItemsRequeridos(buildItemsRequeridos(itemId, cantidad));
    aplicarUbicacion(p, mundoId, x, y, z);

    try {
      proyectoService.create(p, uid, nombreUsuario(s));
    } catch (ProyectoAlreadyExistsException | ProyectoValidationException e) {
      exponerLista(m, s, proyectoService.list(null, null, null, uid, rol(s)));
      m.addAttribute("error", e.getMessage());
      m.addAttribute("toastError", e.getMessage());
      return "proyectos";
    }

    audit.registrar(s, AuditService.CREAR, AuditService.PROYECTO, p.getId(), p.getNombre(), null);
    ra.addFlashAttribute("toastSuccess", "Proyecto creado correctamente");
    return "redirect:/proyectos";
  }

  @GetMapping("/proyectos/{id}")
  public String detail(@PathVariable String id, Model m, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";

    Proyecto p = proyectoService.getVisible(id, uid, rol(s));
    if (p == null) return noEncontrado(ra);

    Map<String, Item> itemMap = new HashMap<>();
    for (Item item : itemService.list(null, null)) {
      itemMap.put(item.getId(), item);
    }

    var mundo = mundoService.get(p.getMundoId());
    m.addAttribute("proyecto", p);
    m.addAttribute("itemMap", itemMap);
    m.addAttribute("mundo", mundo);
    m.addAttribute("mundoEsPropio", MundoService.puedeGestionar(mundo, uid, rol(s)));
    m.addAttribute("canEdit", ProyectoService.canEdit(p, uid, rol(s)));
    m.addAttribute("canManage", ProyectoService.canManage(p, uid, rol(s)));
    m.addAttribute("permisos", List.of(ProyectoService.LECTURA, ProyectoService.EDICION));
    m.addAttribute("currentUid", uid);
    m.addAttribute("isAdmin", "ADMIN".equals(rol(s)));
    exponerFaltantes(m, p, uid);
    exponerComentarios(m, p, uid, rol(s));
    exponerNether(m, p, mundo);
    return "proyecto-detalle";
  }

  @GetMapping("/proyectos/{id}/edit")
  public String editForm(@PathVariable String id, Model m, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";

    Proyecto p = proyectoService.getVisible(id, uid, rol(s));
    if (p == null) return noEncontrado(ra);
    if (!ProyectoService.canEdit(p, uid, rol(s))) return "redirect:/proyectos/" + id;

    exponerFormularioEdicion(m, s, p);
    return "proyecto-edit";
  }

  @GetMapping("/proyectos/{id}/estados")
  public String estados(@PathVariable String id, Model m, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";

    Proyecto p = proyectoService.getVisible(id, uid, rol(s));
    if (p == null) return noEncontrado(ra);

    exponerEstados(m, s, p);
    return "proyecto-estados";
  }

  @PostMapping("/proyectos/{id}/transicionar")
  public String transicionar(@PathVariable String id,
                             @RequestParam String nuevoEstado,
                             @RequestParam(required = false) String motivo,
                             Model m, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";

    try {
      var actualizado = proyectoService.transicionarEstado(id, nuevoEstado, motivo, uid, nombreUsuario(s), rol(s));
      if (actualizado == null) return noEncontrado(ra);
      audit.registrar(s, AuditService.EDITAR, AuditService.PROYECTO, id, actualizado.getNombre(),
          AuditDetalles.transicion(actualizado));
    } catch (ProyectoValidationException e) {
      Proyecto p = proyectoService.getVisible(id, uid, rol(s));
      if (p == null) return noEncontrado(ra);
      exponerEstados(m, s, p);
      m.addAttribute("error", e.getMessage());
      m.addAttribute("toastError", e.getMessage());
      return "proyecto-estados";
    }

    ra.addFlashAttribute("toastSuccess", "Estado actualizado a " + nuevoEstado);
    return "redirect:/proyectos/" + id + "/estados";
  }

  @PostMapping("/proyectos/{id}/update")
  public String update(@PathVariable String id,
                       @RequestParam(required = false) String nombre,
                       @RequestParam(required = false) String descripcion,
                       @RequestParam(required = false) String estado,
                       @RequestParam(required = false) List<String> itemId,
                       @RequestParam(required = false) List<Integer> cantidad,
                       @RequestParam(required = false) String mundoId,
                       @RequestParam(required = false) Integer x,
                       @RequestParam(required = false) Integer y,
                       @RequestParam(required = false) Integer z,
                       Model m, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";

    Proyecto p = proyectoService.getVisible(id, uid, rol(s));
    if (p == null) return noEncontrado(ra);

    Proyecto updatePayload = new Proyecto();
    updatePayload.setNombre(nombre);
    updatePayload.setDescripcion(descripcion);
    updatePayload.setEstado(estado);
    updatePayload.setItemsRequeridos(buildItemsRequeridos(itemId, cantidad));
    aplicarUbicacion(updatePayload, mundoId, x, y, z);

    try {
      proyectoService.update(id, updatePayload, uid, rol(s));
    } catch (ProyectoValidationException | ProyectoAlreadyExistsException e) {
      exponerFormularioEdicion(m, s, p);
      m.addAttribute("error", e.getMessage());
      m.addAttribute("toastError", e.getMessage());
      return "proyecto-edit";
    }

    audit.registrar(s, AuditService.EDITAR, AuditService.PROYECTO, id, nombre, null);
    ra.addFlashAttribute("toastSuccess", "Proyecto actualizado correctamente");
    return "redirect:/proyectos/" + id;
  }

  @PostMapping("/proyectos/{id}/delete")
  public String delete(@PathVariable String id, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";

    var existente = proyectoService.getVisible(id, uid, rol(s));
    try {
      if (!proyectoService.delete(id, uid, rol(s))) return noEncontrado(ra);
    } catch (ProyectoValidationException e) {
      if (proyectoService.getVisible(id, uid, rol(s)) == null) return noEncontrado(ra);
      ra.addFlashAttribute("toastError", e.getMessage());
      return "redirect:/proyectos/" + id;
    }
    audit.registrar(s, AuditService.ELIMINAR, AuditService.PROYECTO, id,
        existente != null ? existente.getNombre() : id, null);
    ra.addFlashAttribute("toastSuccess", "Proyecto eliminado correctamente");
    return "redirect:/proyectos";
  }

  // ---- Miembros ----

  @PostMapping("/proyectos/{id}/miembros")
  public String invitar(@PathVariable String id,
                        @RequestParam(required = false) String email,
                        @RequestParam(required = false) String permiso,
                        HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    try {
      var p = proyectoService.invitar(id, email, permiso, uid, rol(s));
      if (p == null) return noEncontrado(ra);
      audit.registrar(s, AuditService.EDITAR, AuditService.PROYECTO, id, p.getNombre(),
          AuditDetalles.invitacion(email, permiso));
    } catch (ProyectoValidationException e) {
      if (proyectoService.getVisible(id, uid, rol(s)) == null) return noEncontrado(ra);
      ra.addFlashAttribute("toastError", e.getMessage());
      return "redirect:/proyectos/" + id;
    }
    ra.addFlashAttribute("toastSuccess", "Miembro agregado: " + email.trim());
    return "redirect:/proyectos/" + id;
  }

  @PostMapping("/proyectos/{id}/miembros/{miembroUid}/permiso")
  public String cambiarPermiso(@PathVariable String id, @PathVariable String miembroUid,
                               @RequestParam(required = false) String permiso,
                               HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    try {
      var p = proyectoService.cambiarPermisoMiembro(id, miembroUid, permiso, uid, rol(s));
      if (p == null) return noEncontrado(ra);
      audit.registrar(s, AuditService.EDITAR, AuditService.PROYECTO, id, p.getNombre(),
          AuditDetalles.cambioPermiso(p, miembroUid, permiso));
    } catch (ProyectoValidationException e) {
      if (proyectoService.getVisible(id, uid, rol(s)) == null) return noEncontrado(ra);
      ra.addFlashAttribute("toastError", e.getMessage());
      return "redirect:/proyectos/" + id;
    }
    ra.addFlashAttribute("toastSuccess", "Permiso actualizado");
    return "redirect:/proyectos/" + id;
  }

  @PostMapping("/proyectos/{id}/miembros/{miembroUid}/delete")
  public String quitarMiembro(@PathVariable String id, @PathVariable String miembroUid,
                              HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = uid(s);
    if (uid == null) return "redirect:/login";
    var antes = proyectoService.getVisible(id, uid, rol(s));
    try {
      var p = proyectoService.quitarMiembro(id, miembroUid, uid, rol(s));
      if (p == null) return noEncontrado(ra);
      audit.registrar(s, AuditService.EDITAR, AuditService.PROYECTO, id, p.getNombre(),
          AuditDetalles.quitado(antes, miembroUid));
    } catch (ProyectoValidationException e) {
      if (proyectoService.getVisible(id, uid, rol(s)) == null) return noEncontrado(ra);
      ra.addFlashAttribute("toastError", e.getMessage());
      return "redirect:/proyectos/" + id;
    }
    ra.addFlashAttribute("toastSuccess", "Miembro quitado del proyecto");
    return "redirect:/proyectos/" + id;
  }

  // ---- Helpers ----

  /** Coordenadas del proyecto (Overworld) en el Nether y el portal Overworld guardado más cercano del mundo. */
  private static void exponerNether(Model m, Proyecto p, Mundo mundo) {
    if (mundo == null || p.getX() == null || p.getZ() == null) return;
    int y = p.getY() != null ? p.getY() : 64;
    try {
      m.addAttribute("netherEquivalente", NetherCalculator.convertir(Dimension.OVERWORLD, p.getX(), y, p.getZ()));
    } catch (MundoValidationException e) {
      return;
    }
    PortalService.masCercano(PortalService.portales(mundo), p.getX(), p.getZ())
        .ifPresent(c -> m.addAttribute("portalCercano", c));
  }

  private void exponerComentarios(Model m, Proyecto p, String uid, String rol) throws Exception {
    var lista = comentarioService.listar(p.getId());
    if (lista == null) lista = List.of();
    Set<String> editables = new LinkedHashSet<>();
    Set<String> borrables = new LinkedHashSet<>();
    for (var c : lista) {
      if (ComentarioService.puedeEditar(c, uid)) editables.add(c.getId());
      if (ComentarioService.puedeBorrar(c, p, uid, rol)) borrables.add(c.getId());
    }
    m.addAttribute("comentarios", lista);
    m.addAttribute("comentariosEditables", editables);
    m.addAttribute("comentariosBorrables", borrables);
    m.addAttribute("comentarioMax", ComentarioService.MAX_LARGO);
  }

  /** Faltantes contra el inventario de quien mira. Un error del cálculo se muestra en la sección, no rompe la página. */
  private void exponerFaltantes(Model m, Proyecto p, String uid) {
    if (CraftingCalculatorService.demandasDe(p).isEmpty()) return;
    try {
      var r = calculator.faltantesProyecto(p, uid);
      if (r != null) m.addAttribute("faltantes", r);
    } catch (InventoryException e) {
      m.addAttribute("faltantesError", e.getMessage());
    } catch (Exception e) {
      m.addAttribute("faltantesError", "No se pudieron calcular los faltantes en este momento.");
    }
  }

  private void exponerLista(Model m, HttpSession s, List<Proyecto> lista) throws Exception {
    String uid = uid(s);
    Set<String> editables = new LinkedHashSet<>();
    Map<String, String> compartidos = new LinkedHashMap<>();
    for (var p : lista) {
      if (ProyectoService.canEdit(p, uid, rol(s))) editables.add(p.getId());
      var permiso = ProyectoService.permisoDe(p, uid);
      if (permiso != null) compartidos.put(p.getId(), permiso);
    }
    m.addAttribute("proyectos", lista);
    m.addAttribute("editables", editables);
    m.addAttribute("compartidos", compartidos);
    m.addAttribute("catalogItems", itemService.list(null, null));
    m.addAttribute("mundos", mundoService.listar(uid));
    m.addAttribute("currentUid", uid);
    m.addAttribute("isAdmin", "ADMIN".equals(rol(s)));
  }

  private void exponerFormularioEdicion(Model m, HttpSession s, Proyecto p) throws Exception {
    m.addAttribute("proyecto", p);
    m.addAttribute("catalogItems", itemService.list(null, null));
    m.addAttribute("mundos", mundoService.listar(p.getCreadorUid()));
    m.addAttribute("estados", ESTADOS);
    m.addAttribute("isAdmin", "ADMIN".equals(rol(s)));
  }

  private void exponerEstados(Model m, HttpSession s, Proyecto p) {
    String estadoActual = p.getEstado() != null ? p.getEstado() : "PLANIFICACION";
    m.addAttribute("proyecto", p);
    m.addAttribute("canEdit", ProyectoService.canEdit(p, uid(s), rol(s)));
    m.addAttribute("transicionesDisponibles", EstadoProyecto.fromString(estadoActual).getTransicionesValidas());
    m.addAttribute("historial", p.getHistorialEstados());
    m.addAttribute("currentUid", uid(s));
    m.addAttribute("isAdmin", "ADMIN".equals(rol(s)));
  }

  private static String noEncontrado(RedirectAttributes ra) {
    ra.addFlashAttribute("toastError", NO_ENCONTRADO);
    return "redirect:/proyectos";
  }

  private static String uid(HttpSession s) {
    return (String) s.getAttribute("uid");
  }

  private static String rol(HttpSession s) {
    return (String) s.getAttribute("rol");
  }

  private static String nombreUsuario(HttpSession s) {
    String nombre = (String) s.getAttribute("nombre");
    return nombre != null ? nombre : (String) s.getAttribute("usuario");
  }

  private static void aplicarUbicacion(Proyecto p, String mundoId, Integer x, Integer y, Integer z) {
    p.setMundoId(mundoId);
    p.setX(x);
    p.setY(y);
    p.setZ(z);
  }

  private static List<ItemRequerido> buildItemsRequeridos(List<String> itemIds, List<Integer> cantidades) {
    List<ItemRequerido> items = new ArrayList<>();
    if (itemIds == null || cantidades == null) return items;

    int count = Math.min(itemIds.size(), cantidades.size());
    for (int i = 0; i < count; i++) {
      String itemId = itemIds.get(i);
      Integer qty = cantidades.get(i);
      if (itemId != null && !itemId.isBlank() && qty != null && qty > 0) {
        items.add(new ItemRequerido(itemId.trim(), qty));
      }
    }
    return items;
  }
}
