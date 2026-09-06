package cl.grupo5.proyectominecraft.perfil;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserProfile {
  private String uid;
  @NotBlank
  private String nombre;
  @Email
  @NotBlank
  private String email;
  private String rol = "USUARIO";
}
