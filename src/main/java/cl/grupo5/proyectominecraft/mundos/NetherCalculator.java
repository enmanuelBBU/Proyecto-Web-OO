package cl.grupo5.proyectominecraft.mundos;

/** Conversión Overworld ↔ Nether (1 bloque del Nether = 8 del Overworld en X/Z; Y no cambia). */
public final class NetherCalculator {
  public static final int XZ_OVERWORLD = 30_000_000;
  public static final int XZ_NETHER = XZ_OVERWORLD / 8;
  public static final int Y_MIN_OVERWORLD = -64;
  public static final int Y_MAX_OVERWORLD = 320;
  public static final int Y_MIN_NETHER = 0;
  public static final int Y_MAX_NETHER = 255;

  private NetherCalculator() {}

  public static Coordenadas convertir(Dimension origen, int x, int y, int z) {
    validar(origen, x, y, z);
    if (origen == Dimension.OVERWORLD) {
      return new Coordenadas(Dimension.NETHER.name(), Math.floorDiv(x, 8), y, Math.floorDiv(z, 8));
    }
    return new Coordenadas(Dimension.OVERWORLD.name(), x * 8, y, z * 8);
  }

  public static void validar(Dimension d, int x, int y, int z) {
    int yMin = d == Dimension.NETHER ? Y_MIN_NETHER : Y_MIN_OVERWORLD;
    int yMax = d == Dimension.NETHER ? Y_MAX_NETHER : Y_MAX_OVERWORLD;
    int xz = d == Dimension.NETHER ? XZ_NETHER : XZ_OVERWORLD;
    if (y < yMin || y > yMax) {
      throw new MundoValidationException("La coordenada Y debe estar entre " + yMin + " y " + yMax + ".");
    }
    if (Math.abs((long) x) > xz || Math.abs((long) z) > xz) {
      throw new MundoValidationException("Las coordenadas X y Z deben estar entre -" + miles(xz) + " y " + miles(xz) + ".");
    }
  }

  public static int acotarY(Dimension d, int y) {
    int yMin = d == Dimension.NETHER ? Y_MIN_NETHER : Y_MIN_OVERWORLD;
    int yMax = d == Dimension.NETHER ? Y_MAX_NETHER : Y_MAX_OVERWORLD;
    return Math.max(yMin, Math.min(yMax, y));
  }

  /** Parsea la dimensión lanzando el error de validación del módulo. */
  public static Dimension dimension(String s) {
    try {
      return Dimension.fromString(s);
    } catch (IllegalArgumentException e) {
      throw new MundoValidationException(e.getMessage());
    }
  }

  private static String miles(int n) {
    return String.format(java.util.Locale.ROOT, "%,d", n).replace(',', '.');
  }
}
