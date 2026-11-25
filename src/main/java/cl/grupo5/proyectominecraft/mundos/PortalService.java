package cl.grupo5.proyectominecraft.mundos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Reglas de portales sobre la lista {@code Mundo.portales}. Todo puro: quien llama persiste el mundo. */
public final class PortalService {
  public static final String NO_ENCONTRADO = "Portal no encontrado.";
  static final int MAX_NOMBRE = 60;

  private PortalService() {}

  public record PortalCercano(Portal portal, long distancia) {}

  public static List<Portal> portales(Mundo m) {
    return m == null || m.getPortales() == null ? List.of() : m.getPortales();
  }

  public static Portal buscar(Mundo m, String pid) {
    if (pid == null) return null;
    return portales(m).stream().filter(p -> pid.equals(p.getId())).findFirst().orElse(null);
  }

  private static Portal requerir(Mundo m, String pid) {
    var p = buscar(m, pid);
    if (p == null) throw new MundoValidationException(NO_ENCONTRADO);
    return p;
  }

  static Portal normalizar(Portal p) {
    var nombre = p.getNombre() == null ? "" : p.getNombre().trim();
    if (nombre.isEmpty()) throw new MundoValidationException("El nombre del portal es obligatorio.");
    if (nombre.length() > MAX_NOMBRE) {
      throw new MundoValidationException("El nombre del portal no puede superar los " + MAX_NOMBRE + " caracteres.");
    }
    var dim = NetherCalculator.dimension(p.getDimension());
    NetherCalculator.validar(dim, p.getX(), p.getY(), p.getZ());
    p.setNombre(nombre);
    p.setDimension(dim.name());
    return p;
  }

  public static Portal agregar(Mundo m, Portal datos) {
    normalizar(datos);
    datos.setId(UUID.randomUUID().toString());
    var enlace = datos.getEnlazadoId();
    datos.setEnlazadoId(null);
    if (m.getPortales() == null) m.setPortales(new ArrayList<>());
    m.getPortales().add(datos);
    if (enlace != null && !enlace.isBlank()) enlazar(m, datos.getId(), enlace.trim());
    return datos;
  }

  /** Copia nombre, dimensión y coordenadas; {@code cambios.enlazadoId} vacío desenlaza, un id enlaza. */
  public static Portal actualizar(Mundo m, String pid, Portal cambios) {
    var actual = requerir(m, pid);
    normalizar(cambios);
    boolean cambiaDimension = !cambios.getDimension().equals(actual.getDimension());
    actual.setNombre(cambios.getNombre());
    actual.setDimension(cambios.getDimension());
    actual.setX(cambios.getX());
    actual.setY(cambios.getY());
    actual.setZ(cambios.getZ());
    var enlace = cambios.getEnlazadoId() == null ? "" : cambios.getEnlazadoId().trim();
    if (cambiaDimension || enlace.isEmpty()) desenlazar(m, pid);
    if (!enlace.isEmpty() && !enlace.equals(actual.getEnlazadoId())) enlazar(m, pid, enlace);
    return actual;
  }

  public static void eliminar(Mundo m, String pid) {
    var p = requerir(m, pid);
    desenlazar(m, pid);
    m.getPortales().remove(p);
  }

  public static void enlazar(Mundo m, String aId, String bId) {
    if (Objects.equals(aId, bId)) throw new MundoValidationException("Un portal no puede enlazarse consigo mismo.");
    var a = requerir(m, aId);
    var b = requerir(m, bId);
    if (Objects.equals(a.getDimension(), b.getDimension())) {
      throw new MundoValidationException("Solo se pueden enlazar portales de dimensiones distintas.");
    }
    desenlazar(m, aId);
    desenlazar(m, bId);
    a.setEnlazadoId(bId);
    b.setEnlazadoId(aId);
  }

  public static void desenlazar(Mundo m, String pid) {
    var p = buscar(m, pid);
    if (p == null || p.getEnlazadoId() == null) return;
    var otro = buscar(m, p.getEnlazadoId());
    if (otro != null && pid.equals(otro.getEnlazadoId())) otro.setEnlazadoId(null);
    p.setEnlazadoId(null);
  }

  /** Crea el portal de la otra dimensión con coordenadas convertidas (Y acotada) y los enlaza. */
  public static Portal crearPareado(Mundo m, String pid) {
    var origen = requerir(m, pid);
    var dim = Dimension.fromString(origen.getDimension());
    var c = NetherCalculator.convertir(dim, origen.getX(), origen.getY(), origen.getZ());
    var destino = dim.opuesta();
    var par = new Portal();
    var nombre = origen.getNombre() + " (par)";
    par.setNombre(nombre.length() > MAX_NOMBRE ? nombre.substring(0, MAX_NOMBRE) : nombre);
    par.setDimension(destino.name());
    par.setX(c.x());
    par.setY(NetherCalculator.acotarY(destino, c.y()));
    par.setZ(c.z());
    agregar(m, par);
    enlazar(m, pid, par.getId());
    return par;
  }

  /** Portal del Overworld más cercano a (x, z), por distancia horizontal euclídea redondeada. */
  public static Optional<PortalCercano> masCercano(List<Portal> portales, int x, int z) {
    if (portales == null) return Optional.empty();
    return portales.stream()
        .filter(p -> Dimension.OVERWORLD.name().equals(p.getDimension()))
        .map(p -> new PortalCercano(p, Math.round(Math.hypot((double) p.getX() - x, (double) p.getZ() - z))))
        .min(Comparator.comparingLong(PortalCercano::distancia));
  }
}
