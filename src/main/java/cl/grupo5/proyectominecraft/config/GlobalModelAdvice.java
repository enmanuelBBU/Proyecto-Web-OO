package cl.grupo5.proyectominecraft.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Atributos de modelo comunes a todas las vistas (topbar). */
@ControllerAdvice
public class GlobalModelAdvice {

  @ModelAttribute("isAdmin")
  public boolean isAdmin(HttpSession s) {
    return "ADMIN".equals(s.getAttribute("rol"));
  }

  @ModelAttribute("nombreUsuario")
  public String nombreUsuario(HttpSession s) {
    Object nombre = s.getAttribute("nombre");
    if (nombre instanceof String n && !n.isBlank()) return n;
    return "Constructor";
  }
}
