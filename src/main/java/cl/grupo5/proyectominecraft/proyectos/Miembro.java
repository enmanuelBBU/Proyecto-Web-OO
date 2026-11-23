package cl.grupo5.proyectominecraft.proyectos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Miembro {
  private String uid;
  private String nombre;
  private String email;
  private String permiso;   // LECTURA | EDICION
  private String fechaIngreso;
}
