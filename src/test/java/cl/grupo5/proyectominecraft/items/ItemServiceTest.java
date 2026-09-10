package cl.grupo5.proyectominecraft.items;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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

  @Test
  void validateRecetaMatrizAcceptsNullReceta() {
    var item = item("Piedra", "Mineral", true);
    item.setRecetaMatriz(null);

    ItemService.validateRecetaMatriz(item);
  }

  @Test
  void validateRecetaMatrizAcceptsEmptyReceta() {
    var item = item("Piedra", "Mineral", true);
    item.setRecetaMatriz(List.of());

    ItemService.validateRecetaMatriz(item);
  }

  @Test
  void validateRecetaMatrizRejectsWrongSize() {
    var item = item("Cama", "Utilidad", false);
    item.setRecetaMatriz(List.of("lana", "lana"));

    assertThatThrownBy(() -> ItemService.validateRecetaMatriz(item))
        .isInstanceOf(RecipeValidationException.class)
        .hasMessage("La receta debe tener exactamente 9 casillas.");
  }

  @Test
  void validateRecetaMatrizRejectsRecipeOnMateriaPrima() {
    var item = item("Arcilla", "Materia prima", true);
    var receta = new ArrayList<String>(Collections.nCopies(9, null));
    receta.set(0, "lana");
    item.setRecetaMatriz(receta);

    assertThatThrownBy(() -> ItemService.validateRecetaMatriz(item))
        .isInstanceOf(RecipeValidationException.class)
        .hasMessage("Una materia prima no puede tener receta.");
  }

  @Test
  void validateRecetaMatrizAcceptsAllNullNineSlotRecetaOnMateriaPrima() {
    var item = item("Arcilla", "Materia prima", true);
    item.setRecetaMatriz(new ArrayList<>(Collections.nCopies(9, null)));

    ItemService.validateRecetaMatriz(item); // should not throw
  }

  @Test
  void computeIngredientesOnAllNullNineSlotRecetaReturnsEmptyList() {
    assertThat(ItemService.computeIngredientes(new ArrayList<>(Collections.nCopies(9, null)))).isEmpty();
  }

  @Test
  void computeIngredientesCountsRepeatedIdsInOrder() {
    var receta = Arrays.asList(
        "lana", "lana", "lana",
        "tablones_de_roble", "tablones_de_roble", "tablones_de_roble",
        null, null, null);

    var result = ItemService.computeIngredientes(receta);

    assertThat(result).extracting(Ingrediente::getItemId, Ingrediente::getCantidad)
        .containsExactly(tuple("lana", 3), tuple("tablones_de_roble", 3));
  }

  @Test
  void computeIngredientesOnNullOrEmptyRecetaReturnsEmptyList() {
    assertThat(ItemService.computeIngredientes(null)).isEmpty();
    assertThat(ItemService.computeIngredientes(List.of())).isEmpty();
  }

  @Test
  void validarExistenciaAcceptsWhenAllReferencedIdsExist() throws Exception {
    ItemService.validarExistencia(List.of("lana", "tablones_de_roble"), id -> true);
  }

  @Test
  void validarExistenciaRejectsWhenAReferencedIdDoesNotExist() {
    assertThatThrownBy(() -> ItemService.validarExistencia(List.of("lana", "fantasma"), "lana"::equals))
        .isInstanceOf(RecipeValidationException.class)
        .hasMessage("El ingrediente 'fantasma' no existe en el catálogo.");
  }

  @Test
  void validarCiclosAcceptsAcyclicGraph() throws Exception {
    ItemService.validarCiclos("cama", List.of("lana", "tablones_de_roble"), id -> List.of());
  }

  @Test
  void validarCiclosRejectsDirectSelfReference() {
    assertThatThrownBy(() -> ItemService.validarCiclos("torta", List.of("torta"), id -> List.of()))
        .isInstanceOf(RecipeValidationException.class)
        .hasMessage("La receta genera una dependencia circular con 'torta'.");
  }

  @Test
  void validarCiclosRejectsTransitiveCycle() {
    java.util.Map<String, List<String>> recetas = java.util.Map.of(
        "b", List.of("c"),
        "c", List.of("a")
    );

    assertThatThrownBy(() -> ItemService.validarCiclos("a", List.of("b"), id -> recetas.getOrDefault(id, List.of())))
        .isInstanceOf(RecipeValidationException.class)
        .hasMessage("La receta genera una dependencia circular con 'a'.");
  }

  @Test
  void validarCiclosAllowsSharedDependencyWithoutFalsePositive() throws Exception {
    java.util.Map<String, List<String>> recetas = java.util.Map.of(
        "b", List.of("d"),
        "c", List.of("d"),
        "d", List.of()
    );

    ItemService.validarCiclos("a", List.of("b", "c"), id -> recetas.getOrDefault(id, List.of()));
  }
}
