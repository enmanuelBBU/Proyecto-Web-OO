package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.items.ItemService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebCraftingController {
  private final CraftingCalculatorService calculator;
  private final ItemService items;

  public WebCraftingController(CraftingCalculatorService calculator, ItemService items) {
    this.calculator = calculator;
    this.items = items;
  }

  @GetMapping("/calculadora")
  public String view(@RequestParam(required = false) String targetId,
                     @RequestParam(defaultValue = "1") int cantidad,
                     Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    m.addAttribute("catalogo", items.list(null, false));
    m.addAttribute("targetId", targetId);
    m.addAttribute("cantidad", cantidad);
    m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
    if (targetId != null && !targetId.isBlank()) {
      try {
        m.addAttribute("plan", calculator.plan(uid(s), targetId, cantidad));
      } catch (InventoryException e) {
        m.addAttribute("error", e.getMessage());
      }
    }
    return "calculadora";
  }

  @PostMapping("/calculadora/craftear")
  public String craft(@RequestParam(required = false) String itemId,
                      @RequestParam(defaultValue = "1") int cantidad,
                      Model m, HttpSession s, RedirectAttributes ra) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    try {
      calculator.craft(uid(s), itemId, cantidad);
    } catch (InventoryException e) {
      m.addAttribute("catalogo", items.list(null, false));
      m.addAttribute("targetId", itemId);
      m.addAttribute("cantidad", cantidad);
      m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
      m.addAttribute("error", e.getMessage());
      m.addAttribute("toastError", e.getMessage());
      return "calculadora";
    }
    ra.addFlashAttribute("toastSuccess", "¡Crafteo exitoso!");
    return "redirect:/calculadora?targetId=" + itemId + "&cantidad=" + cantidad;
  }

  private static String uid(HttpSession s) {
    return String.valueOf(s.getAttribute("uid"));
  }
}
