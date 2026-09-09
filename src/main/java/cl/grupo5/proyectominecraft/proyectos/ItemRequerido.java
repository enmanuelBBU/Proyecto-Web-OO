package cl.grupo5.proyectominecraft.proyectos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemRequerido {
  @NotBlank
  private String itemId;

  @Min(1)
  private int cantidad;
}
