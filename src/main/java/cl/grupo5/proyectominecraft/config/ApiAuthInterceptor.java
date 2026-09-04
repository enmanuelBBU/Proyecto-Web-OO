package cl.grupo5.proyectominecraft.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiAuthInterceptor implements HandlerInterceptor {
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    var session = request.getSession(false);
    if (session == null || session.getAttribute("uid") == null) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return false;
    }
    return true;
  }
}
