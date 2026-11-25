package cl.grupo5.proyectominecraft.inventario;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Un paso del desglose: cuánto se pide, cuánto sale del inventario, cuánto se fabrica y cuánto falta. */
@Data
public class NodoCrafteo {
  public static final String MATERIA_PRIMA = "MATERIA_PRIMA";
  public static final String CRAFTEO = "CRAFTEO";
  public static final String HORNO = "HORNO";

  private String itemId;
  private String nombre;
  private long requerido;
  private long usadoDeInventario;
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private long aFabricar;
  private long faltante;
  private String tipo;
  private List<NodoCrafteo> hijos = new ArrayList<>();

  @JsonProperty("aFabricar")
  public long getAFabricar() {
    return aFabricar;
  }

  public void setAFabricar(long aFabricar) {
    this.aFabricar = aFabricar;
  }
}
