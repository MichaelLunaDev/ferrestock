package pe.edu.cibertec.ferrestock.authreportes.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Sin pool. Encapsula los setters/getters y las propiedades sensibles del delegado. */
final class StrictSqlDataSource extends AbstractDataSource {
    private final DriverManagerDataSource delegate;
    private final SqlTlsSettings tls;

    StrictSqlDataSource(String url, String username, String password, SqlTlsSettings tls) {
        this.tls = tls;
        delegate = new DriverManagerDataSource() {
            @Override protected Connection getConnectionFromDriver(Properties properties) throws SQLException {
                // Conservar la mezcla/copia de Spring sin su log DEBUG de la URL.
                return getConnectionFromDriverManager(getUrl(), properties);
            }
        };
        delegate.setUrl(url + ";loginTimeout=10");
        delegate.setUsername(username);
        delegate.setPassword(password);
        var properties = new Properties();
        properties.setProperty("trustStore", tls.pathForDriver());
        properties.setProperty("trustStorePassword", tls.passwordForDriver());
        properties.setProperty("trustStoreType", "JKS");
        delegate.setConnectionProperties(properties);
    }

    @Override public Connection getConnection() throws SQLException {
        try {
            tls.verifyFile(); // También rechazar borrado/sustitución inválida antes de cada apertura.
            return delegate.getConnection();
        } catch (SQLException | RuntimeException ex) {
            throw new SQLException("Conexion SQL rechazada", "08001");
        }
    }

    @Override public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLFeatureNotSupportedException("Identidad SQL alternativa no autorizada");
    }

    @Override public String toString() { return "StrictSqlDataSource[REDACTED]"; }
}
