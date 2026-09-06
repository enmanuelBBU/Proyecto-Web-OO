package cl.grupo5.proyectominecraft.auth;

import cl.grupo5.proyectominecraft.config.AdminEmails;
import cl.grupo5.proyectominecraft.perfil.UserProfile;
import cl.grupo5.proyectominecraft.perfil.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebAuthController.class)
class WebAuthControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private FirebaseIdentityService identity;

  @MockitoBean
  private UserProfileService profiles;

  @MockitoBean
  private AdminEmails adminEmails;

  @Test
  void loginRendersFormWithoutSession() throws Exception {
    mvc.perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(view().name("login"));
  }

  @Test
  void loginRedirectsToInicioWhenAlreadyAuthenticated() throws Exception {
    var session = new MockHttpSession();
    session.setAttribute("uid", "uid-1");

    mvc.perform(get("/login").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/inicio"));
  }

  @Test
  void registerRedirectsToInicioWhenAlreadyAuthenticated() throws Exception {
    var session = new MockHttpSession();
    session.setAttribute("uid", "uid-1");

    mvc.perform(get("/register").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/inicio"));
  }

  @Test
  void registerWithAdminEmailCreatesAdminProfile() throws Exception {
    when(identity.signUp("root@example.com", "secret1")).thenReturn(Map.of("localId", "uid-9"));
    when(adminEmails.isAdmin("root@example.com")).thenReturn(true);

    var session = new MockHttpSession();
    mvc.perform(post("/register").session(session).param("email", "root@example.com").param("password", "secret1"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/inicio"));

    var captor = org.mockito.ArgumentCaptor.forClass(UserProfile.class);
    verify(profiles).save(eq("uid-9"), captor.capture());
    assertThat(captor.getValue().getRol()).isEqualTo("ADMIN");
    assertThat(session.getAttribute("rol")).isEqualTo("ADMIN");
  }

  @Test
  void loginWithAdminEmailPromotesExistingProfileToAdmin() throws Exception {
    when(identity.signIn("root@example.com", "secret1")).thenReturn(Map.of("localId", "uid-9"));
    var existing = new UserProfile();
    existing.setNombre("root");
    existing.setEmail("root@example.com");
    existing.setRol("USUARIO");
    when(profiles.get("uid-9")).thenReturn(existing);
    when(adminEmails.isAdmin("root@example.com")).thenReturn(true);

    var session = new MockHttpSession();
    mvc.perform(post("/login").session(session).param("email", "root@example.com").param("password", "secret1"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/inicio"));

    var captor = org.mockito.ArgumentCaptor.forClass(UserProfile.class);
    verify(profiles).save(eq("uid-9"), captor.capture());
    assertThat(captor.getValue().getRol()).isEqualTo("ADMIN");
    assertThat(session.getAttribute("rol")).isEqualTo("ADMIN");
  }

  @Test
  void loginWithNonAdminEmailLeavesRoleUnchanged() throws Exception {
    when(identity.signIn("user@example.com", "secret1")).thenReturn(Map.of("localId", "uid-5"));
    var existing = new UserProfile();
    existing.setNombre("user");
    existing.setEmail("user@example.com");
    existing.setRol("USUARIO");
    when(profiles.get("uid-5")).thenReturn(existing);
    when(adminEmails.isAdmin("user@example.com")).thenReturn(false);

    var session = new MockHttpSession();
    mvc.perform(post("/login").session(session).param("email", "user@example.com").param("password", "secret1"))
        .andExpect(status().is3xxRedirection());

    verify(profiles, never()).save(any(), any());
    assertThat(session.getAttribute("rol")).isEqualTo("USUARIO");
  }
}
