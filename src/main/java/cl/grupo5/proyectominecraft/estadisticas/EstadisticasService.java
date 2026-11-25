package cl.grupo5.proyectominecraft.estadisticas;

import cl.grupo5.proyectominecraft.inventario.CraftingCalculatorService;
import cl.grupo5.proyectominecraft.inventario.InventoryException;
import cl.grupo5.proyectominecraft.inventario.InventoryService;
import cl.grupo5.proyectominecraft.items.Item;
import cl.grupo5.proyectominecraft.items.ItemService;
import cl.grupo5.proyectominecraft.proyectos.EstadoProyecto;
import cl.grupo5.proyectominecraft.proyectos.Proyecto;
import cl.grupo5.proyectominecraft.proyectos.ProyectoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Agregaciones del dashboard /estadisticas. Cada fuente se carga por separado: si una falla, las demás siguen. */
@Service
public class EstadisticasService {
  private static final Logger log = LoggerFactory.getLogger(EstadisticasService.class);
  static final int TOP = 10;
  private static final Map<String, String> ETIQUETAS = Map.of(
      "PLANIFICACION", "Planificación",
      "EN_CONSTRUCCION", "En construcción",
      "COMPLETADO", "Completado",
      "CANCELADO", "Cancelado");

  private final ProyectoService proyectoService;
  private final ItemService itemService;
  private final InventoryService inventoryService;

  public EstadisticasService(ProyectoService proyectoService, ItemService itemService, InventoryService inventoryService) {
    this.proyectoService = proyectoService;
    this.itemService = itemService;
    this.inventoryService = inventoryService;
  }

  public Estadisticas resumen(String uid, String rol) {
    List<Proyecto> proyectos = null;
    try {
      proyectos = proyectoService.list(null, null, null, uid, rol);
    } catch (Exception e) {
      log.warn("Estadísticas: no se pudieron cargar los proyectos de {}", uid, e);
    }
    List<Item> catalogo = null;
    try {
      catalogo = itemService.list(null, null);
    } catch (Exception e) {
      log.warn("Estadísticas: no se pudo cargar el catálogo", e);
    }
    Map<String, Long> stock = null;
    try {
      stock = inventoryService.get(uid);
    } catch (Exception e) {
      log.warn("Estadísticas: no se pudo cargar el inventario de {}", uid, e);
    }

    var porEstado = proyectos != null ? porEstado(proyectos) : null;
    var masUsados = catalogo != null ? itemsMasUsados(catalogo, TOP) : null;
    List<EntradaRanking> escasos = null;
    if (proyectos != null && catalogo != null && stock != null) {
      var mapa = new HashMap<String, Item>();
      for (var it : catalogo) mapa.put(it.getId(), it);
      escasos = materialesEscasos(proyectos, mapa, stock, TOP);
    }
    return new Estadisticas(porEstado, masUsados, escasos);
  }

  /** Conteo por estado en el orden del enum (los cuatro siempre presentes); null cuenta como PLANIFICACION. */
  static Map<String, Integer> porEstado(List<Proyecto> proyectos) {
    var r = new LinkedHashMap<String, Integer>();
    for (var e : EstadoProyecto.values()) r.put(e.name(), 0);
    for (var p : proyectos) {
      var estado = p.getEstado() == null || p.getEstado().isBlank() ? "PLANIFICACION" : p.getEstado().toUpperCase();
      r.merge(estado, 1, Integer::sum);
    }
    return r;
  }

  /** Top de ingredientes por cantidad de recetas distintas que los usan; desempata por cantidad total y nombre. */
  static List<EntradaRanking> itemsMasUsados(List<Item> catalogo, int limite) {
    var nombres = new HashMap<String, String>();
    for (var it : catalogo) nombres.put(it.getId(), it.getNombre());
    var recetas = new LinkedHashMap<String, Long>();
    var cantidades = new HashMap<String, Long>();
    for (var it : catalogo) {
      if (it.getIngredientesParaCalculo() == null) continue;
      var vistos = new LinkedHashSet<String>();
      for (var ing : it.getIngredientesParaCalculo()) {
        if (ing == null || ing.getItemId() == null) continue;
        if (vistos.add(ing.getItemId())) recetas.merge(ing.getItemId(), 1L, Long::sum);
        cantidades.merge(ing.getItemId(), (long) ing.getCantidad(), Long::sum);
      }
    }
    var filas = new ArrayList<EntradaRanking>();
    for (var e : recetas.entrySet()) {
      var nombre = nombres.get(e.getKey());
      filas.add(new EntradaRanking(e.getKey(), nombre != null ? nombre : e.getKey(), e.getValue(),
          cantidades.getOrDefault(e.getKey(), 0L)));
    }
    return top(filas, Comparator.comparingLong(EntradaRanking::getValor).reversed()
        .thenComparing(Comparator.comparingLong(EntradaRanking::getSecundario).reversed()), limite);
  }

