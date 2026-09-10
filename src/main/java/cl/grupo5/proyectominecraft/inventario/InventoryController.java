package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.items.ItemService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventario")
public class InventoryController {
  private final InventoryService inventory;
  private final ItemService items;

  public InventoryController(InventoryService inventory, ItemService items) {
    this.inventory = inventory;
    this.items = items;
  }

  @GetMapping
  public ResponseEntity<?> get(HttpSession s) throws Exception {
    return ResponseEntity.ok(entries(inventory.get(uid(s))));
  }

  @PutMapping("/items/{itemId}")
  public ResponseEntity<?> set(@PathVariable String itemId, @RequestBody Map<String, Object> body, HttpSession s) throws Exception {
    Object raw = body == null ? null : body.get("cantidad");
    if (!(raw instanceof Number cantidad)) return ResponseEntity.badRequest().body("La cantidad debe ser un número.");
    try {
      return ResponseEntity.ok(entries(inventory.set(uid(s), itemId, cantidad.longValue())));
    } catch (InventoryException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @DeleteMapping("/items/{itemId}")
  public ResponseEntity<?> remove(@PathVariable String itemId, HttpSession s) throws Exception {
    try {
      return ResponseEntity.ok(entries(inventory.set(uid(s), itemId, 0)));
    } catch (InventoryException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  private List<InventoryEntry> entries(Map<String, Long> stock) throws Exception {
    var result = new java.util.ArrayList<InventoryEntry>();
    for (var e : stock.entrySet()) {
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
