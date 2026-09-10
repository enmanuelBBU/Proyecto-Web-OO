package cl.grupo5.proyectominecraft.proyectos;

import cl.grupo5.proyectominecraft.items.Item;
import cl.grupo5.proyectominecraft.items.ItemService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cl.grupo5.proyectominecraft.proyectos.ProyectoPdfService.FilaItem;
import static cl.grupo5.proyectominecraft.proyectos.ProyectoPdfService.ReporteProyecto;

@Controller
public class ProyectoPdfController {

  private final ProyectoPdfService pdf;
  private final ProyectoService proyectos;
  private final ItemService items;

  public ProyectoPdfController(ProyectoPdfService pdf,
                               ProyectoService proyectos,
                               ItemService items) {
    this.pdf = pdf;
    this.proyectos = proyectos;
    this.items = items;
  }

  @GetMapping("/proyectos/reportes")
  public String reportes(Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    m.addAttribute("proyectos", proyectos.list(null, null, null));
    m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
    return "reportes";
  }

  @GetMapping("/proyectos/reporte-pdf")
  public ResponseEntity<byte[]> reporte(@RequestParam(required = false) String id,
                                        HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) {
      return redirect("/login");
    }
    if (id == null || id.isBlank()) {
      return redirect("/proyectos/reportes");
    }

    var proyecto = proyectos.get(id);
    if (proyecto == null) {
      return redirect("/proyectos/reportes");
    }

    var reporte = armar(proyecto);
    var bytes = pdf.generar(reporte);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=proyecto-" + id + ".pdf")
        .body(bytes);
  }

  private static ResponseEntity<byte[]> redirect(String url) {
    return ResponseEntity.status(302).header(HttpHeaders.LOCATION, url).build();
  }

  private ReporteProyecto armar(Proyecto proyecto) throws Exception {
    Map<String, Item> itemsPorId =
        items.list(null, null).stream()
            .collect(Collectors.toMap(Item::getId, Function.identity()));

    var filas = new ArrayList<FilaItem>();
    if (proyecto.getItemsRequeridos() != null) {
      for (var requerido : proyecto.getItemsRequeridos()) {
        if (requerido.getItemId() == null || requerido.getItemId().isBlank()) continue;
        var item = itemsPorId.get(requerido.getItemId());
        filas.add(new FilaItem(
            item != null && item.getNombre() != null ? item.getNombre() : requerido.getItemId(),
            requerido.getCantidad(),
            item != null ? item.getCategoria() : null));
      }
    }

    return new ReporteProyecto(
        proyecto.getNombre() != null ? proyecto.getNombre() : "Proyecto sin nombre",
        "Reporte de Proyecto · Minecraft Manager",
        LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
        autor(proyecto),
        estadoLegible(proyecto.getEstado()),
        null,
        proyecto.getDescripcion(),
        null,
        List.copyOf(filas));
  }

  private static String autor(Proyecto proyecto) {
    if (proyecto.getCreadorNombre() != null && !proyecto.getCreadorNombre().isBlank()) {
      return proyecto.getCreadorNombre();
    }
    return proyecto.getCreadorUid();
  }

  static String estadoLegible(String estado) {
    if (estado == null) return null;
    return switch (estado.toUpperCase()) {
      case "PLANIFICACION" -> "En planificación";
      case "EN_CONSTRUCCION" -> "En construcción";
      case "COMPLETADO" -> "Completado";
      case "CANCELADO" -> "Cancelado";
      default -> estado;
    };
  }
}