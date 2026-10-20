package pe.edu.cibertec.ferrestock.authreportes.reportes;

import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.cibertec.ferrestock.authreportes.entity.Rol;
import pe.edu.cibertec.ferrestock.authreportes.exception.ApiProblem;
import pe.edu.cibertec.ferrestock.authreportes.security.AuthPrincipal;
import pe.edu.cibertec.ferrestock.authreportes.service.AutenticacionService;
import pe.edu.cibertec.ferrestock.authreportes.reportes.ReportModels.*;
import pe.edu.cibertec.ferrestock.authreportes.reportes.ReportModels.Period;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReportContractTest {
    CommercialReportReader reader=mock(CommercialReportReader.class);AutenticacionService auth=mock(AutenticacionService.class);
    ReportService service=new ReportService(reader,auth);AuthPrincipal admin=new AuthPrincipal(1,2,1,Rol.ADMINISTRADOR);
    Period period=new Period(LocalDate.of(2024,1,1),LocalDate.of(2024,12,31));
    @Test void leapYear366InclusiveDaysAndExclusiveEnd(){assertThat(period.start()).isEqualTo(LocalDateTime.of(2024,1,1,0,0));assertThat(period.exclusiveEnd()).isEqualTo(LocalDateTime.of(2025,1,1,0,0));}
    @Test void oneDayFilterIncludesWholeDay(){var p=new Period(LocalDate.of(2026,1,1),LocalDate.of(2026,1,1));assertThat(Duration.between(p.start(),p.exclusiveEnd())).isEqualTo(Duration.ofDays(1));}
    @Test void day367AndInvertedDatesRejected(){assertThatThrownBy(() -> new Period(period.desde(),period.hasta().plusDays(1))).hasMessage("PERIODO_INVALIDO");assertThatThrownBy(() -> new Period(period.hasta(),period.desde())).hasMessage("PERIODO_INVALIDO");}
    @Test void nullAndOverflowDatesRejected(){assertThatThrownBy(() -> new Period(null,period.hasta())).hasMessage("PERIODO_INVALIDO");assertThatThrownBy(() -> new Period(LocalDate.MAX,LocalDate.MAX).exclusiveEnd()).hasMessage("PERIODO_INVALIDO");}
    @ParameterizedTest @ValueSource(ints={0,101,-1}) void invalidTop(int top){assertThatThrownBy(() -> service.top(admin,period,top)).hasMessage("TOP_INVALIDO");verifyNoInteractions(reader);}
    @ParameterizedTest @ValueSource(ints={1,100}) void validTopBoundaries(int top){when(reader.topProducts(period,top)).thenReturn(List.of());assertThat(service.top(admin,period,top).top()).isEqualTo(top);verify(auth).requireAdministrator(admin);}
    @ParameterizedTest @ValueSource(ints={-1,1000001}) void invalidThreshold(int threshold){assertThatThrownBy(() -> service.stock(admin,threshold)).hasMessage("UMBRAL_INVALIDO");}
    @ParameterizedTest @ValueSource(ints={0,1000000}) void validThresholdBoundaries(int threshold){when(reader.lowStock(threshold,5001)).thenReturn(List.of());assertThat(service.stock(admin,threshold).umbral()).isEqualTo(threshold);}
    @Test void exactly5000RowsAcceptedAnd5001Rejected(){
        var row=new Sale(1,period.start(),"Synthetic",BigDecimal.ONE,"COMPLETADA");
        when(reader.sales(period,5001)).thenReturn(Collections.nCopies(5000,row));assertThat(service.sales(admin,period).filas()).hasSize(5000);
        when(reader.sales(period,5001)).thenReturn(Collections.nCopies(5001,row));assertThatThrownBy(() -> service.sales(admin,period)).hasMessage("REPORTE_DEMASIADO_GRANDE");
    }
    @Test void stockCannotBeSilentlyTruncated(){when(reader.lowStock(5,5001)).thenReturn(Collections.nCopies(5001,new LowStock(1,"S","N",0)));assertThatThrownBy(() -> service.stock(admin,5)).hasMessage("REPORTE_DEMASIADO_GRANDE");}
    @Test void oversizedRankingRejected(){when(reader.topProducts(period,1)).thenReturn(List.of(new TopProduct(1,"S","N",1,BigDecimal.ONE),new TopProduct(2,"T","M",2,BigDecimal.TEN)));assertThatThrownBy(() -> service.top(admin,period,1)).hasMessage("SERVICIO_NO_DISPONIBLE");}
    @Test void authorizationPrecedesCommercialRead(){doThrow(new ApiProblem(403,"ACCESO_DENEGADO")).when(auth).requireAdministrator(admin);assertThatThrownBy(() -> service.sales(admin,period)).hasMessage("ACCESO_DENEGADO");verifyNoInteractions(reader);}
    @Test void allUnconfiguredQueriesFailExplicitly(){var pending=new ReportService(new UnconfiguredCommercialReader(),auth);assertThatThrownBy(() -> pending.sales(admin,period)).hasMessage("CONTRATO_COMERCIAL_PENDIENTE");assertThatThrownBy(() -> pending.top(admin,period,10)).hasMessage("CONTRATO_COMERCIAL_PENDIENTE");assertThatThrownBy(() -> pending.stock(admin,5)).hasMessage("CONTRATO_COMERCIAL_PENDIENTE");}
    @Test void sqlFailureIsSanitized(){when(reader.sales(any(),anyInt())).thenThrow(new IllegalStateException("SQL_PASSWORD_CANARY"));assertThatThrownBy(() -> service.sales(admin,period)).hasMessage("SERVICIO_NO_DISPONIBLE").hasNoCause();}
    @Test void tableKeepsSameResultValuesAndPeriod(){
        var row=new Sale(9,period.start(),"Seller",new BigDecimal("12.30"),"COMPLETADA");when(reader.sales(period,5001)).thenReturn(List.of(row));
        var report=service.sales(admin,period);var table=service.table(report);
        assertThat(table.rows()).containsExactly(List.of(9L,period.start().toString(),"Seller",new BigDecimal("12.30"),"COMPLETADA"));assertThat(table.filter()).contains("2024-01-01","2024-12-31");
        assertThatThrownBy(() -> report.filas().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
