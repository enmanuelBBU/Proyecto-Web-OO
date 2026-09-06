package cl.grupo5.proyectominecraft.items;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ItemServiceTest {

  private Item item(String nombre, String categoria, boolean esMateriaPrima) {
    var i = new Item();
    i.setNombre(nombre);
    i.setCategoria(categoria);
    i.setEsMateriaPrima(esMateriaPrima);
    return i;
  }

  @Test
  void filterMatchesQueryAgainstNombreOrCategoria() {
    var items = List.of(
        item("Hierro", "Mineral", true),
        item("Pico de hierro", "Herramienta", false),
        item("Tabla", "Madera", true)
    );

    var result = ItemService.filter(items, "miner", null);

    assertThat(result).extracting(Item::getNombre).containsExactly("Hierro");
  }

  @Test
  void filterByEsMateriaPrima() {
    var items = List.of(
        item("Hierro", "Mineral", true),
        item("Pico de hierro", "Herramienta", false)
    );

    var result = ItemService.filter(items, null, true);

    assertThat(result).extracting(Item::getNombre).containsExactly("Hierro");
  }

  @Test
  void filterWithNoCriteriaReturnsAllItems() {
    var items = List.of(item("Hierro", "Mineral", true));

    var result = ItemService.filter(items, null, null);

    assertThat(result).hasSize(1);
  }

  @Test
  void itemDefaultsCategoriaAndEsMateriaPrimaWhenOnlyNombreSet() {
    var item = new Item();
    item.setNombre("Piedra");

    assertThat(item.getCategoria()).isNull();
    assertThat(item.isEsMateriaPrima()).isFalse();
  }
}
