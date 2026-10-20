package pe.edu.cibertec.ferrestock.authreportes.reportes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import pe.edu.cibertec.ferrestock.authreportes.exception.ApiProblem;

/** Contrato de lectura propuesto, pendiente de acordar vistas/semantica con Ventas. */
public final class ReportModels {
    private ReportModels() { }
    public enum Format { JSON, PDF, XLSX }
    public record Period(LocalDate desde,LocalDate hasta) {
        public Period {
            if(desde==null || hasta==null || hasta.isBefore(desde) || ChronoUnit.DAYS.between(desde,hasta)>365)
                throw new ApiProblem(400,"PERIODO_INVALIDO");
        }
        public LocalDateTime start(){return desde.atStartOfDay();}
        public LocalDateTime exclusiveEnd(){try{return hasta.plusDays(1).atStartOfDay();}catch(RuntimeException ex){throw new ApiProblem(400,"PERIODO_INVALIDO");}}
    }
    public record Sale(long id,LocalDateTime fecha,String vendedor,BigDecimal total,String estado) { }
    public record TopProduct(long productoId,String sku,String nombre,long unidades,BigDecimal importe) { }
    public record LowStock(long productoId,String sku,String nombre,int stock) { }
    public record SalesReport(Period periodo,List<Sale> filas) {public SalesReport{filas=List.copyOf(filas);}}
    public record TopReport(Period periodo,int top,List<TopProduct> filas) {public TopReport{filas=List.copyOf(filas);}}
    public record StockReport(int umbral,List<LowStock> filas) {public StockReport{filas=List.copyOf(filas);}}
    public record Table(String title,String filter,List<String> headers,List<List<Object>> rows) {
        public Table {headers=List.copyOf(headers);rows=rows.stream().map(List::copyOf).toList();}
    }
}
