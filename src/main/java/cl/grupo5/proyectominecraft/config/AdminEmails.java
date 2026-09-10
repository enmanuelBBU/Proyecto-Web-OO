package cl.grupo5.proyectominecraft.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AdminEmails {
  private final Set<String> emails;

  public AdminEmails(@Value("${admin.emails:}") String raw) {
    this.emails = Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .map(String::toLowerCase)
        .collect(Collectors.toSet());
  }

  public boolean isAdmin(String email) {
    return email != null && emails.contains(email.trim().toLowerCase());
  }
}
