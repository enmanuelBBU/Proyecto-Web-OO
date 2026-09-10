package cl.grupo5.proyectominecraft.inventario;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryServiceTest {

  @Test
  void mergeSetReplacesExistingQuantity() {
    var current = Map.of("tronco_de_roble", 10L);

    var updated = InventoryService.mergeSet(current, "tronco_de_roble", 3);

    assertThat(updated).containsEntry("tronco_de_roble", 3L);
  }

  @Test
  void mergeSetInsertsAbsentEntry() {
    var updated = InventoryService.mergeSet(Map.of(), "tronco_de_roble", 10);

    assertThat(updated).containsEntry("tronco_de_roble", 10L);
  }

  @Test
  void mergeSetWithZeroRemovesEntry() {
    var current = Map.of("tronco_de_roble", 5L);

    var updated = InventoryService.mergeSet(current, "tronco_de_roble", 0);

    assertThat(updated).doesNotContainKey("tronco_de_roble");
  }

  @Test
  void mergeAddOntoAbsentEntryTreatsBaseAsZero() {
    var updated = InventoryService.mergeAdd(Map.of(), "tronco_de_roble", 4);

    assertThat(updated).containsEntry("tronco_de_roble", 4L);
  }

  @Test
  void mergeAddNegativeDeltaSubtracts() {
    var current = Map.of("tronco_de_roble", 10L);

    var updated = InventoryService.mergeAdd(current, "tronco_de_roble", -4);

    assertThat(updated).containsEntry("tronco_de_roble", 6L);
  }

  @Test
  void mergeAddClampsAtZeroAndRemovesEntry() {
    var current = Map.of("tronco_de_roble", 3L);

    var updated = InventoryService.mergeAdd(current, "tronco_de_roble", -10);

    assertThat(updated).doesNotContainKey("tronco_de_roble");
  }

  @Test
  void readItemsWithMissingDocumentReturnsEmptyMap() {
    assertThat(InventoryService.readItems(null)).isEmpty();
  }

  @Test
  void mergeDoesNotMutateInput() {
    var current = new LinkedHashMap<>(Map.of("tronco_de_roble", 5L));

    InventoryService.mergeSet(current, "tronco_de_roble", 9);

    assertThat(current).containsEntry("tronco_de_roble", 5L);
  }
}
