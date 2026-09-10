package cl.grupo5.proyectominecraft.proyectos;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProyectoServiceTest {

  private Proyecto proyecto(String id, String nombre, String descripcion, String estado, String creadorUid) {
    var p = new Proyecto();
    p.setId(id);
    p.setNombre(nombre);
    p.setDescripcion(descripcion);
    p.setEstado(estado);
    p.setCreadorUid(creadorUid);
    return p;
  }

  @Test
  void filterByNombreOrDescripcion() {
    var lista = List.of(
        proyecto("p1", "Castillo de Piedra", "Gran fortaleza medieval", "PLANIFICACION", "u1"),
        proyecto("p2", "Granja de Hierro", "Mecanismo redstone", "EN_CONSTRUCCION", "u2"),
        proyecto("p3", "Casa de Madera", "Hogar basico", "COMPLETADO", "u1")
    );

    var res = ProyectoService.filter(lista, "hierro", null, null);
    assertThat(res).extracting(Proyecto::getId).containsExactly("p2");

    var resDesc = ProyectoService.filter(lista, "fortaleza", null, null);
    assertThat(resDesc).extracting(Proyecto::getId).containsExactly("p1");
  }

  @Test
  void filterByEstado() {
    var lista = List.of(
        proyecto("p1", "Castillo", "Desc", "PLANIFICACION", "u1"),
        proyecto("p2", "Granja", "Desc", "EN_CONSTRUCCION", "u2")
    );

    var res = ProyectoService.filter(lista, null, "PLANIFICACION", null);
    assertThat(res).extracting(Proyecto::getId).containsExactly("p1");
  }

  @Test
  void filterByCreadorUid() {
    var lista = List.of(
        proyecto("p1", "Castillo", "Desc", "PLANIFICACION", "u1"),
        proyecto("p2", "Granja", "Desc", "EN_CONSTRUCCION", "u2")
    );

    var res = ProyectoService.filter(lista, null, null, "u2");
    assertThat(res).extracting(Proyecto::getId).containsExactly("p2");
  }

  @Test
  void canUserModifyAuthorizationRules() {
    var p = proyecto("p1", "Castillo", "Desc", "PLANIFICACION", "user123");

    assertThat(ProyectoService.canUserModify(p, "user123", "USER")).isTrue();
    assertThat(ProyectoService.canUserModify(p, "otherUser", "ADMIN")).isTrue();
    assertThat(ProyectoService.canUserModify(p, "otherUser", "USER")).isFalse();
    assertThat(ProyectoService.canUserModify(null, "user123", "ADMIN")).isFalse();
  }

  @Test
  void validarEstadoAcceptsValidStates() {
    ProyectoService.validarEstado("PLANIFICACION");
    ProyectoService.validarEstado("EN_CONSTRUCCION");
    ProyectoService.validarEstado("COMPLETADO");
    ProyectoService.validarEstado("CANCELADO");
  }

  @Test
  void validarEstadoRejectsInvalidState() {
    assertThatThrownBy(() -> ProyectoService.validarEstado("DESTRUIDO"))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("El estado 'DESTRUIDO' no es válido.");
  }

  @Test
  void validarItemsRequeridosAcceptsValidItems() throws Exception {
    var items = List.of(new ItemRequerido("piedra", 64));
    ProyectoService.validarItemsRequeridos(items, itemId -> "piedra".equals(itemId));
  }

  @Test
  void validarItemsRequeridosRejectsNegativeOrZeroQuantity() {
    var items = List.of(new ItemRequerido("piedra", 0));

    assertThatThrownBy(() -> ProyectoService.validarItemsRequeridos(items, itemId -> true))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("La cantidad para el ítem 'piedra' debe ser mayor a 0.");
  }

  @Test
  void validarItemsRequeridosRejectsNonExistentItemInCatalog() {
    var items = List.of(new ItemRequerido("bloque_inexistente", 10));

    assertThatThrownBy(() -> ProyectoService.validarItemsRequeridos(items, itemId -> false))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("El ítem 'bloque_inexistente' no existe en el catálogo.");
  }

  // Tests para máquina de estados

  @Test
  void transicionarEstadoSuccess() throws Exception {
    var dbMock = org.mockito.Mockito.mock(com.google.cloud.firestore.Firestore.class);
    var collMock = org.mockito.Mockito.mock(com.google.cloud.firestore.CollectionReference.class);
    var docRefMock = org.mockito.Mockito.mock(com.google.cloud.firestore.DocumentReference.class);
    var futureMock = org.mockito.Mockito.mock(com.google.api.core.ApiFuture.class);
    var snapMock = org.mockito.Mockito.mock(com.google.cloud.firestore.DocumentSnapshot.class);
    
    org.mockito.Mockito.when(dbMock.collection("proyectos")).thenReturn(collMock);
    org.mockito.Mockito.when(collMock.document("p1")).thenReturn(docRefMock);
    org.mockito.Mockito.when(docRefMock.get()).thenReturn(futureMock);
    org.mockito.Mockito.when(futureMock.get()).thenReturn(snapMock);
    org.mockito.Mockito.when(snapMock.exists()).thenReturn(true);
    
    var p = new Proyecto();
    p.setId("p1");
    p.setEstado("PLANIFICACION");
    p.setCreadorUid("uid123");
    
    org.mockito.Mockito.when(snapMock.toObject(Proyecto.class)).thenReturn(p);
    org.mockito.Mockito.when(docRefMock.set(org.mockito.ArgumentMatchers.any(Proyecto.class))).thenReturn(futureMock);

    var service = new ProyectoService(dbMock);
    var actualizado = service.transicionarEstado("p1", "EN_CONSTRUCCION", "Inicio obras", "uid123", "Steve", "USUARIO");
    
    assertThat(actualizado.getEstado()).isEqualTo("EN_CONSTRUCCION");
    assertThat(actualizado.getHistorialEstados()).hasSize(1);
    assertThat(actualizado.getHistorialEstados().get(0).getEstadoNuevo()).isEqualTo("EN_CONSTRUCCION");
    assertThat(actualizado.getHistorialEstados().get(0).getMotivo()).isEqualTo("Inicio obras");
  }

  @Test
  void transicionarEstadoInvalidTransitionThrows() throws Exception {
    var dbMock = org.mockito.Mockito.mock(com.google.cloud.firestore.Firestore.class);
    var collMock = org.mockito.Mockito.mock(com.google.cloud.firestore.CollectionReference.class);
    var docRefMock = org.mockito.Mockito.mock(com.google.cloud.firestore.DocumentReference.class);
    var futureMock = org.mockito.Mockito.mock(com.google.api.core.ApiFuture.class);
    var snapMock = org.mockito.Mockito.mock(com.google.cloud.firestore.DocumentSnapshot.class);
    
    org.mockito.Mockito.when(dbMock.collection("proyectos")).thenReturn(collMock);
    org.mockito.Mockito.when(collMock.document("p1")).thenReturn(docRefMock);
    org.mockito.Mockito.when(docRefMock.get()).thenReturn(futureMock);
    org.mockito.Mockito.when(futureMock.get()).thenReturn(snapMock);
    org.mockito.Mockito.when(snapMock.exists()).thenReturn(true);
    
    var p = new Proyecto();
    p.setId("p1");
    p.setEstado("PLANIFICACION");
    p.setCreadorUid("uid123");
    
    org.mockito.Mockito.when(snapMock.toObject(Proyecto.class)).thenReturn(p);

    var service = new ProyectoService(dbMock);
    
    assertThatThrownBy(() -> service.transicionarEstado("p1", "COMPLETADO", null, "uid123", "Steve", "USUARIO"))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("Transición inválida: de PLANIFICACION a COMPLETADO");
  }

  @Test
  void transicionarEstadoForbiddenThrows() throws Exception {
    var dbMock = org.mockito.Mockito.mock(com.google.cloud.firestore.Firestore.class);
    var collMock = org.mockito.Mockito.mock(com.google.cloud.firestore.CollectionReference.class);
    var docRefMock = org.mockito.Mockito.mock(com.google.cloud.firestore.DocumentReference.class);
    var futureMock = org.mockito.Mockito.mock(com.google.api.core.ApiFuture.class);
    var snapMock = org.mockito.Mockito.mock(com.google.cloud.firestore.DocumentSnapshot.class);
    
    org.mockito.Mockito.when(dbMock.collection("proyectos")).thenReturn(collMock);
    org.mockito.Mockito.when(collMock.document("p1")).thenReturn(docRefMock);
    org.mockito.Mockito.when(docRefMock.get()).thenReturn(futureMock);
    org.mockito.Mockito.when(futureMock.get()).thenReturn(snapMock);
    org.mockito.Mockito.when(snapMock.exists()).thenReturn(true);
    
    var p = new Proyecto();
    p.setId("p1");
    p.setEstado("PLANIFICACION");
    p.setCreadorUid("uid123");
    
    org.mockito.Mockito.when(snapMock.toObject(Proyecto.class)).thenReturn(p);

    var service = new ProyectoService(dbMock);
    
    assertThatThrownBy(() -> service.transicionarEstado("p1", "EN_CONSTRUCCION", null, "otherUid", "Alex", "USUARIO"))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("No tienes permisos para cambiar el estado de este proyecto.");
  }
}
