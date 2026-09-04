package cl.grupo5.proyectominecraft.items;

import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ItemService {
  private final Firestore db;
  public ItemService(Firestore db) { this.db = db; }

  public List<Item> list(String q) throws Exception {
    var docs = db.collection("items").get().get().getDocuments();
    var items = docs.stream().map(d -> {
      var it = d.toObject(Item.class);
      it.setId(d.getId());
      return it;
    }).toList();
    if (q == null || q.isBlank()) return items;
    // ponytail: O(n) scan, indice Firestore si volumen crece
    var needle = q.toLowerCase();
    return items.stream().filter(i -> i.getNombre() != null && i.getNombre().toLowerCase().contains(needle)).toList();
  }

  public Item get(String id) throws Exception {
    var snap = db.collection("items").document(id).get().get();
    if (!snap.exists()) return null;
    var it = snap.toObject(Item.class);
    it.setId(id);
    return it;
  }

  public Item create(Item item) throws Exception {
    var ref = db.collection("items").add(item).get();
    item.setId(ref.getId());
    return item;
  }

  public Item update(String id, Item item) throws Exception {
    db.collection("items").document(id).set(item).get();
    item.setId(id);
    return item;
  }

  public void delete(String id) throws Exception {
    db.collection("items").document(id).delete().get();
  }
}
