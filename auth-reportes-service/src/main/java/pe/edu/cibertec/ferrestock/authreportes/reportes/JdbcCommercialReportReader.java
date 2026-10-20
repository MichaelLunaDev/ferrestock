package pe.edu.cibertec.ferrestock.authreportes.reportes;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.edu.cibertec.ferrestock.authreportes.exception.ApiProblem;
import pe.edu.cibertec.ferrestock.authreportes.reportes.ReportModels.*;

/** Adaptador cableado en ReportConfig bajo registro de contratos revisados, vacío por defecto.
 * Antes de cablearlo: verificar esquema físico, ACL y aprobar las políticas candidatas.
 * Identidad verificada en la MISMA conexión utilizada por cada SELECT comercial. */
public final class JdbcCommercialReportReader implements CommercialReportReader {
    private final JdbcTemplate jdbc;
    private final CandidateCommercialSql sql;
    public JdbcCommercialReportReader(DataSource reportsDataSource, CandidateCommercialSql sql) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(reportsDataSource));
        this.sql = Objects.requireNonNull(sql);
    }
    public List<Sale> sales(Period period, int limit) {
        bound(limit,5001);
        return query(sql.sales(), statement -> period(statement,period,limit), row ->
            new Sale(number(row,"id"), required(row.getTimestamp("fecha")).toLocalDateTime(),
                required(row.getString("vendedor")),required(row.getBigDecimal("total")),required(row.getString("estado"))),limit);
    }
    public List<TopProduct> topProducts(Period period, int top) {
        bound(top,100);
        return query(sql.topProducts(), statement -> period(statement,period,top), row ->
            new TopProduct(number(row,"id"),required(row.getString("sku")),required(row.getString("nombre")),
                number(row,"unidades"),required(row.getBigDecimal("importe"))),top);
    }
    public List<LowStock> lowStock(int threshold, int limit) {
        bound(limit,5001);
        if(threshold<0 || threshold>1000000) throw new ApiProblem(400,"UMBRAL_INVALIDO");
        return query(sql.lowStock(), statement -> {statement.setInt(1,limit);statement.setInt(2,threshold);}, row ->
            new LowStock(number(row,"id"),required(row.getString("sku")),required(row.getString("nombre")),Math.toIntExact(number(row,"stock"))),limit);
    }
    private static void bound(int size,int max){if(size<1 || size>max)throw new ApiProblem(400,"LIMITE_INVALIDO");}
    private static <T> T required(T value){return Objects.requireNonNull(value);}
    private static long number(ResultSet row,String column)throws SQLException {
        long value=row.getLong(column);if(row.wasNull())throw ApiProblem.unavailable();return value;
    }
    private static void period(PreparedStatement statement,Period period,int limit)throws SQLException {
        statement.setInt(1,limit);
        statement.setTimestamp(2,Timestamp.valueOf(period.start()));
        statement.setTimestamp(3,Timestamp.valueOf(period.exclusiveEnd()));
        statement.setString(4,"COMPLETADA"); // Política propuesta, pendiente de aprobación de Ventas.
    }
    private <T> List<T> query(String text,Binder bind,Mapper<T> map,int limit) {
        try {
            return jdbc.execute((ConnectionCallback<List<T>>) connection -> {
                try(var identity=connection.createStatement()){
                    identity.setQueryTimeout(10);
                    try(var row=identity.executeQuery("SELECT DB_NAME(), ORIGINAL_LOGIN(), USER_NAME()")){
                        if(!row.next() || !"ferrestock_ventas".equals(row.getString(1))
                            || !"ferrestock_report_reader".equals(row.getString(2))
                            || !"ferrestock_report_reader".equals(row.getString(3)) || row.next())
                            throw ApiProblem.unavailable();
                    }
                }
                try(var statement=connection.prepareStatement(text)){
                    statement.setQueryTimeout(15);bind.apply(statement);
                    try(var rows=statement.executeQuery()){
                        var result=new ArrayList<T>();
                        while(rows.next()){
                            if(result.size()>=limit)throw ApiProblem.unavailable();
                            result.add(map.apply(rows));
                        }
                        return List.copyOf(result);
                    }
                }
            });
        }catch(RuntimeException ex){throw ApiProblem.unavailable();} // Sin causa/mensaje SQL ni datos de conexión.
    }
    @FunctionalInterface private interface Binder {void apply(PreparedStatement statement)throws SQLException;}
    @FunctionalInterface private interface Mapper<T> {T apply(ResultSet row)throws SQLException;}
}
