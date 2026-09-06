package cl.grupo5.proyectominecraft.items;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/items")
public class ItemController {
  private final ItemService service;
  public ItemController(ItemService service) { this.service = service; }

  @GetMapping
  public ResponseEntity<?> list(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) Boolean esMateriaPrima) throws Exception {
    return ResponseEntity.ok(service.list(q, esMateriaPrima));
  }

  @PostMapping
  public ResponseEntity<?> create(@Valid @RequestBody Item item) throws Exception {
    return ResponseEntity.ok(service.create(item));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable String id, @Valid @RequestBody Item item) throws Exception {
    var updated = service.update(id, item);
    if (updated == null) return ResponseEntity.notFound().build();
    return ResponseEntity.ok(updated);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable String id) throws Exception {
    if (!service.delete(id)) return ResponseEntity.notFound().build();
    return ResponseEntity.noContent().build();
  }
}
