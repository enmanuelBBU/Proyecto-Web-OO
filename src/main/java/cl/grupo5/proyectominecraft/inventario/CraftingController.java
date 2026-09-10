package cl.grupo5.proyectominecraft.inventario;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/calculadora")
public class CraftingController {
  private final CraftingCalculatorService calculator;

  public CraftingController(CraftingCalculatorService calculator) {
    this.calculator = calculator;
  }

  @GetMapping("/plan/{itemId}")
  public ResponseEntity<?> plan(@PathVariable String itemId,
                                @RequestParam(defaultValue = "1") int cantidad,
                                HttpSession s) throws Exception {
    try {
      return ResponseEntity.ok(calculator.plan(uid(s), itemId, cantidad));
    } catch (InventoryException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/craftear")
  public ResponseEntity<?> craft(@RequestBody Map<String, Object> body, HttpSession s) throws Exception {
    Object rawId = body == null ? null : body.get("itemId");
    Object rawCantidad = body == null ? null : body.get("cantidad");
    if (!(rawId instanceof String itemId) || itemId.isBlank()) {
      return ResponseEntity.badRequest().body("El itemId es obligatorio.");
    }
    int cantidad = rawCantidad instanceof Number n ? n.intValue() : 1;
    try {
      return ResponseEntity.ok(calculator.craft(uid(s), itemId, cantidad));
    } catch (InventoryException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  private static String uid(HttpSession s) {
    return String.valueOf(s.getAttribute("uid"));
  }
}
