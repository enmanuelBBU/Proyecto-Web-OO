package cl.grupo5.proyectominecraft.proyectos;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class ProyectoPdfService {

  public record FilaItem(String item, int cantidad, String categoria) {}

  public record ReporteProyecto(
      String titulo,
      String subtitulo,
      String fechaEmision,
      String autor,
      String estado,
      String nombreProyecto,
      String descripcion,
      String objetivo,
      List<FilaItem> filas) {}

  private static final Color GRIS_CABECERA = new Color(0x33, 0x37, 0x41);

  public byte[] generar(ReporteProyecto reporte) throws Exception {
    var baos = new ByteArrayOutputStream();
    var documento = new Document();
    PdfWriter.getInstance(documento, baos);
    documento.open();

    documento.add(titulo(reporte.titulo()));
    documento.add(subtitulo(reporte.subtitulo()));
    documento.add(new Paragraph(" "));

    if (reporte.fechaEmision() != null) documento.add(dato("Fecha de emisión", reporte.fechaEmision()));
    if (reporte.autor() != null) documento.add(dato("Autor", reporte.autor()));
    if (reporte.estado() != null) documento.add(dato("Estado", reporte.estado()));
    documento.add(new Paragraph(" "));

    documento.add(seccion("Detalle del Proyecto"));
    if (reporte.nombreProyecto() != null) documento.add(dato("Nombre", reporte.nombreProyecto()));
    if (reporte.descripcion() != null) documento.add(dato("Descripción", reporte.descripcion()));
    if (reporte.objetivo() != null) documento.add(dato("Objetivo", reporte.objetivo()));
    documento.add(new Paragraph(" "));

    documento.add(seccion("Desglose de Materiales del Proyecto"));
    documento.add(tablaItems(reporte.filas()));

    documento.close();
    return baos.toByteArray();
  }

  private static Paragraph titulo(String texto) {
    var p = new Paragraph(texto, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Font.BOLD, new Color(0x1c, 0x22, 0x30)));
    p.setAlignment(Element.ALIGN_CENTER);
    return p;
  }

  private static Paragraph subtitulo(String texto) {
    var p = new Paragraph(texto, FontFactory.getFont(FontFactory.HELVETICA, 12, Font.NORMAL, new Color(0x5b, 0x63, 0x70)));
    p.setAlignment(Element.ALIGN_CENTER);
    return p;
  }

  private static Paragraph seccion(String texto) {
    var p = new Paragraph(texto, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Font.BOLD, GRIS_CABECERA));
    p.setSpacingBefore(8);
    p.setSpacingAfter(6);
    return p;
  }

  private static Paragraph dato(String etiqueta, String valor) {
    var p = new Paragraph();
    p.add(new Phrase(etiqueta + ": ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.BOLD)));
    p.add(new Phrase(valor, FontFactory.getFont(FontFactory.HELVETICA, 11, Font.NORMAL)));
    p.setSpacingAfter(4);
    return p;
  }

  private static PdfPTable tablaItems(List<FilaItem> filas) {
    var tabla = new PdfPTable(3);
    tabla.setWidthPercentage(100);
    tabla.setSpacingBefore(4);
    tabla.setSpacingAfter(10);
    try {
      tabla.setWidths(new float[]{6, 2, 4});
    } catch (Exception ignored) {
    }

    cabecera(tabla, "Ítem");
    cabecera(tabla, "Cantidad");
    cabecera(tabla, "Categoría");

    if (filas == null || filas.isEmpty()) {
      celdaVacia(tabla, "Sin materiales registrados en este proyecto", 3);
      return tabla;
    }

    for (var fila : filas) {
      celdaTexto(tabla, fila.item());
      celdaTexto(tabla, String.valueOf(fila.cantidad()));
      celdaTexto(tabla, fila.categoria());
    }
    return tabla;
  }

  private static void cabecera(PdfPTable tabla, String texto) {
    var celda = new PdfPCell(new Phrase(texto,
        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.BOLD, Color.WHITE)));
    celda.setBackgroundColor(GRIS_CABECERA);
    celda.setPadding(6);
    celda.setHorizontalAlignment(Element.ALIGN_CENTER);
    tabla.addCell(celda);
  }

  private static void celdaTexto(PdfPTable tabla, String texto) {
    var celda = new PdfPCell(new Phrase(texto == null ? "" : texto,
        FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL)));
    celda.setPadding(5);
    celda.setBorderColor(new Color(0xcc, 0xcc, 0xcc));
    tabla.addCell(celda);
  }

  private static void celdaVacia(PdfPTable tabla, String texto, int colspan) {
    var celda = new PdfPCell(new Phrase(texto,
        FontFactory.getFont(FontFactory.HELVETICA, 10, Font.ITALIC, new Color(0x99, 0x99, 0x99))));
    celda.setColspan(colspan);
    celda.setPadding(5);
    celda.setBorderColor(new Color(0xcc, 0xcc, 0xcc));
    tabla.addCell(celda);
  }
}