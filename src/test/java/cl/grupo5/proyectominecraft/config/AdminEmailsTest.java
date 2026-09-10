package cl.grupo5.proyectominecraft.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AdminEmailsTest {

  @Test
  void isAdminTrueForListedEmailCaseInsensitive() {
    var emails = new AdminEmails("root@example.com, other@example.com");
    assertThat(emails.isAdmin("ROOT@example.com")).isTrue();
  }

  @Test
  void isAdminFalseForUnlistedEmail() {
    var emails = new AdminEmails("root@example.com");
    assertThat(emails.isAdmin("someone@example.com")).isFalse();
  }

  @Test
  void isAdminFalseWhenPropertyIsEmpty() {
    var emails = new AdminEmails("");
    assertThat(emails.isAdmin("root@example.com")).isFalse();
  }

  @Test
  void isAdminFalseForNullEmail() {
    var emails = new AdminEmails("root@example.com");
    assertThat(emails.isAdmin(null)).isFalse();
  }
}
