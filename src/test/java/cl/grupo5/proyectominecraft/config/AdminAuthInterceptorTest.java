package cl.grupo5.proyectominecraft.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class AdminAuthInterceptorTest {

  private final AdminAuthInterceptor interceptor = new AdminAuthInterceptor();

  @Test
  void rejectsRequestWithNoSession() throws Exception {
    var request = mock(HttpServletRequest.class);
    var response = mock(HttpServletResponse.class);
    when(request.getSession(false)).thenReturn(null);

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isFalse();
    verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
  }

  @Test
  void rejectsSessionWithNonAdminRole() throws Exception {
    var request = mock(HttpServletRequest.class);
    var response = mock(HttpServletResponse.class);
    var session = mock(HttpSession.class);
    when(request.getSession(false)).thenReturn(session);
    when(session.getAttribute("rol")).thenReturn("USUARIO");

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isFalse();
    verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
  }

  @Test
  void allowsSessionWithAdminRole() throws Exception {
    var request = mock(HttpServletRequest.class);
    var response = mock(HttpServletResponse.class);
    var session = mock(HttpSession.class);
    when(request.getSession(false)).thenReturn(session);
    when(session.getAttribute("rol")).thenReturn("ADMIN");

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isTrue();
    verify(response, never()).sendError(anyInt());
  }
}
