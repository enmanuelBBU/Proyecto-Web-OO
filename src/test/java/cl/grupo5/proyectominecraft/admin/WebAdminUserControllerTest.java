package cl.grupo5.proyectominecraft.admin;

import cl.grupo5.proyectominecraft.auth.AuthService;
import cl.grupo5.proyectominecraft.perfil.UserProfile;
import cl.grupo5.proyectominecraft.perfil.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebAdminUserController.class)
class WebAdminUserControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private UserProfileService profileService;

  @MockitoBean
  private AuthService authService;

  private MockHttpSession adminSession() {
    var session = new MockHttpSession();
    session.setAttribute("uid", "admin-1");
    session.setAttribute("rol", "ADMIN");
    return session;
  }

  @Test
  void listWithoutSessionRedirectsToLogin() throws Exception {
    mvc.perform(get("/admin/usuarios"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void listWithNonAdminSessionRedirectsToItems() throws Exception {
    var session = new MockHttpSession();
    session.setAttribute("uid", "user-1");
    session.setAttribute("rol", "USUARIO");

    mvc.perform(get("/admin/usuarios").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"));
  }

  @Test
  void listWithAdminSessionRendersUserList() throws Exception {
    when(profileService.list()).thenReturn(List.of());

    mvc.perform(get("/admin/usuarios").session(adminSession()))
        .andExpect(status().isOk())
        .andExpect(view().name("admin-usuarios"));
  }

  @Test
  void listExposesIsAdminTrue() throws Exception {
    when(profileService.list()).thenReturn(List.of());

    mvc.perform(get("/admin/usuarios").session(adminSession()))
        .andExpect(status().isOk())
        .andExpect(model().attribute("isAdmin", true));
  }

  @Test
  void editOwnAccountRedirectsToList() throws Exception {
    mvc.perform(get("/admin/usuarios/admin-1/edit").session(adminSession()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/usuarios"));
  }

  @Test
  void deleteOwnAccountRedirectsWithoutDeleting() throws Exception {
    mvc.perform(post("/admin/usuarios/admin-1/delete").session(adminSession()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/usuarios"));

    verify(authService, never()).deleteUser(any());
    verify(profileService, never()).delete(any());
  }

  @Test
  void updateOwnAccountRedirectsWithoutChanging() throws Exception {
    mvc.perform(post("/admin/usuarios/admin-1/update")
            .session(adminSession())
            .param("nombre", "Nuevo Nombre")
            .param("email", "nuevo@x.com")
            .param("rol", "ADMIN"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/usuarios"));

    verify(profileService, never()).save(any(), any());
  }

  @Test
  void updateWithInvalidRoleRerendersWithError() throws Exception {
    var target = new UserProfile();
    target.setUid("user-2");
    target.setNombre("Ana");
    target.setEmail("ana@x.com");
    target.setRol("USUARIO");
    when(profileService.get("user-2")).thenReturn(target);

    mvc.perform(post("/admin/usuarios/user-2/update")
            .session(adminSession())
            .param("nombre", "Ana")
            .param("email", "ana@x.com")
            .param("rol", "SUPERADMIN"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin-usuario-edit"))
        .andExpect(model().attributeExists("error"))
        .andExpect(model().attribute("toastError", "Rol inválido."));

    verify(profileService, never()).save(any(), any());
  }

  @Test
  void updateChangesTargetUser() throws Exception {
    var target = new UserProfile();
    target.setUid("user-2");
    target.setNombre("Ana");
    target.setEmail("ana@x.com");
    target.setRol("USUARIO");
    when(profileService.get("user-2")).thenReturn(target);

    mvc.perform(post("/admin/usuarios/user-2/update")
            .session(adminSession())
            .param("nombre", "Ana Nueva")
            .param("email", "ananueva@x.com")
            .param("rol", "ADMIN"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/usuarios"))
        .andExpect(flash().attribute("toastSuccess", "Usuario actualizado correctamente"));

    var captor = org.mockito.ArgumentCaptor.forClass(UserProfile.class);
    verify(profileService).save(eq("user-2"), captor.capture());
    assertThat(captor.getValue().getNombre()).isEqualTo("Ana Nueva");
    assertThat(captor.getValue().getEmail()).isEqualTo("ananueva@x.com");
    assertThat(captor.getValue().getRol()).isEqualTo("ADMIN");
  }

  @Test
  void deleteRemovesTargetAuthAndProfileInSafeOrder() throws Exception {
    mvc.perform(post("/admin/usuarios/user-2/delete").session(adminSession()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/usuarios"))
        .andExpect(flash().attribute("toastSuccess", "Usuario eliminado correctamente"));

    var order = inOrder(authService, profileService);
    order.verify(authService).deleteUser("user-2");
    order.verify(profileService).delete("user-2");
  }

  @Test
  void updateWithBlankNombreRerendersWithError() throws Exception {
    var target = new UserProfile();
    target.setUid("user-2");
    target.setNombre("Ana");
    target.setEmail("ana@x.com");
    target.setRol("USUARIO");
    when(profileService.get("user-2")).thenReturn(target);

    mvc.perform(post("/admin/usuarios/user-2/update")
            .session(adminSession())
            .param("nombre", "")
            .param("email", "ana@x.com")
            .param("rol", "USUARIO"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin-usuario-edit"))
        .andExpect(model().attributeExists("error"))
        .andExpect(model().attribute("toastError", "El nombre es obligatorio."));

    verify(profileService, never()).save(any(), any());
  }

  @Test
  void updateWithInvalidEmailRerendersWithError() throws Exception {
    var target = new UserProfile();
    target.setUid("user-2");
    target.setNombre("Ana");
    target.setEmail("ana@x.com");
    target.setRol("USUARIO");
    when(profileService.get("user-2")).thenReturn(target);

    mvc.perform(post("/admin/usuarios/user-2/update")
            .session(adminSession())
            .param("nombre", "Ana")
            .param("email", "not-an-email")
            .param("rol", "USUARIO"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin-usuario-edit"))
        .andExpect(model().attributeExists("error"))
        .andExpect(model().attribute("toastError", "El email no es válido."));

    verify(profileService, never()).save(any(), any());
  }
}
