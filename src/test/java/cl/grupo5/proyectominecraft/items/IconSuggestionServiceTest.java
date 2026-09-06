package cl.grupo5.proyectominecraft.items;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IconSuggestionServiceTest {

  @Test
  void ranksMinecraftNamespaceAboveOtherNamespacesForSameQuery() {
    var candidates = List.of(
        Map.<String, Object>of("full_id", "somemod:oak_planks", "namespace", "somemod", "display_name", "Oak Planks"),
        Map.<String, Object>of("full_id", "minecraft:oak_planks", "namespace", "minecraft", "display_name", "Oak Planks")
    );

    var result = IconSuggestionService.rank(candidates, "oak planks");

    assertThat(result.get(0).getFullId()).isEqualTo("minecraft:oak_planks");
  }

  @Test
  void limitsResultsToFive() {
    var candidates = List.of(
        Map.<String, Object>of("full_id", "minecraft:a", "namespace", "minecraft", "display_name", "A"),
        Map.<String, Object>of("full_id", "minecraft:b", "namespace", "minecraft", "display_name", "B"),
        Map.<String, Object>of("full_id", "minecraft:c", "namespace", "minecraft", "display_name", "C"),
        Map.<String, Object>of("full_id", "minecraft:d", "namespace", "minecraft", "display_name", "D"),
        Map.<String, Object>of("full_id", "minecraft:e", "namespace", "minecraft", "display_name", "E"),
        Map.<String, Object>of("full_id", "minecraft:f", "namespace", "minecraft", "display_name", "F")
    );

    var result = IconSuggestionService.rank(candidates, "");

    assertThat(result).hasSize(5);
  }

  @Test
  void buildsIconUrlFromFullId() {
    var candidates = List.of(
        Map.<String, Object>of("full_id", "minecraft:clay_ball", "namespace", "minecraft", "display_name", "Clay")
    );

    var result = IconSuggestionService.rank(candidates, "clay");

    assertThat(result.get(0).getIconUrl())
        .isEqualTo("https://blocksitems.com/api/v1/items/minecraft:clay_ball/icon?size=64");
  }
}
