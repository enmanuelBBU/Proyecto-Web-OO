package cl.grupo5.proyectominecraft.perfil;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileServiceTest {

  @Test
  void buscarPorEmailIgnoraMayusculasYEspacios() {
    var u = new UserProfile();
    u.setUid("u2");
    u.setEmail("alex@mail.com");
    assertThat(UserProfileService.buscarPorEmail(List.of(u), " Alex@Mail.com ")).isSameAs(u);
    assertThat(UserProfileService.buscarPorEmail(List.of(u), "otro@mail.com")).isNull();
    assertThat(UserProfileService.buscarPorEmail(List.of(u), null)).isNull();
  }
}
