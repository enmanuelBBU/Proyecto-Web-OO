package cl.grupo5.proyectominecraft.items;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class WebItemController {
  private final ItemService items;

  public WebItemController(ItemService items) {
    this.items = items;
  }

  @GetMapping("/items")
  public String list(@RequestParam(required = false) String q,
                      @RequestParam(required = false) Boolean esMateriaPrima,
                      Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    m.addAttribute("items", items.list(q, esMateriaPrima));
    m.addAttribute("q", q);
    m.addAttribute("esMateriaPrima", esMateriaPrima);
    m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
    return "items";
  }

  @PostMapping("/items")
  public String create(@RequestParam(required = false) String nombre, @RequestParam(required = false) String categoria,
                       @RequestParam(defaultValue = "false") boolean esMateriaPrima,
                       Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    if (nombre == null || nombre.isBlank()) {
      m.addAttribute("error", "El nombre del ítem es obligatorio.");
      m.addAttribute("items", items.list(null, null));
      m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
      return "items";
    }
    var it = new Item();
    it.setNombre(nombre);
    it.setCategoria(categoria);
    it.setEsMateriaPrima(esMateriaPrima);
    items.create(it);
    return "redirect:/items";
  }

  @GetMapping("/items/{id}/edit")
  public String edit(@PathVariable String id, Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    var it = items.get(id);
    if (it == null) return "redirect:/items";
    m.addAttribute("item", it);
    return "item-edit";
  }

  @PostMapping("/items/{id}/update")
  public String update(@PathVariable String id, @RequestParam(required = false) String nombre,
                       @RequestParam(required = false) String categoria,
                       @RequestParam(defaultValue = "false") boolean esMateriaPrima,
                       Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    var it = items.get(id);
    if (it == null) return "redirect:/items";
    if (nombre == null || nombre.isBlank()) {
      m.addAttribute("item", it);
      m.addAttribute("error", "El nombre del ítem es obligatorio.");
      return "item-edit";
    }
    it.setNombre(nombre);
    it.setCategoria(categoria);
    it.setEsMateriaPrima(esMateriaPrima);
    items.update(id, it);
    return "redirect:/items";
  }

  @PostMapping("/items/{id}/delete")
  public String delete(@PathVariable String id, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    items.delete(id);
    return "redirect:/items";
  }
}
