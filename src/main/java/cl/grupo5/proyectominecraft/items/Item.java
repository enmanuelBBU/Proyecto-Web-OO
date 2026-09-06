package cl.grupo5.proyectominecraft.items;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class Item {
  private String id;
  @NotBlank
  private String nombre;
  private String categoria;
  private boolean esMateriaPrima;
}
