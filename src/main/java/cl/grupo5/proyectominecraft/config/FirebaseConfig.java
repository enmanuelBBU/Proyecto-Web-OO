package cl.grupo5.proyectominecraft.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.FileInputStream;

@Configuration
public class FirebaseConfig {
  @Value("${firebase.project-id}")
  private String projectId;
  @Value("${firebase.credentials.path:}")
  private String credentialsPath;

  @Bean
  public Firestore firestore() throws Exception {
    if (FirebaseApp.getApps().isEmpty()) {
      FirebaseOptions.Builder b = FirebaseOptions.builder().setProjectId(projectId);
      if (!credentialsPath.isBlank()) {
        b.setCredentials(GoogleCredentials.fromStream(new FileInputStream(credentialsPath)));
      } else {
        b.setCredentials(GoogleCredentials.getApplicationDefault());
      }
      FirebaseApp.initializeApp(b.build());
    }
    return FirestoreClient.getFirestore();
  }
}
