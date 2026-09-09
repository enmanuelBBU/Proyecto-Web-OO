package cl.grupo5.proyectominecraft.proyectos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistorialEstado {
  private String estadoAnterior;
  private String estadoNuevo;
  private String uid;
  private String nombre;
  private String fecha;
  private String motivo;
}
