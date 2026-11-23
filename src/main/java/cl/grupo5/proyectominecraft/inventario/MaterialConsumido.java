package cl.grupo5.proyectominecraft.inventario;

import lombok.Data;

@Data
public class MaterialConsumido {
  private String itemId;
  private String nombre;
  private long cantidad;
}
