package cl.grupo5.proyectominecraft.inventario;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ResultadoFaltantes {
  private List<NodoCrafteo> arbol = new ArrayList<>();
  /** Todo lo que se toma del inventario: materias primas e intermedios. */
  private List<MaterialCantidad> consumidosDeInventario = new ArrayList<>();
  /** Materias primas sin stock suficiente. */
  private List<MaterialCantidad> faltantes = new ArrayList<>();
  /** true si algún ítem intermedio (no materia prima) se tomó del inventario. */
  private boolean usaIntermedios;
}
