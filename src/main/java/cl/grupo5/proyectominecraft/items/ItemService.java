package cl.grupo5.proyectominecraft.items;

import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ItemService {
  private final Firestore db;
  public ItemService(Firestore db) { this.db = db; }

  public List<Item> list(String q, Boolean esMateriaPrima) throws Exception {
    var docs = db.collection("items").get().get().getDocuments();
    var items = docs.stream().map(d -> {
      var it = d.toObject(Item.class);
      it.setId(d.getId());
      return it;
    }).toList();
    return filter(items, q, esMateriaPrima);
  }

  static List<Item> filter(List<Item> items, String q, Boolean esMateriaPrima) {
    var result = items;
    if (q != null && !q.isBlank()) {
      var needle = q.toLowerCase();
      result = result.stream().filter(i ->
          (i.getNombre() != null && i.getNombre().toLowerCase().contains(needle)) ||
          (i.getCategoria() != null && i.getCategoria().toLowerCase().contains(needle))
      ).toList();
    }
    if (esMateriaPrima != null) {
      result = result.stream().filter(i -> i.isEsMateriaPrima() == esMateriaPrima).toList();
    }
    return result;
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
    var snap = db.collection("items").document(id).get().get();
    if (!snap.exists()) return null;
    item.setId(id);
    db.collection("items").document(id).set(item).get();
    return item;
  }

  public boolean delete(String id) throws Exception {
    var snap = db.collection("items").document(id).get().get();
    if (!snap.exists()) return false;
    db.collection("items").document(id).delete().get();
    return true;
  }
}
