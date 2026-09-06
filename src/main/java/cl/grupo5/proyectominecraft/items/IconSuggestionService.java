package cl.grupo5.proyectominecraft.items;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class IconSuggestionService {
  private static final String SEARCH_URL = "https://blocksitems.com/api/v1/items?search={q}";

  private final RestTemplate rt = new RestTemplate();
  private final MinecraftEsEn dictionary;

  public IconSuggestionService(MinecraftEsEn dictionary) {
    this.dictionary = dictionary;
  }

  public List<IconCandidate> suggest(String nombre) {
    var translated = dictionary.translate(nombre);
    var results = search(translated);
    if (results.isEmpty()) {
      results = search(nombre);
    }
    return rank(results, translated.isBlank() ? nombre : translated);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> search(String query) {
    if (query == null || query.isBlank()) return List.of();
    var response = rt.getForObject(SEARCH_URL, Map.class, query);
    if (response == null || !(response.get("data") instanceof List<?> data)) return List.of();
    return (List<Map<String, Object>>) (List<?>) data;
  }

  static List<IconCandidate> rank(List<Map<String, Object>> candidates, String query) {
    var queryTokens = tokenize(query);
    return candidates.stream()
        .sorted(Comparator
            .<Map<String, Object>>comparingInt(c -> "minecraft".equals(c.get("namespace")) ? 0 : 1)
            .thenComparing(c -> -overlap(queryTokens, tokenize(String.valueOf(c.get("display_name"))))))
        .limit(5)
        .map(IconSuggestionService::toCandidate)
        .toList();
  }

  private static IconCandidate toCandidate(Map<String, Object> c) {
    var candidate = new IconCandidate();
    var fullId = String.valueOf(c.get("full_id"));
    candidate.setFullId(fullId);
    candidate.setDisplayName(String.valueOf(c.get("display_name")));
    candidate.setIconUrl("https://blocksitems.com/api/v1/items/" + fullId + "/icon?size=64");
    return candidate;
  }

  private static Set<String> tokenize(String s) {
    if (s == null) return Set.of();
    return Arrays.stream(s.toLowerCase().split("[^a-z0-9]+"))
        .filter(t -> !t.isBlank())
        .collect(Collectors.toSet());
  }

  private static long overlap(Set<String> a, Set<String> b) {
    return a.stream().filter(b::contains).count();
  }
}
