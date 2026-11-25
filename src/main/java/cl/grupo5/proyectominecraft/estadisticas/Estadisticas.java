package cl.grupo5.proyectominecraft.estadisticas;

import java.util.List;
import java.util.Map;

/** Datos del dashboard de estadísticas. Un campo null significa que su fuente falló y la vista muestra "—". */
public record Estadisticas(
    Map<String, Integer> porEstado,
    List<EntradaRanking> masUsados,
    List<EntradaRanking> escasos) {}
