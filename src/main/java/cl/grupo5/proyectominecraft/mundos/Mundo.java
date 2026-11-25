package cl.grupo5.proyectominecraft.mundos;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Mundo {
  private String id;
  private String uid;
  private String nombre;
  private String version;
  private String modoJuego;
  private String semilla;
  private String fechaCreacion;
  private List<Portal> portales = new ArrayList<>();  // null en documentos antiguos
}
