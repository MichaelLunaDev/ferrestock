package pe.edu.cibertec.ferrestock.authreportes.reportes;

import java.util.List;
import pe.edu.cibertec.ferrestock.authreportes.exception.ApiProblem;
import pe.edu.cibertec.ferrestock.authreportes.reportes.ReportModels.*;

/** Falla cerrado: jamas produce datos ficticios ni una lista vacia aparente. */
public final class UnconfiguredCommercialReader implements CommercialReportReader {
    private ApiProblem pending(){return new ApiProblem(503,"CONTRATO_COMERCIAL_PENDIENTE");}
    public List<Sale> sales(Period p,int limit){throw pending();}
    public List<TopProduct> topProducts(Period p,int top){throw pending();}
    public List<LowStock> lowStock(int threshold,int limit){throw pending();}
}
