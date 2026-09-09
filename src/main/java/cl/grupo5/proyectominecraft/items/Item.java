package cl.grupo5.proyectominecraft.items;

import com.google.cloud.firestore.annotation.Exclude;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class Item {
  private String id;
  @NotBlank
  private String nombre;
  private String categoria;
  private boolean esMateriaPrima;
  private String fullId;
  private String tipoVisual;
  @Exclude
  private String textura;
  private List<String> recetaMatriz;
  private List<Ingrediente> ingredientesParaCalculo;
}
