package cl.grupo5.proyectominecraft.proyectos;

public class ProyectoAlreadyExistsException extends RuntimeException {
  public ProyectoAlreadyExistsException(String id) {
    super("Ya existe un proyecto con el ID '" + id + "'.");
  }
}
