package pe.edu.cibertec.ferrestock.authreportes.config;

import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import javax.net.ssl.X509TrustManager;

/** Public certificate observation only. ALWAYS aborts trust; never authenticates to SQL. */
public final class MigrationCertificateProbe {
    public static final class RejectingTrustManager implements X509TrustManager {
        private static volatile X509Certificate[] observed;
        public RejectingTrustManager() { }
        public void checkClientTrusted(X509Certificate[] chain, String type) throws CertificateException {
            throw new CertificateException("Client trust rejected");
        }
        public void checkServerTrusted(X509Certificate[] chain, String type) throws CertificateException {
            observed = chain == null ? null : chain.clone();
            throw new CertificateException("Observation only; server trust rejected");
        }
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }
    public static void main(String[] args) {
        try { observe(args); }
        catch (Exception ex) {
            System.out.println("{\"Result\":\"BLOCKED\",\"Category\":\"CERTIFICATE_OBSERVATION_FAILED\",\"StrictTlsValidated\":false}");
            System.exit(1);
        }
    }
    private static void observe(String[] args) throws Exception {
        if (args.length != 0) { System.out.println("{\"Result\":\"BLOCKED\",\"Category\":\"ARGUMENTOS\"}"); System.exit(1); }
        RejectingTrustManager.observed = null;
        var properties = new Properties();
        properties.setProperty("trustManagerClass", RejectingTrustManager.class.getName());
        properties.setProperty("user", "certificate_observation_never_authenticate");
        properties.setProperty("password", "not-a-credential");
        try (var ignored = DriverManager.getConnection("jdbc:sqlserver://127.0.0.1:14333;databaseName=master;encrypt=true;trustServerCertificate=false;loginTimeout=5;connectRetryCount=0", properties)) {
            System.out.println("{\"Result\":\"BLOCKED\",\"Category\":\"UNEXPECTED_CONNECTION\"}"); System.exit(1);
        } catch (Exception ignored) { /* No unknown exception output. Trust rejection is mandatory. */ }
        var chain = RejectingTrustManager.observed;
        if (chain == null || chain.length == 0) {
            System.out.println("{\"Result\":\"BLOCKED\",\"Category\":\"NO_CERTIFICATE_OBSERVED\",\"StrictTlsValidated\":false}");
            System.exit(1);
        }
        var leaf = chain[0];
        var ips = new java.util.ArrayList<String>();
        var sans = leaf.getSubjectAlternativeNames();
        if (sans != null) for (var san : sans) if (Integer.valueOf(7).equals(san.get(0)) && san.get(1) instanceof String value
                && value.matches("[0-9a-fA-F:.]+")) ips.add(value);
        List<String> eku = leaf.getExtendedKeyUsage();
        System.out.println("{\"Result\":\"CERTIFICATE_OBSERVED_UNTRUSTED\",\"StrictTlsValidated\":false,\"SqlAuthenticated\":false,"
                + "\"ChainLength\":" + chain.length + ",\"LeafSha256Der\":\""
                + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(leaf.getEncoded()))
                + "\",\"NotBefore\":\"" + leaf.getNotBefore().toInstant() + "\",\"NotAfter\":\"" + leaf.getNotAfter().toInstant()
                + "\",\"HasIp127001\":" + ips.contains("127.0.0.1")
                + ",\"HasServerAuthEku\":" + (eku != null && eku.contains("1.3.6.1.5.5.7.3.1")) + "}");
    }
    private MigrationCertificateProbe() { }
}
