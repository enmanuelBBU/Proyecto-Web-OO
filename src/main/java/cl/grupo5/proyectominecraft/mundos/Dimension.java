package cl.grupo5.proyectominecraft.mundos;

public enum Dimension {
  OVERWORLD, NETHER;

  public static Dimension fromString(String s) {
    if (s == null) throw new IllegalArgumentException("Dimensión inválida.");
    try {
      return Dimension.valueOf(s.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Dimensión inválida.");
    }
  }

  public Dimension opuesta() {
    return this == OVERWORLD ? NETHER : OVERWORLD;
  }
}
