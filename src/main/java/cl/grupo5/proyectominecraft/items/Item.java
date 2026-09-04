package cl.grupo5.proyectominecraft.items;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class Item {
  private String id;
  @NotBlank
  private String nombre;
  private String tipo = "BLOQUE";
  @Min(0)
  private int cantidad;
  private String icono;
}
