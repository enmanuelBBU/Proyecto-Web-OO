package cl.grupo5.proyectominecraft.proyectos;

import java.util.Set;

public enum EstadoProyecto {
  PLANIFICACION("EN_CONSTRUCCION", "CANCELADO"),
  EN_CONSTRUCCION("COMPLETADO", "CANCELADO"),
  COMPLETADO(),
  CANCELADO("PLANIFICACION");

  private final Set<String> transicionesValidas;

  EstadoProyecto(String... transicionesValidas) {
    this.transicionesValidas = Set.of(transicionesValidas);
  }

  public boolean puedeTransicionarA(String destino) {
    if (destino == null) return false;
    return transicionesValidas.contains(destino.toUpperCase());
  }

  public Set<String> getTransicionesValidas() {
    return transicionesValidas;
  }

  public static EstadoProyecto fromString(String s) {
    if (s == null) throw new IllegalArgumentException("Estado no puede ser nulo");
    try {
      return EstadoProyecto.valueOf(s.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("El estado '" + s + "' no es válido.");
    }
  }
}
