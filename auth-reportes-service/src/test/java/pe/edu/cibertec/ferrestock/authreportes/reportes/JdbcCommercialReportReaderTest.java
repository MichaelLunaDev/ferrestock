package pe.edu.cibertec.ferrestock.authreportes.reportes;

import java.sql.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import pe.edu.cibertec.ferrestock.authreportes.reportes.ReportModels.Period;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Dobles JDBC: comprueban sentencias y mapeo; NO ejecutan un motor SQL. */
class JdbcCommercialReportReaderTest {
    DataSource source=mock(DataSource.class);
    Connection connection=mock(Connection.class);
    Statement identity=mock(Statement.class);
    PreparedStatement statement=mock(PreparedStatement.class);
    ResultSet principal=mock(ResultSet.class),rows=mock(ResultSet.class);
    CandidateCommercialSql sql=new CandidateCommercialSql("fixture");
    JdbcCommercialReportReader reader=new JdbcCommercialReportReader(source,sql);
    Period period=new Period(LocalDate.of(2026,10,1),LocalDate.of(2026,10,19));
    @BeforeEach void setup()throws Exception {
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(identity);
        when(identity.executeQuery(anyString())).thenReturn(principal);
        when(principal.next()).thenReturn(true,false);
        when(principal.getString(1)).thenReturn("ferrestock_ventas");
        when(principal.getString(2)).thenReturn("ferrestock_report_reader");
        when(principal.getString(3)).thenReturn("ferrestock_report_reader");
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
    }
    @Test void constructionOpensNothing(){verifyNoInteractions(source);}
    @Test void salesBindsWholePeriodAndCompletedPolicyAndClosesResources()throws Exception {
        when(rows.next()).thenReturn(true,false);
        when(rows.getLong("id")).thenReturn(5L);
        when(rows.getTimestamp("fecha")).thenReturn(Timestamp.valueOf(period.start()));
        when(rows.getString("vendedor")).thenReturn("Vendedor de prueba");
        when(rows.getString("estado")).thenReturn("COMPLETADA");
        when(rows.getBigDecimal("total")).thenReturn(new BigDecimal("123.40"));
        assertThat(reader.sales(period,5001)).singleElement().satisfies(s -> {
            assertThat(s.total()).isEqualTo(new BigDecimal("123.40"));assertThat(s.fecha()).isEqualTo(period.start());
        });
        verify(statement).setInt(1,5001);verify(statement).setTimestamp(2,Timestamp.valueOf(period.start()));
        verify(statement).setTimestamp(3,Timestamp.valueOf(period.exclusiveEnd()));verify(statement).setString(4,"COMPLETADA");
        verify(rows).close();verify(statement).close();verify(principal).close();verify(identity).close();verify(connection).close();
    }
    @Test void rankingUsesHistoricalSubtotalsAndBigintBeforeSum()throws Exception {
        reader.topProducts(period,10);
        var capture=ArgumentCaptor.forClass(String.class);verify(connection).prepareStatement(capture.capture());
        assertThat(capture.getValue()).contains("SUM(CONVERT(bigint,d.cantidad))","SUM(d.subtotal)","GROUP BY d.producto_id",
            "ORDER BY a.unidades DESC,a.importe DESC,p.id ASC").doesNotContain("p.precio","clientes","NOLOCK");
        verify(statement).setInt(1,10);
    }
    @Test void rankingMapsExactDecimalAndLongUnits()throws Exception {
        when(rows.next()).thenReturn(true,false);when(rows.getLong("id")).thenReturn(1L);
        when(rows.getString("sku")).thenReturn("SKU");when(rows.getString("nombre")).thenReturn("Producto");
        when(rows.getLong("unidades")).thenReturn(4_294_967_294L);
        when(rows.getBigDecimal("importe")).thenReturn(new BigDecimal("999999999999999999.99"));
        assertThat(reader.topProducts(period,1)).singleElement().satisfies(r -> {
            assertThat(r.unidades()).isEqualTo(4_294_967_294L);assertThat(r.importe()).isEqualTo(new BigDecimal("999999999999999999.99"));
        });
    }
    @Test void stockIsInclusiveAndDeterministic()throws Exception {
        assertThat(reader.lowStock(5,5001)).isEmpty();verify(statement).setInt(2,5);
        verify(connection).prepareStatement(contains("WHERE p.stock<=? ORDER BY p.stock ASC,p.id ASC"));
    }
    @Test void emptySalesAreLegitimateAfterSuccessfulQuery(){assertThat(reader.sales(period,5001)).isEmpty();}
    @Test void identityMismatchBlocksBeforeCommercialSql()throws Exception {
        when(principal.getString(2)).thenReturn("sa");
        assertThatThrownBy(() -> reader.sales(period,5001)).hasMessage("SERVICIO_NO_DISPONIBLE").hasNoCause();
        verify(connection,never()).prepareStatement(anyString());verify(connection).close();
    }
    @Test void wrongDatabaseBlocks()throws Exception {
        when(principal.getString(1)).thenReturn("ferrestock_auth_dev");
        assertThatThrownBy(() -> reader.lowStock(5,5001)).hasMessage("SERVICIO_NO_DISPONIBLE");
        verify(connection,never()).prepareStatement(anyString());
    }
    @Test void wrongDatabaseUserBlocks()throws Exception {
        when(principal.getString(3)).thenReturn("dbo");
        assertThatThrownBy(() -> reader.topProducts(period,10)).hasMessage("SERVICIO_NO_DISPONIBLE");
    }
    @Test void driverFailureSanitized()throws Exception {
        when(source.getConnection()).thenThrow(new SQLException("PASSWORD_CANARY jdbc:secret"));
        assertThatThrownBy(() -> reader.sales(period,5001)).hasMessage("SERVICIO_NO_DISPONIBLE").hasNoCause();
    }
    @Test void missingContractColumnFailsClosed()throws Exception {
        when(rows.next()).thenReturn(true);when(rows.getTimestamp("fecha")).thenThrow(new SQLException("UNKNOWN_SQL_CANARY"));
        assertThatThrownBy(() -> reader.sales(period,5001)).hasMessage("SERVICIO_NO_DISPONIBLE").hasNoCause();
    }
    @ParameterizedTest @ValueSource(ints={0,5002,-1}) void invalidLimitNeverConnects(int limit){
        assertThatThrownBy(() -> reader.sales(period,limit)).hasMessage("LIMITE_INVALIDO");verifyNoInteractions(source);
    }
    @ParameterizedTest @ValueSource(strings={"dbo]; DROP TABLE x--","a.b","","a b"}) void identifiersCannotInject(String schema){
        assertThatIllegalArgumentException().isThrownBy(() -> new CandidateCommercialSql(schema));
    }
    @Test void exact5001RowsDeliveredToServiceRatherThanSilentlyDroppingSentinel()throws Exception {
        when(rows.next()).thenAnswer(new org.mockito.stubbing.Answer<Boolean>(){int n;public Boolean answer(org.mockito.invocation.InvocationOnMock call){return n++<5001;}});
        when(rows.getString("sku")).thenReturn("S");when(rows.getString("nombre")).thenReturn("N");
        assertThat(reader.lowStock(5,5001)).hasSize(5001);
    }
    @Test void datasourceIgnoringTopFailsRatherThanTruncates()throws Exception {
        when(rows.next()).thenReturn(true);when(rows.getString("sku")).thenReturn("S");when(rows.getString("nombre")).thenReturn("N");
        assertThatThrownBy(() -> reader.lowStock(5,1)).hasMessage("SERVICIO_NO_DISPONIBLE");
    }
}
