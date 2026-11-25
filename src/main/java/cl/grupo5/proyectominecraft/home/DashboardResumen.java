package cl.grupo5.proyectominecraft.home;

import cl.grupo5.proyectominecraft.proyectos.Proyecto;

import java.util.List;

/**
 * KPIs del inicio. Un campo null significa que su fuente falló y la vista muestra "—".
 * {@code recientes} nunca es null.
 */
public record DashboardResumen(
    Integer proyectosActivos,
    Integer proyectosTotal,
    Long unidadesInventario,
    Integer tiposInventario,
    Integer crafteos,
    Integer mundos,
    List<Proyecto> recientes) {

  public DashboardResumen {
    recientes = recientes == null ? List.of() : recientes;
  }
}
