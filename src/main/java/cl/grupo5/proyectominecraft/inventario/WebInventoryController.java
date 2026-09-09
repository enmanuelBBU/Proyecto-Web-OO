package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.items.ItemService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@Controller
public class WebInventoryController {
  private final InventoryService inventory;
  private final ItemService items;

  public WebInventoryController(InventoryService inventory, ItemService items) {
    this.inventory = inventory;
    this.items = items;
  }

  @GetMapping("/inventario")
  public String view(Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    m.addAttribute("entries", entries(s));
    m.addAttribute("catalogo", items.list(null, null));
    m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
    return "inventario";
  }

  @PostMapping("/inventario/agregar")
  public String add(@RequestParam(required = false) String itemId,
                    @RequestParam(defaultValue = "1") long cantidad,
                    Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    try {
      inventory.add(uid(s), itemId, cantidad);
    } catch (InventoryException e) {
      return rerender(m, s, e.getMessage());
    }
    return "redirect:/inventario";
  }

  @PostMapping("/inventario/actualizar")
  public String update(@RequestParam(required = false) String itemId,
                       @RequestParam(defaultValue = "0") long cantidad,
                       Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    try {
      inventory.set(uid(s), itemId, cantidad);
    } catch (InventoryException e) {
      return rerender(m, s, e.getMessage());
    }
    return "redirect:/inventario";
  }

  @PostMapping("/inventario/eliminar")
  public String remove(@RequestParam(required = false) String itemId, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    inventory.set(uid(s), itemId, 0);
    return "redirect:/inventario";
  }

  private String rerender(Model m, HttpSession s, String error) throws Exception {
    m.addAttribute("entries", entries(s));
    m.addAttribute("catalogo", items.list(null, null));
    m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
    m.addAttribute("error", error);
    return "inventario";
  }

  private java.util.List<InventoryEntry> entries(HttpSession s) throws Exception {
    var result = new ArrayList<InventoryEntry>();
    for (var e : inventory.get(uid(s)).entrySet()) {
      var entry = new InventoryEntry();
      entry.setItemId(e.getKey());
      var item = items.get(e.getKey());
      entry.setNombre(item != null && item.getNombre() != null ? item.getNombre() : e.getKey());
      entry.setCantidad(e.getValue());
      result.add(entry);
    }
    return result;
  }

  private static String uid(HttpSession s) {
    return String.valueOf(s.getAttribute("uid"));
  }
}
