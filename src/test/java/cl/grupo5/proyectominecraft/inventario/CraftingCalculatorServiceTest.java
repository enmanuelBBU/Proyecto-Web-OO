package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.items.Ingrediente;
import cl.grupo5.proyectominecraft.items.Item;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
    item.setTipoReceta("CRAFTEO");
    item.setIngredientesParaCalculo(List.of(ingredientes));
    return item;
  }

  private Item horno(String id, String nombre, String entrada) {
    var item = crafted(id, nombre, ing(entrada, 1));
    item.setTipoReceta("HORNO");
    item.setRecetaHorno(entrada);
    return item;
  }

  private Ingrediente ing(String itemId, int cantidad) {
    var ingrediente = new Ingrediente();
    ingrediente.setItemId(itemId);
    ingrediente.setCantidad(cantidad);
    return ingrediente;
  }

  private Map<String, Item> catalogo() {
    var c = new HashMap<String, Item>();
    c.put("tronco", raw("tronco", "Tronco"));
    c.put("tablones", crafted("tablones", "Tablones", ing("tronco", 1)));
    c.put("palos", crafted("palos", "Palos", ing("tablones", 2)));
    c.put("pala", crafted("pala", "Pala", ing("tablones", 2), ing("palos", 1)));
    c.put("hierro_crudo", raw("hierro_crudo", "Hierro crudo"));
    c.put("lingote", horno("lingote", "Lingote de hierro", "hierro_crudo"));
    c.put("cubeta", crafted("cubeta", "Cubeta", ing("lingote", 3)));
    return c;
  }

  private static Map<String, Long> stock(Object... pares) {
    var m = new LinkedHashMap<String, Long>();
    for (int i = 0; i < pares.length; i += 2) m.put((String) pares[i], ((Number) pares[i + 1]).longValue());
    return m;
  }

  private static NodoCrafteo hijo(NodoCrafteo n, String itemId) {
    return n.getHijos().stream().filter(h -> h.getItemId().equals(itemId)).findFirst().orElseThrow();
  }

  @Test
  void sinStockAgregaMateriasPrimasMultinivel() throws Exception {
    var r = CraftingCalculatorService.calcular(List.of(new Demanda("pala", 3)), catalogo()::get, stock());

    assertThat(r.getFaltantes()).extracting(MaterialCantidad::getItemId, MaterialCantidad::getCantidad)
        .containsExactly(tuple("tronco", 12L));
    assertThat(r.getConsumidosDeInventario()).isEmpty();
    assertThat(r.isUsaIntermedios()).isFalse();
  }

  @Test
  void arbolConStockIntermedioParcial() throws Exception {
    var s = stock("tablones", 1);
    var r = CraftingCalculatorService.calcular(List.of(new Demanda("pala", 1)), catalogo()::get, s);

    var pala = r.getArbol().get(0);
    assertThat(pala.getTipo()).isEqualTo(NodoCrafteo.CRAFTEO);
    assertThat(pala.getAFabricar()).isEqualTo(1);
    var tablones = pala.getHijos().get(0);
    assertThat(tablones.getItemId()).isEqualTo("tablones");
    assertThat(tablones.getRequerido()).isEqualTo(2);
    assertThat(tablones.getUsadoDeInventario()).isEqualTo(1);
    assertThat(tablones.getAFabricar()).isEqualTo(1);
    assertThat(hijo(tablones, "tronco").getFaltante()).isEqualTo(1);
    assertThat(r.getFaltantes()).extracting(MaterialCantidad::getItemId, MaterialCantidad::getCantidad)
        .containsExactly(tuple("tronco", 3L));
    assertThat(r.getConsumidosDeInventario()).extracting(MaterialCantidad::getItemId, MaterialCantidad::getCantidad)
        .containsExactly(tuple("tablones", 1L));
    assertThat(r.isUsaIntermedios()).isTrue();
    assertThat(s).doesNotContainKey("tablones");
  }

  @Test
  void stockIntermedioTotalNoExpande() throws Exception {
    var r = CraftingCalculatorService.calcular(List.of(new Demanda("palos", 1)), catalogo()::get, stock("tablones", 5));

    var tablones = r.getArbol().get(0).getHijos().get(0);
    assertThat(tablones.getUsadoDeInventario()).isEqualTo(2);
    assertThat(tablones.getAFabricar()).isZero();
    assertThat(tablones.getHijos()).isEmpty();
    assertThat(r.getFaltantes()).isEmpty();
  }

  @Test
  void stockCompartidoEntreDosDemandas() throws Exception {
    var r = CraftingCalculatorService.calcular(
        List.of(new Demanda("palos", 1), new Demanda("palos", 1)), catalogo()::get, stock("tablones", 2));

    assertThat(r.getArbol().get(0).getHijos().get(0).getUsadoDeInventario()).isEqualTo(2);
    assertThat(r.getArbol().get(1).getHijos().get(0).getUsadoDeInventario()).isZero();
    assertThat(r.getConsumidosDeInventario()).extracting(MaterialCantidad::getCantidad).containsExactly(2L);
    assertThat(r.getFaltantes()).extracting(MaterialCantidad::getItemId, MaterialCantidad::getCantidad)
        .containsExactly(tuple("tronco", 2L));
  }

  @Test
  void demandaDeItemQueYaTengoSeTomaDelInventario() throws Exception {
    var r = CraftingCalculatorService.calcular(List.of(new Demanda("pala", 2)), catalogo()::get, stock("pala", 2));

    assertThat(r.getArbol().get(0).getUsadoDeInventario()).isEqualTo(2);
    assertThat(r.getArbol().get(0).getHijos()).isEmpty();
    assertThat(r.getFaltantes()).isEmpty();
  }

  @Test
  void hornoAnidadoEnCrafteo() throws Exception {
    var r = CraftingCalculatorService.calcular(List.of(new Demanda("cubeta", 1)), catalogo()::get,
        stock("hierro_crudo", 1));

    var lingote = r.getArbol().get(0).getHijos().get(0);
    assertThat(lingote.getTipo()).isEqualTo(NodoCrafteo.HORNO);
    assertThat(lingote.getNombre()).isEqualTo("Lingote de hierro");
    assertThat(lingote.getAFabricar()).isEqualTo(3);
    var crudo = lingote.getHijos().get(0);
    assertThat(crudo.getTipo()).isEqualTo(NodoCrafteo.MATERIA_PRIMA);
    assertThat(crudo.getRequerido()).isEqualTo(3);
    assertThat(crudo.getUsadoDeInventario()).isEqualTo(1);
    assertThat(crudo.getFaltante()).isEqualTo(2);
    assertThat(r.isUsaIntermedios()).isFalse();
  }

  @Test
  void itemInexistenteLanza() {
    assertThatThrownBy(() -> CraftingCalculatorService.calcular(List.of(new Demanda("diamante", 1)),
        catalogo()::get, stock()))
        .isInstanceOf(InventoryException.class)
        .hasMessageContaining("diamante");
  }

  @Test
  void limiteDeExpansionLanza() {
    var c = catalogo();
    c.put("bucle", crafted("bucle", "Bucle", ing("bucle", 1)));
    assertThatThrownBy(() -> CraftingCalculatorService.calcular(List.of(new Demanda("bucle", 1)), c::get, stock()))
        .isInstanceOf(InventoryException.class)
        .hasMessageContaining("límite");
  }

  @Test
  void calcularCrafteoNoUsaStockDelObjetivo() throws Exception {
    var r = CraftingCalculatorService.calcularCrafteo("palos", 1, catalogo()::get, stock("palos", 9, "tablones", 2));

    var raiz = r.getArbol().get(0);
    assertThat(raiz.getUsadoDeInventario()).isZero();
    assertThat(raiz.getAFabricar()).isEqualTo(1);
    assertThat(r.getConsumidosDeInventario()).extracting(MaterialCantidad::getItemId).containsExactly("tablones");
  }

  @Test
  void aplicarConsumoDescuentaIntermediosYSumaObjetivo() {
    var consumidos = List.of(new MaterialCantidad("tablones", "Tablones", 1), new MaterialCantidad("tronco", "Tronco", 3));

    var r = CraftingCalculatorService.aplicarConsumo(stock("tablones", 1, "tronco", 5), consumidos, "pala", 1);

    assertThat(r).doesNotContainKey("tablones").containsEntry("tronco", 2L).containsEntry("pala", 1L);
  }

  @Test
  void aplicarConsumoRevalidaStock() {
    var consumidos = List.of(new MaterialCantidad("tablones", "Tablones", 2));
    assertThatThrownBy(() -> CraftingCalculatorService.aplicarConsumo(stock("tablones", 1), consumidos, "palos", 1))
        .isInstanceOf(InventoryException.class)
        .hasMessage("No tienes suficientes materiales.");
  }

  @Test
  void planDerivaMaterialesYCraftable() throws Exception {
    var plan = CraftingCalculatorService.armarPlan("pala", 1, catalogo()::get, stock("tablones", 1, "tronco", 3));

    assertThat(plan.isCraftable()).isTrue();
    assertThat(plan.getConsumidos()).extracting(MaterialCantidad::getItemId, MaterialCantidad::getCantidad)
        .containsExactly(tuple("tablones", 1L), tuple("tronco", 3L));
    assertThat(plan.getMateriales()).extracting(MaterialRequirement::getItemId, MaterialRequirement::getRequerido,
            MaterialRequirement::getDisponible, MaterialRequirement::getFaltante)
        .containsExactly(tuple("tablones", 1L, 1L, 0L), tuple("tronco", 3L, 3L, 0L));
    assertThat(plan.isUsaIntermedios()).isTrue();
  }

  @Test
  void planNoCraftableReportaFaltante() throws Exception {
    var plan = CraftingCalculatorService.armarPlan("pala", 1, catalogo()::get, stock("tronco", 1));

    assertThat(plan.isCraftable()).isFalse();
    assertThat(plan.getMateriales()).extracting(MaterialRequirement::getItemId, MaterialRequirement::getRequerido,
            MaterialRequirement::getDisponible, MaterialRequirement::getFaltante)
        .containsExactly(tuple("tronco", 4L, 1L, 3L));
  }

  @Test
  void rawTargetIsDetected() {
    assertThat(CraftingCalculatorService.isRaw(raw("tronco", "Tronco"))).isTrue();
    assertThat(CraftingCalculatorService.isRaw(catalogo().get("pala"))).isFalse();
  }
}