  /**
   * Faltantes sumados entre los proyectos activos con un único stock compartido (motor de faltantes).
   * Un proyecto cuyo cálculo falla (p. ej. ítem borrado) se omite sin consumir stock.
   */
  static List<EntradaRanking> materialesEscasos(List<Proyecto> proyectos, Map<String, Item> catalogo,
                                                Map<String, Long> stockInicial, int limite) {
    Map<String, Long> stock = new LinkedHashMap<>(stockInicial);
    var faltante = new LinkedHashMap<String, Long>();
    var enProyectos = new HashMap<String, Long>();
    var nombres = new HashMap<String, String>();
    for (var p : proyectos) {
      if (!activo(p)) continue;
      var copia = new LinkedHashMap<>(stock);
      try {
        var r = CraftingCalculatorService.calcular(CraftingCalculatorService.demandasDe(p), catalogo::get, copia);
        stock = copia;
        for (var f : r.getFaltantes()) {
          faltante.merge(f.getItemId(), f.getCantidad(), Long::sum);
          enProyectos.merge(f.getItemId(), 1L, Long::sum);
          nombres.putIfAbsent(f.getItemId(), f.getNombre());
        }
      } catch (InventoryException e) {
        log.debug("Estadísticas: se omite el proyecto {}: {}", p.getId(), e.getMessage());
      } catch (Exception e) {
        log.warn("Estadísticas: error calculando faltantes del proyecto {}", p.getId(), e);
      }
    }
    var filas = new ArrayList<EntradaRanking>();
    for (var e : faltante.entrySet()) {
      filas.add(new EntradaRanking(e.getKey(), nombres.get(e.getKey()), e.getValue(), enProyectos.get(e.getKey())));
    }
    return top(filas, Comparator.comparingLong(EntradaRanking::getValor).reversed(), limite);
  }

  private static boolean activo(Proyecto p) {
    var estado = p.getEstado() == null ? "PLANIFICACION" : p.getEstado();
    return "PLANIFICACION".equals(estado) || "EN_CONSTRUCCION".equals(estado);
  }

  private static List<EntradaRanking> top(List<EntradaRanking> filas, Comparator<EntradaRanking> orden, int limite) {
    return filas.stream()
        .sorted(orden.thenComparing(f -> f.getNombre() == null ? "" : f.getNombre().toLowerCase()))
        .limit(limite)
        .toList();
  }

  /** Mapas y listas simples para serializar con th:inline="javascript". Una clave con null = fuente caída. */
  static Map<String, Object> datosGraficos(Estadisticas e) {
    var d = new LinkedHashMap<String, Object>();
    if (e.porEstado() == null) {
      d.put("porEstado", null);
    } else {
      var serie = new LinkedHashMap<String, Object>();
      serie.put("claves", new ArrayList<>(e.porEstado().keySet()));
      serie.put("labels", e.porEstado().keySet().stream().map(k -> ETIQUETAS.getOrDefault(k, k)).toList());
      serie.put("valores", new ArrayList<>(e.porEstado().values()));
      d.put("porEstado", serie);
    }
    d.put("masUsados", ranking(e.masUsados()));
    d.put("escasos", ranking(e.escasos()));
    return d;
  }

  private static Map<String, Object> ranking(List<EntradaRanking> filas) {
    if (filas == null) return null;
    var serie = new LinkedHashMap<String, Object>();
    serie.put("labels", filas.stream().map(EntradaRanking::getNombre).toList());
    serie.put("valores", filas.stream().map(EntradaRanking::getValor).toList());
    return serie;
  }

  public static String etiquetaEstado(String clave) {
    return ETIQUETAS.getOrDefault(clave, clave);
  }
}
