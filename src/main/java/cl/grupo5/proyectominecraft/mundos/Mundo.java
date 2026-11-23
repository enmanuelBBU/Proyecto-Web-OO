package cl.grupo5.proyectominecraft.mundos;

import lombok.Data;

@Data
public class Mundo {
  private String id;
  private String uid;
  private String nombre;
  private String version;
  private String modoJuego;
  private String semilla;
  private String fechaCreacion;
}
