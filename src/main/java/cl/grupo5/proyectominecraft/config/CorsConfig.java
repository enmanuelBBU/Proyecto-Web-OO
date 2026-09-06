package cl.grupo5.proyectominecraft.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
  private final ApiAuthInterceptor apiAuthInterceptor;
  private final AdminAuthInterceptor adminAuthInterceptor;

  public CorsConfig(ApiAuthInterceptor apiAuthInterceptor, AdminAuthInterceptor adminAuthInterceptor) {
    this.apiAuthInterceptor = apiAuthInterceptor;
    this.adminAuthInterceptor = adminAuthInterceptor;
  }

  @Bean
  public WebMvcConfigurer cors() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry r) {
        r.addMapping("/api/**").allowedOrigins("http://localhost:5173").allowedMethods("GET", "POST", "PUT", "DELETE");
      }

      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiAuthInterceptor).addPathPatterns("/api/**").excludePathPatterns("/api/auth/**");
        registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/api/admin/**");
      }
    };
  }
}
