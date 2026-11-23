package cl.grupo5.proyectominecraft.inventario;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RegistroCrafteo {
  private String id;
  private String uid;
  private String itemId;
  private String itemNombre;
  private String tipoReceta;   // CRAFTEO | HORNO
  private int cantidad;
  private List<MaterialConsumido> materiales = new ArrayList<>();
  private String fecha;        // ISO-8601
}
