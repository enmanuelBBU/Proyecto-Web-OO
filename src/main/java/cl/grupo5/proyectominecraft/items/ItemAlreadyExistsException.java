package cl.grupo5.proyectominecraft.items;

public class ItemAlreadyExistsException extends RuntimeException {
  public ItemAlreadyExistsException(String id) {
    super("Ya existe un item con ese nombre.");
  }
}
