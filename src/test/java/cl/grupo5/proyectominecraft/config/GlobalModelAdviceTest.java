package cl.grupo5.proyectominecraft.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalModelAdviceTest {

  private final GlobalModelAdvice advice = new GlobalModelAdvice();

  private static MockHttpSession con(String attr, Object valor) {
    var s = new MockHttpSession();
    if (valor != null) s.setAttribute(attr, valor);
    return s;
  }

  @Test
  void isAdminTrueOnlyForAdminRol() {
    assertTrue(advice.isAdmin(con("rol", "ADMIN")));
    assertFalse(advice.isAdmin(con("rol", "USUARIO")));
    assertFalse(advice.isAdmin(con("rol", null)));
  }

  @Test
  void nombreUsuarioFallsBack() {
    assertEquals("Constructor", advice.nombreUsuario(con("nombre", null)));
    assertEquals("Constructor", advice.nombreUsuario(con("nombre", "  ")));
    assertEquals("Alex", advice.nombreUsuario(con("nombre", "Alex")));
  }
}
