package cl.grupo5.proyectominecraft.mundos;

public enum ModoJuego {
  SUPERVIVENCIA, CREATIVO, AVENTURA, ESPECTADOR, HARDCORE;

  public static ModoJuego fromString(String s) {
    if (s == null) throw new IllegalArgumentException("Modo de juego inválido.");
    try {
      return ModoJuego.valueOf(s.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Modo de juego inválido.");
    }
  }
}
