package cl.grupo5.proyectominecraft.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    var session = request.getSession(false);
    var rol = session == null ? null : session.getAttribute("rol");
    if (!"ADMIN".equals(rol)) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return false;
    }
    return true;
  }
}
