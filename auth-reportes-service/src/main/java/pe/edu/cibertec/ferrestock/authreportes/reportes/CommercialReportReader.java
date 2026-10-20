package pe.edu.cibertec.ferrestock.authreportes.reportes;

import java.util.List;
import pe.edu.cibertec.ferrestock.authreportes.reportes.ReportModels.*;

/** Debe devolver datos completos hasta limit; pedir limit+1 para detectar exceso.
 * Adaptador SQL pendiente de contrato comercial aprobado, no existe SQL inventado. */
public interface CommercialReportReader {
    List<Sale> sales(Period period,int limit);
    List<TopProduct> topProducts(Period period,int top);
    List<LowStock> lowStock(int threshold,int limit);
}
