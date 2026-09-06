package cl.grupo5.proyectominecraft.items;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
                       @RequestParam(required = false) String fullId,
                       @RequestParam(required = false) List<String> slot,
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
    it.setFullId(fullId);
    it.setRecetaMatriz(normalizeSlots(slot));
    try {
      items.create(it);
    } catch (ItemAlreadyExistsException | RecipeValidationException e) {
      m.addAttribute("error", e.getMessage());
      m.addAttribute("items", items.list(null, null));
      m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
      return "items";
    }
    return "redirect:/items";
  }

  @GetMapping("/items/{id}/edit")
  public String edit(@PathVariable String id, Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    var it = items.get(id);
    if (it == null) return "redirect:/items";
    m.addAttribute("item", it);
    m.addAttribute("recetaMatriz", nineSlots(it.getRecetaMatriz()));
    return "item-edit";
  }

  @PostMapping("/items/{id}/update")
  public String update(@PathVariable String id, @RequestParam(required = false) String nombre,
                       @RequestParam(required = false) String categoria,
                       @RequestParam(defaultValue = "false") boolean esMateriaPrima,
                       @RequestParam(required = false) String fullId,
                       @RequestParam(required = false) List<String> slot,
                       Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    var it = items.get(id);
    if (it == null) return "redirect:/items";
    if (nombre == null || nombre.isBlank()) {
      m.addAttribute("item", it);
      m.addAttribute("recetaMatriz", nineSlots(it.getRecetaMatriz()));
      m.addAttribute("error", "El nombre del ítem es obligatorio.");
      return "item-edit";
    }
    it.setNombre(nombre);
    it.setCategoria(categoria);
    it.setEsMateriaPrima(esMateriaPrima);
    it.setFullId(fullId);
    it.setRecetaMatriz(normalizeSlots(slot));
    try {
      items.update(id, it);
    } catch (RecipeValidationException e) {
      m.addAttribute("item", it);
      m.addAttribute("recetaMatriz", nineSlots(it.getRecetaMatriz()));
      m.addAttribute("error", e.getMessage());
      return "item-edit";
    }
    return "redirect:/items";
  }

  @PostMapping("/items/{id}/delete")
  public String delete(@PathVariable String id, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    items.delete(id);
    return "redirect:/items";
  }

  private static List<String> normalizeSlots(List<String> slot) {
    if (slot == null) return List.of();
    return slot.stream().map(v -> (v == null || v.isBlank()) ? null : v.trim()).toList();
  }

  private static List<String> nineSlots(List<String> receta) {
    var base = new ArrayList<String>(Collections.nCopies(9, null));
    if (receta != null) {
      for (int i = 0; i < Math.min(9, receta.size()); i++) base.set(i, receta.get(i));
    }
    return base;
  }
}
