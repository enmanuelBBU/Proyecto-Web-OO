package cl.grupo5.proyectominecraft.inventario;

import lombok.Data;

@Data
public class MaterialRequirement {
  private String itemId;
  private String nombre;
  private long requerido;
  private long disponible;
  private long faltante;
}
