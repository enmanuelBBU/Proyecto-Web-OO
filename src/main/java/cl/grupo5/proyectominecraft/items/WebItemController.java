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
  public String list(@RequestParam(required = false) String q, Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    m.addAttribute("items", items.list(q));
    m.addAttribute("q", q);
    return "items";
  }

  @PostMapping("/items")
  public String create(@RequestParam String nombre, @RequestParam(defaultValue = "BLOQUE") String tipo,
                       @RequestParam(defaultValue = "0") int cantidad, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    var it = new Item();
    it.setNombre(nombre);
    it.setTipo(tipo);
    it.setCantidad(cantidad);
    items.create(it);
    return "redirect:/items";
  }

  @GetMapping("/items/{id}/edit")
  public String edit(@PathVariable String id, Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    m.addAttribute("item", items.get(id));
    return "item-edit";
  }

  @PostMapping("/items/{id}/update")
  public String update(@PathVariable String id, @RequestParam String nombre,
                       @RequestParam(defaultValue = "BLOQUE") String tipo,
                       @RequestParam(defaultValue = "0") int cantidad, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    var it = items.get(id);
    it.setNombre(nombre);
    it.setTipo(tipo);
    it.setCantidad(cantidad);
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
