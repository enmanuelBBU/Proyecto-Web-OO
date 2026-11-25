package cl.grupo5.proyectominecraft.estadisticas;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fila de un top. Ítems más usados: valor = recetas que lo usan, secundario = cantidad total.
 * Materiales escasos: valor = faltante sumado, secundario = proyectos donde falta.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntradaRanking {
  private String itemId;
  private String nombre;
  private long valor;
  private long secundario;
}
