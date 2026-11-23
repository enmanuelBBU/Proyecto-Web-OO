package cl.grupo5.proyectominecraft.proyectos;

public class ProyectoAlreadyExistsException extends RuntimeException {
  public ProyectoAlreadyExistsException(String nombre) {
    super("Ya tienes un proyecto llamado '" + nombre + "'.");
  }
}
