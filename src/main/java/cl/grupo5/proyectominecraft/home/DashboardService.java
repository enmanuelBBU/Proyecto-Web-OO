package cl.grupo5.proyectominecraft.home;

import cl.grupo5.proyectominecraft.inventario.HistorialCrafteoService;
import cl.grupo5.proyectominecraft.inventario.InventoryService;
import cl.grupo5.proyectominecraft.mundos.MundoService;
import cl.grupo5.proyectominecraft.proyectos.Proyecto;
import cl.grupo5.proyectominecraft.proyectos.ProyectoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Arma los KPIs del inicio. Cada fuente se consulta por separado: si una falla, las demás siguen. */
@Service
public class DashboardService {
  private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
  static final int LIMITE_RECIENTES = 5;

  private final ProyectoService proyectoService;
  private final InventoryService inventoryService;
  private final HistorialCrafteoService historialService;
  private final MundoService mundoService;

  public DashboardService(ProyectoService proyectoService, InventoryService inventoryService,
                          HistorialCrafteoService historialService, MundoService mundoService) {
    this.proyectoService = proyectoService;
    this.inventoryService = inventoryService;
    this.historialService = historialService;
    this.mundoService = mundoService;
  }

  public DashboardResumen resumen(String uid, String rol) {
    Integer activos = null;
    Integer total = null;
    List<Proyecto> recientes = List.of();
    try {
      var lista = proyectoService.list(null, null, null, uid, rol);
      activos = contarActivos(lista);
      total = lista.size();
      recientes = recientes(lista, LIMITE_RECIENTES);
    } catch (Exception e) {
      log.warn("Dashboard: no se pudieron cargar los proyectos de {}", uid, e);
    }

    Long unidades = null;
    Integer tipos = null;
    try {
      var inv = inventoryService.get(uid);
      unidades = unidades(inv);
      tipos = tipos(inv);
    } catch (Exception e) {
      log.warn("Dashboard: no se pudo cargar el inventario de {}", uid, e);
    }

    Integer crafteos = null;
    try {
      crafteos = historialService.listar(uid).size();
    } catch (Exception e) {
      log.warn("Dashboard: no se pudo cargar el historial de crafteo de {}", uid, e);
    }

    Integer mundos = null;
    try {
      mundos = mundoService.listar(uid).size();
    } catch (Exception e) {
      log.warn("Dashboard: no se pudieron cargar los mundos de {}", uid, e);
    }

    return new DashboardResumen(activos, total, unidades, tipos, crafteos, mundos, recientes);
  }

  /** Proyectos en PLANIFICACION o EN_CONSTRUCCION; estado null cuenta como PLANIFICACION. */
  static int contarActivos(List<Proyecto> proyectos) {
    int n = 0;
    for (var p : proyectos) {
      var estado = p.getEstado() == null ? "PLANIFICACION" : p.getEstado();
      if ("PLANIFICACION".equals(estado) || "EN_CONSTRUCCION".equals(estado)) n++;
    }
    return n;
  }

  /** Ordena por fechaCreacion (ISO) descendente, nulls al final, y corta en {@code limite}. */
  static List<Proyecto> recientes(List<Proyecto> proyectos, int limite) {
    return proyectos.stream()
        .sorted(Comparator.comparing(Proyecto::getFechaCreacion, Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(limite)
        .toList();
  }

  static long unidades(Map<String, Long> inventario) {
    return inventario.values().stream().filter(v -> v != null && v > 0).mapToLong(Long::longValue).sum();
  }

  static int tipos(Map<String, Long> inventario) {
    return (int) inventario.values().stream().filter(v -> v != null && v > 0).count();
  }
}
