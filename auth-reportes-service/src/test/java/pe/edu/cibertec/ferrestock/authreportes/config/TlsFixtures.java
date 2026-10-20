package pe.edu.cibertec.ferrestock.authreportes.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import org.springframework.mock.env.MockEnvironment;
import static org.mockito.Mockito.*;

/** Solo almacenes sintéticos y transporte JDBC en memoria; ninguna conexión de red. */
final class TlsFixtures {
    static final String STORE_PASSWORD = "TLS_INTEGRITY_CANARY";
    static final String SQL_PASSWORD = "SQL_CREDENTIAL_CANARY";

    static Path store(Path directory) throws Exception {
        var store = KeyStore.getInstance("JKS");
        store.load(null, STORE_PASSWORD.toCharArray());
        Path path = directory.resolve("synthetic-trust.jks");
        try (var output = Files.newOutputStream(path)) { store.store(output, STORE_PASSWORD.toCharArray()); }
        return path.toRealPath();
    }

    static MockEnvironment tls(MockEnvironment environment, Path path) {
        return environment.withProperty("SQL_TLS_TRUSTSTORE_PATH", path.toString())
                .withProperty("SQL_TLS_TRUSTSTORE_PASSWORD", STORE_PASSWORD);
    }

    static MockEnvironment auth(Path store, boolean test) {
        var env = tls(new MockEnvironment(), store);
        env.setActiveProfiles(test ? "auth-integration" : "auth-db");
        String prefix = test ? "AUTH_TEST_DB_" : "AUTH_DB_";
        return env.withProperty(prefix + "URL", "jdbc:sqlserver://127.0.0.1:14333;databaseName="
                + (test ? "ferrestock_auth_test" : "ferrestock_auth_dev") + ";encrypt=true;trustServerCertificate=false")
                .withProperty(prefix + "USER", test ? "ferrestock_auth_test_app" : "ferrestock_auth_app")
                .withProperty(prefix + "PASSWORD", SQL_PASSWORD);
    }

    static MockEnvironment reports(Path store) {
        var env = tls(new MockEnvironment(), store); env.setActiveProfiles("auth-db");
        return env.withProperty("REPORTS_DB_URL", "jdbc:sqlserver://127.0.0.1:14333;databaseName=ferrestock_ventas;encrypt=true;trustServerCertificate=false")
                .withProperty("REPORTS_DB_USER", "ferrestock_report_reader").withProperty("REPORTS_DB_PASSWORD", SQL_PASSWORD);
    }

    static final class CaptureDriver implements Driver, AutoCloseable {
        private final List<Driver> original;
        final List<String> urls = new ArrayList<>();
        final List<Properties> properties = new ArrayList<>();
        boolean fail;
        boolean mutateProperties;

        CaptureDriver() throws SQLException {
            original = Collections.list(DriverManager.getDrivers());
            // Ningún driver real puede recibir las URLs durante estos ensayos.
            for (Driver driver : original) DriverManager.deregisterDriver(driver);
            DriverManager.registerDriver(this);
        }
        @Override public Connection connect(String url, Properties info) throws SQLException {
            urls.add(url); var copy = new Properties(); copy.putAll(info); properties.add(copy);
            if (fail) throw new SQLException(STORE_PASSWORD + SQL_PASSWORD + info.getProperty("trustStore"));
            if (mutateProperties) info.clear();
            var connection = mock(Connection.class); var statement = mock(Statement.class); var row = mock(ResultSet.class);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(anyString())).thenReturn(row);
            when(row.next()).thenReturn(true);
            String database = url.substring(url.indexOf("databaseName=") + 13).split(";", 2)[0];
            when(row.getString(1)).thenReturn(database);
            when(row.getString(2)).thenReturn(copy.getProperty("user"));
            when(row.getString(3)).thenReturn(copy.getProperty("user"));
            return connection;
        }
        @Override public boolean acceptsURL(String url) { return url.startsWith("jdbc:sqlserver:"); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
        @Override public int getMajorVersion() { return 1; }
        @Override public int getMinorVersion() { return 0; }
        @Override public boolean jdbcCompliant() { return false; }
        @Override public Logger getParentLogger() { return Logger.getLogger("tls-fixture"); }
        @Override public void close() throws SQLException {
            DriverManager.deregisterDriver(this);
            for (Driver driver : original) DriverManager.registerDriver(driver);
        }
        @Override public String toString() { return "CaptureDriver[REDACTED]"; }
    }
}
