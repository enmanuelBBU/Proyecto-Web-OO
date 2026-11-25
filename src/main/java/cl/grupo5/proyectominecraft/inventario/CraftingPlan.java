package cl.grupo5.proyectominecraft.inventario;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CraftingPlan {
  private String targetItemId;
  private int cantidad;
  /** Resumen por ítem (compatibilidad): requerido = usado del inventario + faltante. */
  private List<MaterialRequirement> materiales = new ArrayList<>();
  private boolean craftable;
  private String tipoReceta;
  private List<NodoCrafteo> arbol = new ArrayList<>();
  /** Exactamente lo que craft() descuenta del inventario (incluye intermedios). */
  private List<MaterialCantidad> consumidos = new ArrayList<>();
  private List<MaterialCantidad> faltantes = new ArrayList<>();
  private boolean usaIntermedios;
}
