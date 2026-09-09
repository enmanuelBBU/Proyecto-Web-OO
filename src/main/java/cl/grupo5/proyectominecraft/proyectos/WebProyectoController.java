package cl.grupo5.proyectominecraft.proyectos;

import cl.grupo5.proyectominecraft.items.Item;
import cl.grupo5.proyectominecraft.items.ItemService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class WebProyectoController {
  private final ProyectoService proyectoService;
  private final ItemService itemService;

  public WebProyectoController(ProyectoService proyectoService, ItemService itemService) {
    this.proyectoService = proyectoService;
    this.itemService = itemService;
  }

  @GetMapping("/proyectos")
  public String list(@RequestParam(required = false) String q,
                     @RequestParam(required = false) String estado,
                     @RequestParam(required = false) Boolean misProyectos,
                     Model m, HttpSession s) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";

    String filterUid = Boolean.TRUE.equals(misProyectos) ? uid : null;
    List<Proyecto> lista = proyectoService.list(q, estado, filterUid);
    List<Item> catalog = itemService.list(null, null);

    m.addAttribute("proyectos", lista);
    m.addAttribute("catalogItems", catalog);
    m.addAttribute("q", q);
    m.addAttribute("estado", estado);
    m.addAttribute("misProyectos", misProyectos);
    m.addAttribute("currentUid", uid);
    m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
    return "proyectos";
  }

  @PostMapping("/proyectos")
  public String create(@RequestParam(required = false) String nombre,
                       @RequestParam(required = false) String descripcion,
                       @RequestParam(required = false) String estado,
                       @RequestParam(required = false) List<String> itemId,
                       @RequestParam(required = false) List<Integer> cantidad,
                       Model m, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";

    String nombreUsuario = (String) s.getAttribute("nombre");
    if (nombreUsuario == null) nombreUsuario = (String) s.getAttribute("usuario");

    Proyecto p = new Proyecto();
    p.setNombre(nombre);
    p.setDescripcion(descripcion);
    p.setEstado(estado != null && !estado.isBlank() ? estado : "PLANIFICACION");
    p.setItemsRequeridos(buildItemsRequeridos(itemId, cantidad));

    try {
      proyectoService.create(p, uid, nombreUsuario);
    } catch (ProyectoAlreadyExistsException | ProyectoValidationException e) {
      m.addAttribute("error", e.getMessage());
      m.addAttribute("toastError", e.getMessage());
      m.addAttribute("proyectos", proyectoService.list(null, null, null));
      m.addAttribute("catalogItems", itemService.list(null, null));
      m.addAttribute("currentUid", uid);
      m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
      return "proyectos";
    }

    ra.addFlashAttribute("toastSuccess", "Proyecto creado correctamente");
    return "redirect:/proyectos";
  }

  @GetMapping("/proyectos/{id}")
  public String detail(@PathVariable String id, Model m, HttpSession s) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";

    Proyecto p = proyectoService.get(id);
    if (p == null) return "redirect:/proyectos";

    // Build map of catalog items for displaying rich item details
    List<Item> catalog = itemService.list(null, null);
    Map<String, Item> itemMap = new HashMap<>();
    for (Item item : catalog) {
      itemMap.put(item.getId(), item);
    }

    boolean canEdit = ProyectoService.canUserModify(p, uid, (String) s.getAttribute("rol"));

    m.addAttribute("proyecto", p);
    m.addAttribute("itemMap", itemMap);
    m.addAttribute("canEdit", canEdit);
    m.addAttribute("currentUid", uid);
    m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
    return "proyecto-detalle";
  }

  @GetMapping("/proyectos/{id}/edit")
  public String editForm(@PathVariable String id, Model m, HttpSession s) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";

    Proyecto p = proyectoService.get(id);
    if (p == null) return "redirect:/proyectos";

    boolean canEdit = ProyectoService.canUserModify(p, uid, (String) s.getAttribute("rol"));
    if (!canEdit) return "redirect:/proyectos/" + id;

    List<Item> catalog = itemService.list(null, null);

    m.addAttribute("proyecto", p);
    m.addAttribute("catalogItems", catalog);
    m.addAttribute("estados", List.of("PLANIFICACION", "EN_CONSTRUCCION", "COMPLETADO", "CANCELADO")); // Mantenemos para el select general por ahora o se quita, pero no es estados de transicion
    return "proyecto-edit";
  }

  @GetMapping("/proyectos/{id}/estados")
  public String estados(@PathVariable String id, Model m, HttpSession s) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";

    Proyecto p = proyectoService.get(id);
    if (p == null) return "redirect:/proyectos";

    boolean canEdit = ProyectoService.canUserModify(p, uid, (String) s.getAttribute("rol"));

    String estadoActual = p.getEstado() != null ? p.getEstado() : "PLANIFICACION";
    Set<String> transicionesDisponibles = EstadoProyecto.fromString(estadoActual).getTransicionesValidas();

    m.addAttribute("proyecto", p);
    m.addAttribute("canEdit", canEdit);
    m.addAttribute("transicionesDisponibles", transicionesDisponibles);
    m.addAttribute("historial", p.getHistorialEstados());
    m.addAttribute("currentUid", uid);
    m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
    return "proyecto-estados";
  }

  @PostMapping("/proyectos/{id}/transicionar")
  public String transicionar(@PathVariable String id,
                             @RequestParam String nuevoEstado,
                             @RequestParam(required = false) String motivo,
                             Model m, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";
    String rol = (String) s.getAttribute("rol");
    String nombre = (String) s.getAttribute("nombre");
    if (nombre == null) nombre = (String) s.getAttribute("usuario");

    try {
      proyectoService.transicionarEstado(id, nuevoEstado, motivo, uid, nombre, rol);
    } catch (ProyectoValidationException e) {
      Proyecto p = proyectoService.get(id);
      String estadoActual = p.getEstado() != null ? p.getEstado() : "PLANIFICACION";
      Set<String> transicionesDisponibles = EstadoProyecto.fromString(estadoActual).getTransicionesValidas();

      m.addAttribute("proyecto", p);
      m.addAttribute("canEdit", ProyectoService.canUserModify(p, uid, rol));
      m.addAttribute("transicionesDisponibles", transicionesDisponibles);
      m.addAttribute("historial", p.getHistorialEstados());
      m.addAttribute("error", e.getMessage());
      m.addAttribute("toastError", e.getMessage());
      m.addAttribute("currentUid", uid);
      m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
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
                       Model m, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";
    String rol = (String) s.getAttribute("rol");

    Proyecto p = proyectoService.get(id);
    if (p == null) return "redirect:/proyectos";

    Proyecto updatePayload = new Proyecto();
    updatePayload.setNombre(nombre);
    updatePayload.setDescripcion(descripcion);
    updatePayload.setEstado(estado);
    updatePayload.setItemsRequeridos(buildItemsRequeridos(itemId, cantidad));

    try {
      proyectoService.update(id, updatePayload, uid, rol);
    } catch (ProyectoValidationException e) {
      m.addAttribute("proyecto", p);
      m.addAttribute("catalogItems", itemService.list(null, null));
      m.addAttribute("estados", List.of("PLANIFICACION", "EN_CONSTRUCCION", "COMPLETADO", "CANCELADO"));
      m.addAttribute("error", e.getMessage());
      m.addAttribute("toastError", e.getMessage());
      return "proyecto-edit";
    }

    ra.addFlashAttribute("toastSuccess", "Proyecto actualizado correctamente");
    return "redirect:/proyectos/" + id;
  }

  @PostMapping("/proyectos/{id}/delete")
  public String delete(@PathVariable String id, HttpSession s, RedirectAttributes ra) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";
    String rol = (String) s.getAttribute("rol");

    proyectoService.delete(id, uid, rol);
    ra.addFlashAttribute("toastSuccess", "Proyecto eliminado correctamente");
    return "redirect:/proyectos";
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
