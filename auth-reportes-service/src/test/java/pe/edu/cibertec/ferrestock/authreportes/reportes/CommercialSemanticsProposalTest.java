package pe.edu.cibertec.ferrestock.authreportes.reportes;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayInputStream;
import pe.edu.cibertec.ferrestock.authreportes.reportes.ReportModels.*;
import pe.edu.cibertec.ferrestock.authreportes.reportes.ReportModels.Period;
import pe.edu.cibertec.ferrestock.authreportes.service.AutenticacionService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Oráculo en memoria de la propuesta de negocio. NO prueba de ejecución T-SQL ni de VentaService. */
class CommercialSemanticsProposalTest {
    Period p=new Period(LocalDate.of(2026,10,1),LocalDate.of(2026,10,1));
    record Detail(long sale,long product,int quantity,BigDecimal historicalSubtotal){}
    List<Sale> eligible(List<Sale> rows){return rows.stream().filter(r -> r.estado().equals("COMPLETADA")
        && !r.fecha().isBefore(p.start()) && r.fecha().isBefore(p.exclusiveEnd()))
        .sorted(Comparator.comparing(Sale::fecha).reversed().thenComparing(Comparator.comparingLong(Sale::id).reversed())).toList();}
    Sale sale(long id,LocalDateTime at,String state){return new Sale(id,at,"TEST_SELLER",new BigDecimal("12.30"),state);}
    @Test void completedCancelledAndHalfOpenBoundaries(){
        var rows=List.of(sale(1,p.start(),"COMPLETADA"),sale(2,p.start(),"ANULADA"),
            sale(3,p.exclusiveEnd(),"COMPLETADA"),sale(4,p.exclusiveEnd().minusNanos(1),"COMPLETADA"),
            sale(5,p.start().minusNanos(1),"COMPLETADA"));
        assertThat(eligible(rows)).extracting(Sale::id).containsExactly(4L,1L);
        assertThat(new CandidateCommercialSql("fixture").sales()).contains("v.fecha>=? AND v.fecha<?","v.estado COLLATE Latin1_General_100_BIN2=?","v.id DESC");
    }
    @Test void repeatedDetailsAndHistoricalAmountsAggregateWithoutCurrentPrice(){
        var sales=eligible(List.of(sale(1,p.start(),"COMPLETADA"),sale(2,p.start(),"ANULADA")));
        var ids=sales.stream().map(Sale::id).toList();
        var details=List.of(new Detail(1,10,2,new BigDecimal("3.20")),new Detail(1,10,3,new BigDecimal("4.80")),
            new Detail(2,10,100,new BigDecimal("500.00")));
        var eligible=details.stream().filter(d -> ids.contains(d.sale())).toList();
        assertThat(eligible.stream().mapToLong(Detail::quantity).sum()).isEqualTo(5);
        assertThat(eligible.stream().map(Detail::historicalSubtotal).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("8.00");
        assertThat(new CandidateCommercialSql("fixture").topProducts()).contains("SUM(d.subtotal)").doesNotContain("precio");
    }
    @Test void rankingTiesResolveByRevenueThenId(){
        var rows=List.of(new TopProduct(3,"S3","N",5,new BigDecimal("8.00")),new TopProduct(2,"S2","N",5,new BigDecimal("9.00")),new TopProduct(1,"S1","N",5,new BigDecimal("9.00")));
        var sorted=rows.stream().sorted(Comparator.comparingLong(TopProduct::unidades).reversed()
            .thenComparing(TopProduct::importe,Comparator.reverseOrder()).thenComparingLong(TopProduct::productoId)).toList();
        assertThat(sorted).extracting(TopProduct::productoId).containsExactly(1L,2L,3L);
    }
    @Test void stockBelowEqualAboveAndNegative(){
        var rows=List.of(new LowStock(1,"S1","N",4),new LowStock(2,"S2","N",5),new LowStock(3,"S3","N",6),new LowStock(4,"S4","N",-1));
        assertThat(rows.stream().filter(r -> r.stock()<=5).sorted(Comparator.comparingInt(LowStock::stock).thenComparingLong(LowStock::productoId)).toList())
            .extracting(LowStock::productoId).containsExactly(4L,1L,2L);
    }
    @Test void allThreeDtosReachExistingPdfAndXlsxWithoutAlternativeData()throws Exception {
        var service=new ReportService(new UnconfiguredCommercialReader(),mock(AutenticacionService.class));
        var sales=new SalesReport(p,List.of(sale(1,p.start(),"COMPLETADA")));
        var top=new TopReport(p,10,List.of(new TopProduct(1,"FIXTURE_SKU","TEST_PRODUCT",5,new BigDecimal("8.00"))));
        var stock=new StockReport(5,List.of(new LowStock(1,"FIXTURE_SKU","TEST_PRODUCT",5)));
        var tables=List.of(service.table(sales),service.table(top),service.table(stock));
        var exporter=new DocumentExporter();
        for(var table:tables){
            try(var pdf=Loader.loadPDF(exporter.pdf(table))){assertThat(new PDFTextStripper().getText(pdf)).contains(table.title());}
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(exporter.xlsx(table)))){
                assertThat(book.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo(table.title());
                assertThat(book.getSheetAt(0).getRow(4).getPhysicalNumberOfCells()).isEqualTo(table.headers().size());
            }
        }
    }
}
