package cl.grupo5.proyectominecraft.proyectos;

/** Textos de "detalle" para los eventos de auditoría sobre proyectos. */
final class AuditDetalles {
  private AuditDetalles() {}

  static String transicion(Proyecto p) {
    var h = p.getHistorialEstados();
    if (h == null || h.isEmpty()) return "Estado: " + p.getEstado();
    var ultimo = h.get(h.size() - 1);
    return "Estado: " + ultimo.getEstadoAnterior() + " → " + ultimo.getEstadoNuevo();
  }

  static String invitacion(String email, String permiso) {
    return "Invitó a " + (email == null ? "" : email.trim()) + " (" + (permiso == null ? "" : permiso.trim().toUpperCase()) + ")";
  }

  static String cambioPermiso(Proyecto p, String miembroUid, String permiso) {
    return "Permiso de " + emailDe(p, miembroUid) + " → " + (permiso == null ? "" : permiso.trim().toUpperCase());
  }

  static String quitado(Proyecto antes, String miembroUid) {
    return "Quitó a " + emailDe(antes, miembroUid);
  }

  private static String emailDe(Proyecto p, String miembroUid) {
    if (p == null || p.getMiembros() == null) return miembroUid;
    return p.getMiembros().stream()
        .filter(m -> miembroUid.equals(m.getUid()))
        .map(Miembro::getEmail)
        .findFirst().orElse(miembroUid);
  }
}
