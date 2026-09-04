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
  public ResponseEntity<?> list(@RequestParam(required = false) String q) throws Exception {
    return ResponseEntity.ok(service.list(q));
  }

  @PostMapping
  public ResponseEntity<?> create(@Valid @RequestBody Item item) throws Exception {
    return ResponseEntity.ok(service.create(item));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable String id, @Valid @RequestBody Item item) throws Exception {
    return ResponseEntity.ok(service.update(id, item));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable String id) throws Exception {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
