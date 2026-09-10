package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.items.Ingrediente;
import cl.grupo5.proyectominecraft.items.Item;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CraftingCalculatorServiceTest {

  private Item raw(String id, String nombre) {
    var item = new Item();
    item.setId(id);
    item.setNombre(nombre);
    item.setEsMateriaPrima(true);
    return item;
  }

  private Item crafted(String id, String nombre, Ingrediente... ingredientes) {
    var item = new Item();
    item.setId(id);
    item.setNombre(nombre);
    item.setIngredientesParaCalculo(List.of(ingredientes));
    return item;
  }

  private Ingrediente ing(String itemId, int cantidad) {
    var ingrediente = new Ingrediente();
    ingrediente.setItemId(itemId);
    ingrediente.setCantidad(cantidad);
    return ingrediente;
  }

  private Map<String, Item> catalogo() {
    var tronco = raw("tronco", "Tronco");
    var tablones = crafted("tablones", "Tablones", ing("tronco", 1));
    var palos = crafted("palos", "Palos", ing("tablones", 2));
    var pala = crafted("pala", "Pala", ing("tablones", 2), ing("palos", 1));
    return Map.of("tronco", tronco, "tablones", tablones, "palos", palos, "pala", pala);
  }

  @Test
  void expandsMultiLevelRecipeAggregatingRawMaterials() throws Exception {
    var catalogo = catalogo();

    var requirements = CraftingCalculatorService.expandRequirements("pala", 1, catalogo::get);

    assertThat(requirements).containsEntry("tronco", 4L);
    assertThat(requirements).doesNotContainKey("tablones");
  }

  @Test
  void multipliesQuantitiesByRequestedAmount() throws Exception {
    var catalogo = catalogo();

    var requirements = CraftingCalculatorService.expandRequirements("pala", 3, catalogo::get);

    assertThat(requirements).containsEntry("tronco", 12L);
  }

  @Test
  void unknownTargetThrows() {
    var catalogo = catalogo();

    assertThatThrownBy(() -> CraftingCalculatorService.expandRequirements("diamante", 1, catalogo::get))
        .isInstanceOf(InventoryException.class);
  }

  @Test
  void planReportsMissingAndNotCraftableWhenShort() {
    var requirements = Map.of("tronco", 8L);
    var stock = Map.of("tronco", 5L);

    var plan = CraftingCalculatorService.toPlan("pala", 1, requirements, stock, id -> "Tronco");

    assertThat(plan.isCraftable()).isFalse();
    assertThat(plan.getMateriales()).hasSize(1);
    var fila = plan.getMateriales().get(0);
    assertThat(fila.getRequerido()).isEqualTo(8L);
    assertThat(fila.getDisponible()).isEqualTo(5L);
    assertThat(fila.getFaltante()).isEqualTo(3L);
  }

  @Test
  void planIsCraftableWhenStockCoversRequirements() {
    var requirements = Map.of("tronco", 8L);
    var stock = Map.of("tronco", 8L);

    var plan = CraftingCalculatorService.toPlan("pala", 1, requirements, stock, id -> "Tronco");

    assertThat(plan.isCraftable()).isTrue();
    assertThat(plan.getMateriales().get(0).getFaltante()).isZero();
  }

  @Test
  void rawTargetIsDetected() {
    assertThat(CraftingCalculatorService.isRaw(raw("tronco", "Tronco"))).isTrue();
    assertThat(CraftingCalculatorService.isRaw(catalogo().get("pala"))).isFalse();
  }
}
