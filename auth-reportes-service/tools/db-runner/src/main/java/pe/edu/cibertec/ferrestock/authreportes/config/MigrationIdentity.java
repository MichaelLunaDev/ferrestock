package pe.edu.cibertec.ferrestock.authreportes.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import static pe.edu.cibertec.ferrestock.authreportes.config.MigrationPolicy.*;

final class MigrationIdentity {
    // Names are compared in Java (exact Unicode/length); SID equality stays binary in SQL.
    static void verify(Connection connection, Target target) throws SQLException {
        if (!connection.getAutoCommit()) throw blocked("TRANSACCION_ENTRADA");
        try (var command = connection.createStatement()) {
            command.setQueryTimeout(10);
            try (var row = command.executeQuery("""
                    SELECT DB_NAME(), ORIGINAL_LOGIN(), SUSER_SNAME(), USER_NAME(),
                      p.type, p.authentication_type, p.default_schema_name,
                      CASE WHEN p.sid = SUSER_SID() THEN 1 ELSE 0 END,
                      owner.name, owner.type, owner.authentication_type,
                      @@TRANCOUNT, XACT_STATE(), IS_SRVROLEMEMBER('sysadmin'),
                      (SELECT COUNT(*) FROM sys.database_role_members WHERE member_principal_id=USER_ID()),
                      (SELECT COUNT(*) FROM sys.schemas WHERE principal_id=USER_ID()),
                      HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL'),
                      HAS_PERMS_BY_NAME(NULL,'SERVER','CONTROL SERVER'),
                      HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY USER'),
                      HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY ROLE'),
                      HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CREATE SCHEMA')
                    FROM sys.database_principals p
                    JOIN sys.schemas s ON s.name=N'auth' COLLATE Latin1_General_100_BIN2
                      AND DATALENGTH(s.name)=8
                    JOIN sys.database_principals owner ON owner.principal_id=s.principal_id
                    WHERE p.principal_id=USER_ID()
                    """)) {
                if (!row.next()) throw blocked("IDENTIDAD");
                if (!target.database.equals(row.getString(1)) || !LOGIN.equals(row.getString(2))
                        || !LOGIN.equals(row.getString(3)) || !LOGIN.equals(row.getString(4))
                        || !"S".equals(row.getString(5)) || number(row, 6) != 1
                        || !"auth".equals(row.getString(7)) || number(row, 8) != 1
                        || !"auth_owner".equals(row.getString(9)) || !"S".equals(row.getString(10))
                        || number(row, 11) != 0) throw blocked("IDENTIDAD");
                for (int index = 12; index <= 21; index++) if (number(row, index) != 0) throw blocked("PRIVILEGIOS");
                if (row.next()) throw blocked("IDENTIDAD");
            }
            Set<String> actual = new HashSet<>();
            int count = 0;
            try (var rows = command.executeQuery("""
                    SELECT dp.class, dp.major_id, dp.minor_id, dp.permission_name, dp.state,
                      CASE WHEN dp.class=3 THEN SCHEMA_NAME(dp.major_id) ELSE N'' END
                    FROM sys.database_permissions dp WHERE dp.grantee_principal_id=USER_ID()
                    """)) {
                while (rows.next()) {
                    count++;
                    int scope = number(rows, 1);
                    if (number(rows, 3) != 0 || !"G".equals(rows.getString(5))
                            || (scope == 0 && number(rows, 2) != 0)
                            || (scope == 3 && !"auth".equals(rows.getString(6)))) throw blocked("ACL");
                    actual.add(scope + ":" + rows.getString(4));
                }
            }
            if (count != 9 || !actual.equals(Set.of("0:CONNECT", "0:CREATE TABLE", "3:ALTER",
                    "3:REFERENCES", "3:SELECT", "3:INSERT", "3:UPDATE", "3:DELETE", "3:VIEW DEFINITION"))) throw blocked("ACL");
        }
    }
    static Set<String> objects(Connection connection) throws SQLException {
        Set<String> names = new HashSet<>();
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            try (var rows = statement.executeQuery("""
                    SELECT o.name,RTRIM(o.type) FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
                    WHERE s.name=N'auth' COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.name)=8
                      AND o.is_ms_shipped=0 AND (o.parent_object_id=0 OR o.type IN ('TR','TA'))
                    """)) {
                while (rows.next()) {
                    if (!"U".equals(rows.getString(2)) || !names.add(rows.getString(1))) throw blocked("OBJETOS");
                }
            }
        }
        return names;
    }
    private static int number(ResultSet row, int column) throws SQLException {
        int value = row.getInt(column);
        if (row.wasNull()) throw blocked("METADATO_DESCONOCIDO");
        return value;
    }
    private MigrationIdentity() { }
}
