package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.items.ItemService;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class InventoryService {
  private final Firestore db;
  private final ItemService items;

  public InventoryService(Firestore db, ItemService items) {
    this.db = db;
    this.items = items;
  }

  public Map<String, Long> get(String uid) throws Exception {
    var snap = db.collection("inventarios").document(uid).get().get();
    return readItems(snap);
  }

  public Map<String, Long> set(String uid, String itemId, long cantidad) throws Exception {
    if (cantidad < 0) throw new InventoryException("La cantidad no puede ser negativa.");
    if (items.get(itemId) == null) throw new InventoryException("El ítem no existe en el catálogo.");
    var ref = db.collection("inventarios").document(uid);
    return db.runTransaction(tx -> {
      var current = readItems(tx.get(ref).get());
      var updated = mergeSet(current, itemId, cantidad);
      tx.set(ref, Map.of("items", updated));
      return updated;
    }).get();
  }

  public Map<String, Long> add(String uid, String itemId, long delta) throws Exception {
    if (items.get(itemId) == null) throw new InventoryException("El ítem no existe en el catálogo.");
    var ref = db.collection("inventarios").document(uid);
    return db.runTransaction(tx -> {
      var current = readItems(tx.get(ref).get());
      var updated = mergeAdd(current, itemId, delta);
      tx.set(ref, Map.of("items", updated));
      return updated;
    }).get();
  }

  public void clear(String uid) throws Exception {
    db.collection("inventarios").document(uid).delete().get();
  }

  static Map<String, Long> mergeSet(Map<String, Long> current, String itemId, long cantidad) {
    var updated = new LinkedHashMap<>(current);
    if (cantidad == 0) updated.remove(itemId);
    else updated.put(itemId, cantidad);
    return updated;
  }

  static Map<String, Long> mergeAdd(Map<String, Long> current, String itemId, long delta) {
    var updated = new LinkedHashMap<>(current);
    long next = updated.getOrDefault(itemId, 0L) + delta;
    if (next <= 0) updated.remove(itemId);
    else updated.put(itemId, next);
    return updated;
  }

  static Map<String, Long> readItems(DocumentSnapshot snap) {
    var result = new LinkedHashMap<String, Long>();
    if (snap == null || !snap.exists()) return result;
    Object raw = snap.get("items");
    if (!(raw instanceof Map<?, ?> map)) return result;
    for (var e : map.entrySet()) {
      if (e.getKey() instanceof String key && e.getValue() instanceof Number n) {
        result.put(key, n.longValue());
      }
    }
    return result;
  }
}
