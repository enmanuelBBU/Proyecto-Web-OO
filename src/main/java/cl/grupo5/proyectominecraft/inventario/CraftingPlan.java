package cl.grupo5.proyectominecraft.inventario;

import lombok.Data;

import java.util.List;

@Data
public class CraftingPlan {
  private String targetItemId;
  private int cantidad;
  private List<MaterialRequirement> materiales;
  private boolean craftable;
}
