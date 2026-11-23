package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.items.Item;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class HistorialCrafteoService {
  static final String COLECCION = "crafteos";

  private final Firestore db;

  public HistorialCrafteoService(Firestore db) {
    this.db = db;
  }

  public List<RegistroCrafteo> listar(String uid) throws Exception {
    var docs = db.collection(COLECCION).whereEqualTo("uid", uid).get().get().getDocuments();
    var registros = docs.stream().map(d -> {
      var r = d.toObject(RegistroCrafteo.class);
      r.setId(d.getId());
      return r;
    }).toList();
    return ordenarRecientes(registros);
  }

  static RegistroCrafteo crearRegistro(String uid, Item target, CraftingPlan plan, String fecha) {
    var r = new RegistroCrafteo();
    r.setUid(uid);
    r.setItemId(plan.getTargetItemId());
    r.setItemNombre(target != null && target.getNombre() != null ? target.getNombre() : plan.getTargetItemId());
    r.setTipoReceta(plan.getTipoReceta());
    r.setCantidad(plan.getCantidad());
    r.setFecha(fecha);
    r.setMateriales(plan.getMateriales().stream().map(m -> {
      var mc = new MaterialConsumido();
      mc.setItemId(m.getItemId());
      mc.setNombre(m.getNombre());
      mc.setCantidad(m.getRequerido());
      return mc;
    }).toList());
    return r;
  }

  static List<RegistroCrafteo> ordenarRecientes(List<RegistroCrafteo> registros) {
    return registros.stream()
        .sorted(Comparator.comparing(RegistroCrafteo::getFecha, Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();
  }
}
