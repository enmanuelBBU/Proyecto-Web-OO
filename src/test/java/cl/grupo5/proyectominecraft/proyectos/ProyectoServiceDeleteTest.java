package cl.grupo5.proyectominecraft.proyectos;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProyectoServiceDeleteTest {

  @Test
  void eliminarProyectoBorraSusComentariosEnElMismoBatch() throws Exception {
    var db = mock(Firestore.class);
    var proyectos = mock(CollectionReference.class);
    var ref = mock(DocumentReference.class);
    var snap = mock(DocumentSnapshot.class);
    var comentarios = mock(CollectionReference.class);
    var query = mock(QuerySnapshot.class);
    var doc = mock(QueryDocumentSnapshot.class);
    var comentarioRef = mock(DocumentReference.class);
    var batch = mock(WriteBatch.class);
    var p = new Proyecto();
    p.setCreadorUid("u1");

    when(db.collection("proyectos")).thenReturn(proyectos);
    when(proyectos.document("p1")).thenReturn(ref);
    when(ref.get()).thenReturn(ApiFutures.immediateFuture(snap));
    when(snap.exists()).thenReturn(true);
    when(snap.toObject(Proyecto.class)).thenReturn(p);
    when(ref.collection(ComentarioService.SUBCOLECCION)).thenReturn(comentarios);
    when(comentarios.get()).thenReturn(ApiFutures.immediateFuture(query));
    when(query.getDocuments()).thenReturn(List.of(doc));
    when(doc.getReference()).thenReturn(comentarioRef);
    when(db.batch()).thenReturn(batch);
    when(batch.commit()).thenReturn(ApiFutures.immediateFuture(List.of()));

    var service = new ProyectoService(db, null, null, null);

    assertThat(service.delete("p1", "u1", "USER")).isTrue();
    verify(batch).delete(comentarioRef);
    verify(batch).delete(ref);
    verify(batch).commit();
  }
}
