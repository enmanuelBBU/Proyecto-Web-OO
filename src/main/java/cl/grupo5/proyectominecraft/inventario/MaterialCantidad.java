package cl.grupo5.proyectominecraft.inventario;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialCantidad {
  private String itemId;
  private String nombre;
  private long cantidad;
}
