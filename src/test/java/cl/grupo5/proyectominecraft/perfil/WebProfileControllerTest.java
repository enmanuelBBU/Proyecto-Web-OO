package cl.grupo5.proyectominecraft.perfil;

import cl.grupo5.proyectominecraft.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WebProfileController.class)
class WebProfileControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private UserProfileService profileService;

  @MockitoBean
  private AuthService authService;

  private MockHttpSession authenticated(String uid) {
    var session = new MockHttpSession();
    session.setAttribute("uid", uid);
    return session;
  }

  @Test
  void eliminarDeletesProfileAndAuthUserThenInvalidatesSessionAndRedirectsToLogin() throws Exception {
    var session = authenticated("uid-1");

    mvc.perform(post("/perfil/eliminar").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));

    verify(profileService).delete("uid-1");
    verify(authService).deleteUser("uid-1");
    assertThat(session.isInvalid()).isTrue();
  }

  @Test
  void eliminarWithoutSessionRedirectsToLoginWithoutDeletingAnything() throws Exception {
    mvc.perform(post("/perfil/eliminar"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));

    verify(profileService, never()).delete(any());
    verify(authService, never()).deleteUser(any());
  }

  @Test
  void showExposesIsAdminTrueForAdminSession() throws Exception {
    when(profileService.get("admin-1")).thenReturn(null);
    var session = authenticated("admin-1");
    session.setAttribute("email", "admin@x.com");
    session.setAttribute("rol", "ADMIN");

    mvc.perform(get("/perfil").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("isAdmin", true));
  }

  @Test
  void saveRedirectsWithSuccessToast() throws Exception {
    when(profileService.get("uid-1")).thenReturn(null);
    var session = authenticated("uid-1");

    mvc.perform(post("/perfil").session(session)
            .param("nombre", "Ana")
            .param("email", "ana@x.com"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/perfil"))
        .andExpect(flash().attribute("toastSuccess", "Perfil actualizado correctamente"));
  }
}
