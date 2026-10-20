package pe.edu.cibertec.ferrestock.authreportes.controller;

import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.cibertec.ferrestock.authreportes.security.AuthPrincipal;
import pe.edu.cibertec.ferrestock.authreportes.reportes.*;
import pe.edu.cibertec.ferrestock.authreportes.reportes.ReportModels.*;

@RestController @RequestMapping("/api/reportes") @Profile({"auth-db","auth-integration"})
public class ReportController {
    private final ReportService service;private final DocumentExporter exporter;
    public ReportController(ReportService service,DocumentExporter exporter){this.service=service;this.exporter=exporter;}
    @GetMapping("/ventas") public ResponseEntity<?> sales(@AuthenticationPrincipal AuthPrincipal p,@RequestParam LocalDate desde,@RequestParam LocalDate hasta,@RequestParam(defaultValue="JSON") Format formato){
        var report=service.sales(p,new Period(desde,hasta));return output(report,formato,formato==Format.JSON?null:service.table(report),"ventas");
    }
    @GetMapping("/productos-mas-vendidos") public ResponseEntity<?> top(@AuthenticationPrincipal AuthPrincipal p,@RequestParam LocalDate desde,@RequestParam LocalDate hasta,@RequestParam(defaultValue="10") int top,@RequestParam(defaultValue="JSON") Format formato){
        var report=service.top(p,new Period(desde,hasta),top);return output(report,formato,formato==Format.JSON?null:service.table(report),"productos-mas-vendidos");
    }
    @GetMapping("/stock-bajo") public ResponseEntity<?> stock(@AuthenticationPrincipal AuthPrincipal p,@RequestParam(defaultValue="5") int umbral,@RequestParam(defaultValue="JSON") Format formato){
        var report=service.stock(p,umbral);return output(report,formato,formato==Format.JSON?null:service.table(report),"stock-bajo");
    }
    private ResponseEntity<?> output(Object report,Format format,Table table,String name){
        if(format==Format.JSON)return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(report);
        boolean pdf=format==Format.PDF;byte[] bytes=pdf?exporter.pdf(table):exporter.xlsx(table);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(pdf?"application/pdf":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name+(pdf?".pdf":".xlsx")).build().toString())
            .contentLength(bytes.length).body(bytes);
    }
}
