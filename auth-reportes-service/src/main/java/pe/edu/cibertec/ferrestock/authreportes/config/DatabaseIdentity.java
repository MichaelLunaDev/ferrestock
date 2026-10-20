package pe.edu.cibertec.ferrestock.authreportes.config;

import javax.sql.DataSource;
import java.sql.SQLException;

final class DatabaseIdentity {
    private DatabaseIdentity() { }

    static void verify(DataSource source, AuthDatabaseSettings settings) {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            try (var row = statement.executeQuery("SELECT DB_NAME(), ORIGINAL_LOGIN(), USER_NAME()")) {
                if (!row.next() || !settings.database().equals(row.getString(1))
                        || !settings.user().equals(row.getString(2)) || !settings.user().equals(row.getString(3))) {
                    throw new IllegalStateException("Identidad SQL Auth no conforme");
                }
            }
        } catch (SQLException ex) {
            // No propagar mensaje/cause del driver: puede contener parametros de conexion.
            throw new IllegalStateException("No se pudo verificar identidad SQL Auth");
        }
    }
}
