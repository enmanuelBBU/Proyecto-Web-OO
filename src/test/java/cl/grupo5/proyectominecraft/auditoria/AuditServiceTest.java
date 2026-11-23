package cl.grupo5.proyectominecraft.auditoria;

import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditServiceTest {

  private EventoAuditoria evento(String fecha, String accion, String entidad, String actor, String nombre) {
    return AuditService.construir("u-" + actor, actor, accion, entidad, nombre.toLowerCase(), nombre, null, fecha);
  }

  @Test
  void construirCopiaTodosLosCampos() {
    var e = AuditService.construir("u1", "Steve", "CREAR", "ITEM", "palo", "Palo", "detalle", "t");
    assertThat(e.getActorUid()).isEqualTo("u1");
    assertThat(e.getActorNombre()).isEqualTo("Steve");
    assertThat(e.getAccion()).isEqualTo("CREAR");
    assertThat(e.getEntidad()).isEqualTo("ITEM");
    assertThat(e.getEntidadId()).isEqualTo("palo");
    assertThat(e.getEntidadNombre()).isEqualTo("Palo");
    assertThat(e.getDetalle()).isEqualTo("detalle");
    assertThat(e.getFecha()).isEqualTo("t");
  }

  @Test
  void registrarNoLanzaSiFirestoreFalla() {
    var db = mock(Firestore.class);
    when(db.collection("auditoria")).thenThrow(new RuntimeException("caído"));
    var s = new MockHttpSession();
    s.setAttribute("uid", "u1");
    s.setAttribute("nombre", "Steve");
    assertThatCode(() -> new AuditService(db).registrar(s, "CREAR", "ITEM", "palo", "Palo", null))
        .doesNotThrowAnyException();
  }

  @Test
  void registrarNoLanzaConSesionNula() {
    var db = mock(Firestore.class);
    when(db.collection("auditoria")).thenThrow(new RuntimeException("caído"));
    assertThatCode(() -> new AuditService(db).registrar(null, "CREAR", "ITEM", "palo", "Palo", null))
        .doesNotThrowAnyException();
  }

  @Test
  void filtrarPorEntidadAccionYTexto() {
    var a = evento("2026-11-01", "CREAR", "ITEM", "Steve", "Palo");
    var b = evento("2026-11-02", "ELIMINAR", "PROYECTO", "Alex", "Castillo");
    var c = evento("2026-11-03", "EDITAR", "ITEM", "Alex", "Antorcha");
    var todos = List.of(a, b, c);
    assertThat(AuditService.filtrar(todos, "ITEM", null, null)).containsExactly(c, a);
    assertThat(AuditService.filtrar(todos, null, "ELIMINAR", null)).containsExactly(b);
    assertThat(AuditService.filtrar(todos, "", "", "alex")).containsExactly(c, b);
    assertThat(AuditService.filtrar(todos, null, null, "castillo")).containsExactly(b);
  }

  @Test
  void filtrarOrdenaMasRecientePrimero() {
    var viejo = evento("2026-01-01T00:00:00Z", "CREAR", "ITEM", "Steve", "Palo");
    var nuevo = evento("2026-11-23T00:00:00Z", "CREAR", "ITEM", "Steve", "Mesa");
    assertThat(AuditService.filtrar(List.of(viejo, nuevo), null, null, null)).containsExactly(nuevo, viejo);
  }
}
