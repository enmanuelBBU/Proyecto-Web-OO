package cl.grupo5.proyectominecraft.proyectos;

import cl.grupo5.proyectominecraft.auditoria.AuditService;
import cl.grupo5.proyectominecraft.inventario.CraftingCalculatorService;
import cl.grupo5.proyectominecraft.inventario.InventoryException;
import cl.grupo5.proyectominecraft.inventario.MaterialCantidad;
import cl.grupo5.proyectominecraft.inventario.NodoCrafteo;
import cl.grupo5.proyectominecraft.inventario.ResultadoFaltantes;
import cl.grupo5.proyectominecraft.items.ItemService;
import cl.grupo5.proyectominecraft.mundos.MundoService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebProyectoController.class)
class WebProyectoControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private AuditService audit;

  @MockitoBean
  private ProyectoService proyectoService;

  @MockitoBean
  private ItemService itemService;

  @MockitoBean
  private MundoService mundoService;

  @MockitoBean
  private CraftingCalculatorService calculator;

  @MockitoBean
  private ComentarioService comentarioService;

  private MockHttpSession userSession(String uid) {
    var session = new MockHttpSession();
    session.setAttribute("uid", uid);
    session.setAttribute("nombre", "Steve");
    session.setAttribute("rol", "USER");
    return session;
  }

  @Test
  void proyectosWithoutSessionRedirectsToLogin() throws Exception {
    mvc.perform(get("/proyectos"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void listProyectosExposesModelAttributes() throws Exception {
    var session = userSession("user123");
    when(proyectoService.list(eq(null), eq(null), eq(null), any(), any())).thenReturn(List.of());
    when(itemService.list(null, null)).thenReturn(List.of());

    mvc.perform(get("/proyectos").session(session))
        .andExpect(status().isOk())
        .andExpect(view().name("proyectos"))
        .andExpect(model().attributeExists("proyectos"))
        .andExpect(model().attributeExists("catalogItems"))
        .andExpect(model().attribute("currentUid", "user123"));
  }

  @Test
  void listProyectosWithMisProyectosFilter() throws Exception {
    var session = userSession("user123");
    when(proyectoService.list(eq(null), eq(null), eq("user123"), any(), any())).thenReturn(List.of());
    when(itemService.list(null, null)).thenReturn(List.of());

    mvc.perform(get("/proyectos").session(session).param("misProyectos", "true"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("misProyectos", true));
  }

  @Test
  void createProyectoRedirectsOnSuccess() throws Exception {
    var session = userSession("user123");

    mvc.perform(post("/proyectos").session(session)
            .param("nombre", "Granja de Hierro")
            .param("descripcion", "Granja automatica")
            .param("estado", "PLANIFICACION"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/proyectos"))
        .andExpect(flash().attribute("toastSuccess", "Proyecto creado correctamente"));
  }

  @Test
  void createProyectoRerendersWithErrorWhenValidationFails() throws Exception {
    var session = userSession("user123");
    doThrow(new ProyectoValidationException("El nombre del proyecto es obligatorio."))
        .when(proyectoService).create(any(), any(), any());

    mvc.perform(post("/proyectos").session(session).param("nombre", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("proyectos"))
        .andExpect(model().attributeExists("error"))
        .andExpect(model().attribute("toastError", "El nombre del proyecto es obligatorio."));
  }

  @Test
  void detailWithMissingProyectoRedirectsToProyectos() throws Exception {
    var session = userSession("user123");
    when(proyectoService.getVisible(eq("missing-id"), any(), any())).thenReturn(null);

    mvc.perform(get("/proyectos/missing-id").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/proyectos"));
  }

  @Test
  void detailExposesProyectoAndCanEdit() throws Exception {
    var session = userSession("user123");
    var p = new Proyecto();
    p.setId("castillo");
    p.setNombre("Castillo");
    p.setCreadorUid("user123");
    when(proyectoService.getVisible(eq("castillo"), any(), any())).thenReturn(p);
    when(itemService.list(null, null)).thenReturn(List.of());

    mvc.perform(get("/proyectos/castillo").session(session))
        .andExpect(status().isOk())
        .andExpect(view().name("proyecto-detalle"))
        .andExpect(model().attribute("canEdit", true));
  }

  @Test
  void editFormForbiddenForNonOwnerRedirectsToDetail() throws Exception {
    var session = userSession("otherUser");
    var p = new Proyecto();
    p.setId("castillo");
    p.setNombre("Castillo");
    p.setCreadorUid("user123");
    when(proyectoService.getVisible(eq("castillo"), any(), any())).thenReturn(p);

    mvc.perform(get("/proyectos/castillo/edit").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/proyectos/castillo"));
  }

  @Test
  void updateForbiddenRerendersEditWithError() throws Exception {
    var session = userSession("user123");
    var p = new Proyecto();
    p.setId("castillo");
    p.setNombre("Castillo");
    p.setCreadorUid("user123");
    when(proyectoService.getVisible(eq("castillo"), any(), any())).thenReturn(p);
    doThrow(new ProyectoValidationException("El estado 'INVALID' no es válido."))
        .when(proyectoService).update(eq("castillo"), any(), any(), any());

    mvc.perform(post("/proyectos/castillo/update").session(session)
            .param("nombre", "Castillo")
            .param("estado", "INVALID"))
        .andExpect(status().isOk())
        .andExpect(view().name("proyecto-edit"))
        .andExpect(model().attributeExists("error"))
        .andExpect(model().attribute("toastError", "El estado 'INVALID' no es válido."));
  }

  @Test
  void updateRedirectsWithSuccessToast() throws Exception {
    var session = userSession("user123");
    var p = new Proyecto();
    p.setId("castillo");
    p.setNombre("Castillo");
    p.setCreadorUid("user123");
    when(proyectoService.getVisible(eq("castillo"), any(), any())).thenReturn(p);

    mvc.perform(post("/proyectos/castillo/update").session(session)
            .param("nombre", "Castillo Nuevo")
            .param("estado", "EN_CONSTRUCCION"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/proyectos/castillo"))
        .andExpect(flash().attribute("toastSuccess", "Proyecto actualizado correctamente"));
  }

  @Test
  void deleteRedirectsToProyectos() throws Exception {
    var session = userSession("user123");
    when(proyectoService.delete("castillo", "user123", "USER")).thenReturn(true);

    mvc.perform(post("/proyectos/castillo/delete").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/proyectos"))
        .andExpect(flash().attribute("toastSuccess", "Proyecto eliminado correctamente"));
  }

  @Test
  void estadosExposesModelAttributes() throws Exception {
    var session = userSession("user123");
    var p = new Proyecto();
    p.setId("castillo");
    p.setEstado("PLANIFICACION");
    p.setCreadorUid("user123");
    when(proyectoService.getVisible(eq("castillo"), any(), any())).thenReturn(p);

    mvc.perform(get("/proyectos/castillo/estados").session(session))
        .andExpect(status().isOk())
        .andExpect(view().name("proyecto-estados"))
        .andExpect(model().attributeExists("transicionesDisponibles"))
        .andExpect(model().attribute("canEdit", true));
  }

  @Test
  void transicionarRedirectsOnSuccess() throws Exception {
    var session = userSession("user123");
    when(proyectoService.transicionarEstado(eq("castillo"), eq("EN_CONSTRUCCION"), eq("Start"), eq("user123"), any(), any()))
        .thenReturn(new Proyecto());

    mvc.perform(post("/proyectos/castillo/transicionar").session(session)
            .param("nuevoEstado", "EN_CONSTRUCCION")
            .param("motivo", "Start"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/proyectos/castillo/estados"))
        .andExpect(flash().attribute("toastSuccess", "Estado actualizado a EN_CONSTRUCCION"));
  }

  @Test
  void transicionarRerendersWithErrorOnFailure() throws Exception {
    var session = userSession("user123");
    var p = new Proyecto();
    p.setId("castillo");
    p.setEstado("PLANIFICACION");
    when(proyectoService.getVisible(eq("castillo"), any(), any())).thenReturn(p);

    doThrow(new ProyectoValidationException("Transición inválida"))
        .when(proyectoService).transicionarEstado(any(), any(), any(), any(), any(), any());

    mvc.perform(post("/proyectos/castillo/transicionar").session(session)
            .param("nuevoEstado", "COMPLETADO"))
        .andExpect(status().isOk())
        .andExpect(view().name("proyecto-estados"))
        .andExpect(model().attributeExists("error"))
        .andExpect(model().attribute("toastError", "Transición inválida"));
  }

  @Test
  void crearProyectoEnviaMundoYCoordenadas() throws Exception {
    mvc.perform(post("/proyectos").session(userSession("user123"))
            .param("nombre", "Granja").param("mundoId", "m1")
            .param("x", "10").param("y", "64").param("z", "-5"))
        .andExpect(redirectedUrl("/proyectos"));
    var captor = ArgumentCaptor.forClass(Proyecto.class);
    verify(proyectoService).create(captor.capture(), eq("user123"), any());
    assertThat(captor.getValue().getMundoId()).isEqualTo("m1");
    assertThat(captor.getValue().getX()).isEqualTo(10);
    assertThat(captor.getValue().getY()).isEqualTo(64);
    assertThat(captor.getValue().getZ()).isEqualTo(-5);
  }

  @Test
  void listaExponeMundosDelUsuarioParaElFormulario() throws Exception {
    when(mundoService.listar("user123")).thenReturn(List.of());
    mvc.perform(get("/proyectos").session(userSession("user123")))
        .andExpect(model().attributeExists("mundos"));
  }

  @Test
  void detalleNoVisibleRedirigeConToast() throws Exception {
    mvc.perform(get("/proyectos/p1").session(userSession("user123")))
        .andExpect(redirectedUrl("/proyectos"))
        .andExpect(flash().attribute("toastError", "Proyecto no encontrado."));
  }

  @Test
  void lectorNoPuedeAbrirEdicion() throws Exception {
    var p = new Proyecto();
    p.setId("p1");
    p.setNombre("Castillo");
    p.setCreadorUid("creador");
    p.getMiembros().add(new Miembro("user123", "Steve", "s@x.cl", "LECTURA", "t"));
    when(proyectoService.getVisible(eq("p1"), eq("user123"), any())).thenReturn(p);
    mvc.perform(get("/proyectos/p1/edit").session(userSession("user123")))
        .andExpect(redirectedUrl("/proyectos/p1"));
  }

  @Test
  void detalleExponePermisosDeGestion() throws Exception {
    var p = new Proyecto();
    p.setId("p1");
    p.setNombre("Castillo");
    p.setCreadorUid("creador");
    p.getMiembros().add(new Miembro("user123", "Steve", "s@x.cl", "EDICION", "t"));
    when(proyectoService.getVisible(eq("p1"), eq("user123"), any())).thenReturn(p);
    when(itemService.list(null, null)).thenReturn(List.of());
    mvc.perform(get("/proyectos/p1").session(userSession("user123")))
        .andExpect(status().isOk())
        .andExpect(model().attribute("canEdit", true))
        .andExpect(model().attribute("canManage", false));
  }

  @Test
  void invitarMiembroRedirigeConToastExito() throws Exception {
    var p = new Proyecto();
    p.setId("p1");
    when(proyectoService.invitar("p1", "a@x.cl", "EDICION", "user123", "USER")).thenReturn(p);
    mvc.perform(post("/proyectos/p1/miembros").session(userSession("user123"))
            .param("email", "a@x.cl").param("permiso", "EDICION"))
        .andExpect(redirectedUrl("/proyectos/p1"))
        .andExpect(flash().attribute("toastSuccess", "Miembro agregado: a@x.cl"));
  }

  @Test
  void invitarMiembroConErrorRedirigeConToastError() throws Exception {
    when(proyectoService.getVisible(eq("p1"), any(), any())).thenReturn(new Proyecto());
    when(proyectoService.invitar("p1", "nadie@x.cl", "LECTURA", "user123", "USER"))
        .thenThrow(new ProyectoValidationException("No existe un usuario registrado con ese email."));
    mvc.perform(post("/proyectos/p1/miembros").session(userSession("user123"))
            .param("email", "nadie@x.cl").param("permiso", "LECTURA"))
        .andExpect(redirectedUrl("/proyectos/p1"))
        .andExpect(flash().attribute("toastError", "No existe un usuario registrado con ese email."));
  }

  @Test
  void listaMarcaProyectosCompartidosYEditables() throws Exception {
    var propio = new Proyecto();
    propio.setId("p1");
    propio.setNombre("Propio");
    propio.setCreadorUid("user123");
    var compartido = new Proyecto();
    compartido.setId("p2");
    compartido.setNombre("Compartido");
    compartido.setCreadorUid("otro");
    compartido.getMiembros().add(new Miembro("user123", "Steve", "s@x.cl", "LECTURA", "t"));
    when(proyectoService.list(eq(null), eq(null), eq(null), eq("user123"), any())).thenReturn(List.of(propio, compartido));
    when(itemService.list(null, null)).thenReturn(List.of());
    mvc.perform(get("/proyectos").session(userSession("user123")))
        .andExpect(model().attribute("editables", java.util.Set.of("p1")))
        .andExpect(model().attribute("compartidos", java.util.Map.of("p2", "LECTURA")));
  }

  @Test
  void eliminarProyectoInexistenteRedirigeConToast() throws Exception {
    mvc.perform(post("/proyectos/nada/delete").session(userSession("user123")))
        .andExpect(redirectedUrl("/proyectos"))
        .andExpect(flash().attribute("toastError", "Proyecto no encontrado."));
  }

  @Test
  void transicionRegistraDetalleDeEstado() throws Exception {
    var p = new Proyecto();
    p.setId("castillo");
    p.setNombre("Castillo");
    p.setEstado("EN_CONSTRUCCION");
    var historial = new HistorialEstado("PLANIFICACION", "EN_CONSTRUCCION", "user123", "Steve", "t", null);
    p.getHistorialEstados().add(historial);
    when(proyectoService.transicionarEstado(eq("castillo"), eq("EN_CONSTRUCCION"), any(), eq("user123"), any(), any()))
        .thenReturn(p);
    mvc.perform(post("/proyectos/castillo/transicionar").session(userSession("user123")).param("nuevoEstado", "EN_CONSTRUCCION"));
    verify(audit).registrar(any(), eq("EDITAR"), eq("PROYECTO"), eq("castillo"), eq("Castillo"),
        eq("Estado: PLANIFICACION → EN_CONSTRUCCION"));
  }

  @Test
  void invitarRegistraDetalle() throws Exception {
    var p = new Proyecto();
    p.setId("p1");
    p.setNombre("Castillo");
    when(proyectoService.invitar("p1", "a@x.cl", "EDICION", "user123", "USER")).thenReturn(p);
    mvc.perform(post("/proyectos/p1/miembros").session(userSession("user123"))
        .param("email", "a@x.cl").param("permiso", "EDICION"));
    verify(audit).registrar(any(), eq("EDITAR"), eq("PROYECTO"), eq("p1"), eq("Castillo"), eq("Invitó a a@x.cl (EDICION)"));
  }

  @Test
  void crearProyectoRegistraAuditoria() throws Exception {
    when(proyectoService.create(any(), eq("user123"), any())).thenAnswer(inv -> {
      Proyecto p = inv.getArgument(0);
      p.setId("abc123");
      return p;
    });
    mvc.perform(post("/proyectos").session(userSession("user123")).param("nombre", "Granja"));
    verify(audit).registrar(any(), eq("CREAR"), eq("PROYECTO"), eq("abc123"), eq("Granja"), isNull());
  }

  @Test
  void eliminarProyectoAjenoNoRevelaQueExiste() throws Exception {
    when(proyectoService.delete(eq("ajeno"), any(), any()))
        .thenThrow(new ProyectoValidationException("No tienes permisos para eliminar este proyecto."));
    mvc.perform(post("/proyectos/ajeno/delete").session(userSession("user123")))
        .andExpect(redirectedUrl("/proyectos"))
        .andExpect(flash().attribute("toastError", "Proyecto no encontrado."));
  }

  // ---- Faltantes ----

  private Proyecto proyectoConItems(String uid) {
    var p = new Proyecto();
    p.setId("castillo");
    p.setNombre("Castillo");
    p.setCreadorUid(uid);
    p.setItemsRequeridos(List.of(new ItemRequerido("pala", 1)));
    return p;
  }

  private static ResultadoFaltantes resultado(boolean conFaltantes) {
    var n = new NodoCrafteo();
    n.setItemId("pala");
    n.setNombre("Pala");
    n.setTipo(NodoCrafteo.CRAFTEO);
    n.setRequerido(1);
    n.setAFabricar(1);
    var r = new ResultadoFaltantes();
    r.setArbol(List.of(n));
    if (conFaltantes) r.setFaltantes(List.of(new MaterialCantidad("tronco", "Tronco", 3)));
    return r;
  }

  @Test
  void detailExponeFaltantesDelQueMira() throws Exception {
    var p = proyectoConItems("user123");
    when(proyectoService.getVisible(eq("castillo"), any(), any())).thenReturn(p);
    when(calculator.faltantesProyecto(p, "user123")).thenReturn(resultado(true));

    mvc.perform(get("/proyectos/castillo").session(userSession("user123")))
        .andExpect(status().isOk())
        .andExpect(model().attributeExists("faltantes"))
        .andExpect(content().string(containsString("Se descuenta primero lo que ya tienes en tu inventario, incluidos ítems intermedios")))
        .andExpect(content().string(containsString("Tronco")))
        .andExpect(content().string(not(containsString("Tienes todo"))));
  }

  @Test
  void detailMuestraTienesTodoSinFaltantes() throws Exception {
    var p = proyectoConItems("user123");
    when(proyectoService.getVisible(eq("castillo"), any(), any())).thenReturn(p);
    when(calculator.faltantesProyecto(p, "user123")).thenReturn(resultado(false));

    mvc.perform(get("/proyectos/castillo").session(userSession("user123")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Tienes todo")));
  }

  @Test
  void detailMuestraErrorDeFaltantesSinRomper() throws Exception {
    var p = proyectoConItems("user123");
    when(proyectoService.getVisible(eq("castillo"), any(), any())).thenReturn(p);
    when(calculator.faltantesProyecto(p, "user123"))
        .thenThrow(new InventoryException("El ítem 'pala' no existe en el catálogo."));

    mvc.perform(get("/proyectos/castillo").session(userSession("user123")))
        .andExpect(status().isOk())
        .andExpect(model().attribute("faltantesError", "El ítem 'pala' no existe en el catálogo."))
        .andExpect(content().string(containsString("no existe en el catálogo")));
  }

  @Test
  void detailSinItemsNoCalculaFaltantes() throws Exception {
    var p = new Proyecto();
    p.setId("castillo");
    p.setNombre("Castillo");
    p.setCreadorUid("user123");
    when(proyectoService.getVisible(eq("castillo"), any(), any())).thenReturn(p);

    mvc.perform(get("/proyectos/castillo").session(userSession("user123")))
        .andExpect(status().isOk())
        .andExpect(model().attributeDoesNotExist("faltantes"));
    verify(calculator, never()).faltantesProyecto(any(), any());
  }

  // ---- Comentarios ----

  private static Comentario comentario(String id, String autor, String texto, String edicion) {
    var c = new Comentario();
    c.setId(id);
    c.setAutorUid(autor);
    c.setAutorNombre(autor + "-nombre");
    c.setTexto(texto);
    c.setFechaCreacion("2026-11-25T10:00:00Z");
    c.setFechaEdicion(edicion);
    return c;
  }

  @Test
  void detailListaComentariosConPermisosPorComentario() throws Exception {
    var p = new Proyecto();
    p.setId("castillo");
    p.setNombre("Castillo");
    p.setCreadorUid("creador");
    p.setMiembros(List.of(new Miembro("lector", "Le", "l@x", ProyectoService.LECTURA, "t")));
    when(proyectoService.getVisible(eq("castillo"), any(), any())).thenReturn(p);
    when(comentarioService.listar("castillo")).thenReturn(List.of(
        comentario("c1", "lector", "Mío <b>", "2026-11-25T11:00:00Z"),
        comentario("c2", "otro", "Ajeno", null)));

    mvc.perform(get("/proyectos/castillo").session(userSession("lector")))
        .andExpect(status().isOk())
        .andExpect(model().attribute("comentariosEditables", java.util.Set.of("c1")))
        .andExpect(model().attribute("comentariosBorrables", java.util.Set.of("c1")))
        .andExpect(content().string(containsString("Mío &lt;b&gt;")))
        .andExpect(content().string(containsString("(editado)")))
        .andExpect(content().string(containsString("/proyectos/castillo/comentarios/c1/update")))
        .andExpect(content().string(not(containsString("/proyectos/castillo/comentarios/c2/delete"))))
        .andExpect(content().string(containsString("action=\"/proyectos/castillo/comentarios\"")));
  }

  @Test
  void detailCreadorPuedeBorrarComentarioAjenoPeroNoEditarlo() throws Exception {
    var p = new Proyecto();
    p.setId("castillo");
    p.setNombre("Castillo");
    p.setCreadorUid("user123");
    when(proyectoService.getVisible(eq("castillo"), any(), any())).thenReturn(p);
    when(comentarioService.listar("castillo")).thenReturn(List.of(comentario("c2", "otro", "Ajeno", null)));

    mvc.perform(get("/proyectos/castillo").session(userSession("user123")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("/proyectos/castillo/comentarios/c2/delete")))
        .andExpect(content().string(not(containsString("/proyectos/castillo/comentarios/c2/update"))));
  }

  @Test
  void detailSinComentariosMuestraEstadoVacio() throws Exception {
    var p = new Proyecto();
    p.setId("castillo");
    p.setNombre("Castillo");
    p.setCreadorUid("user123");
    when(proyectoService.getVisible(eq("castillo"), any(), any())).thenReturn(p);

    mvc.perform(get("/proyectos/castillo").session(userSession("user123")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Aún no hay comentarios")));
  }
}
