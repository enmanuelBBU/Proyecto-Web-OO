package cl.grupo5.proyectominecraft.mundos;

import lombok.Data;

/** Portal del Nether guardado dentro de un mundo (lista Mundo.portales). */
@Data
public class Portal {
  private String id;          // UUID
  private String nombre;
  private String dimension;   // OVERWORLD | NETHER
  private int x;
  private int y;
  private int z;
  private String enlazadoId;  // id del portal par (otra dimensión, mismo mundo) o null
}
