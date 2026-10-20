package pe.edu.cibertec.ferrestock.authreportes.reportes;

import java.util.List;
import pe.edu.cibertec.ferrestock.authreportes.exception.ApiProblem;
import pe.edu.cibertec.ferrestock.authreportes.security.AuthPrincipal;
import pe.edu.cibertec.ferrestock.authreportes.service.AutenticacionService;
import pe.edu.cibertec.ferrestock.authreportes.reportes.ReportModels.*;

public final class ReportService {
    public static final int MAX_ROWS=5000;
    private final CommercialReportReader reader;private final AutenticacionService auth;
    public ReportService(CommercialReportReader reader,AutenticacionService auth){this.reader=reader;this.auth=auth;}
    public SalesReport sales(AuthPrincipal p,Period period){
        auth.requireAdministrator(p);period.exclusiveEnd();
        var rows=read(() -> reader.sales(period,MAX_ROWS+1));checkSize(rows);
        return new SalesReport(period,rows);
    }
    public TopReport top(AuthPrincipal p,Period period,int top){
        if(top<1 || top>100) throw new ApiProblem(400,"TOP_INVALIDO");
        auth.requireAdministrator(p);period.exclusiveEnd();
        var rows=read(() -> reader.topProducts(period,top));
        if(rows.size()>top) throw ApiProblem.unavailable();
        return new TopReport(period,top,rows);
    }
    public StockReport stock(AuthPrincipal p,int threshold){
        if(threshold<0 || threshold>1000000) throw new ApiProblem(400,"UMBRAL_INVALIDO");
        auth.requireAdministrator(p);var rows=read(() -> reader.lowStock(threshold,MAX_ROWS+1));checkSize(rows);
        return new StockReport(threshold,rows);
    }
    private void checkSize(List<?> rows){if(rows.size()>MAX_ROWS)throw new ApiProblem(422,"REPORTE_DEMASIADO_GRANDE");}
    private <T> List<T> read(java.util.function.Supplier<List<T>> query){
        try{return List.copyOf(query.get());}catch(ApiProblem ex){throw ex;}catch(RuntimeException ex){throw ApiProblem.unavailable();}
    }
    public Table table(SalesReport r){return new Table("Ventas por periodo",r.periodo().toString(),
        List.of("Venta","Fecha","Vendedor","Total","Estado"),r.filas().stream().map(x -> List.<Object>of(x.id(),x.fecha().toString(),x.vendedor(),x.total(),x.estado())).toList());}
    public Table table(TopReport r){return new Table("Productos mas vendidos",r.periodo()+"; top="+r.top(),
        List.of("Producto","SKU","Nombre","Unidades","Importe"),r.filas().stream().map(x -> List.<Object>of(x.productoId(),x.sku(),x.nombre(),x.unidades(),x.importe())).toList());}
    public Table table(StockReport r){return new Table("Productos con stock bajo","Stock <= "+r.umbral(),
        List.of("Producto","SKU","Nombre","Stock"),r.filas().stream().map(x -> List.<Object>of(x.productoId(),x.sku(),x.nombre(),x.stock())).toList());}
}
