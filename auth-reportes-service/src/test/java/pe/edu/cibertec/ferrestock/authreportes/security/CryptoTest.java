package pe.edu.cibertec.ferrestock.authreportes.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.cibertec.ferrestock.authreportes.entity.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import static org.assertj.core.api.Assertions.*;

class CryptoTest {
    @Test void loginUsesRootInsteadOfDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(LoginNormalizer.normalize("  INVITADO._-12 ")).isEqualTo("invitado._-12");
        } finally { Locale.setDefault(previous); }
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings={"ab", "a b", "niño", "İNVITADO", "Kelvin", "a/b", "a;b", "aa\nzz"})
    void rejectsNonAsciiOrMalformedLogin(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> LoginNormalizer.normalize(value));
    }

    @Test void loginLengthBounds() {
        assertThat(LoginNormalizer.normalize("a".repeat(64))).hasSize(64);
        assertThatIllegalArgumentException().isThrownBy(() -> LoginNormalizer.normalize("a".repeat(65)));
    }

    @Test void passwordRoundTripAndRandomSalts() {
        var encoder = new VersionedPasswordEncoder();
        String first = encoder.encode("synthetic-passphrase");
        String second = encoder.encode("synthetic-passphrase");
        assertThat(first).startsWith(VersionedPasswordEncoder.PREFIX).isNotEqualTo(second);
        assertThat(encoder.matches("synthetic-passphrase", first)).isTrue();
        assertThat(encoder.matches("wrong-passphrase", first)).isFalse();
        assertThat(first).doesNotContain("synthetic-passphrase");
    }

    @Test void passwordFormatMatchesIndependentJdkVector() throws Exception {
        // Datos ficticios y sal publica determinista; no es una credencial de FerreStock.
        byte[] salt = HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f");
        String password = "fixture-only-password";
        var spec = new PBEKeySpec(password.toCharArray(), salt, 600_000, 256);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        String encoded = VersionedPasswordEncoder.PREFIX + HexFormat.of().formatHex(salt) + HexFormat.of().formatHex(key);
        assertThat(new VersionedPasswordEncoder().matches(password, encoded)).isTrue();
        // Vector fijado externamente a la implementacion Spring (Python hashlib).
        assertThat(HexFormat.of().formatHex(key)).isEqualTo("9ca1a0ee50f0e8b38ff42f72f517e982ff201a7816a3a700329d9ab08109a606");
        spec.clearPassword();
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings={"{noop}fixture-only-password", "{bcrypt}invalid", "{pbkdf2-ferrestock-v2}abc",
            "{pbkdf2-ferrestock-v1}abc", "raw-password"})
    void rejectsUnknownOrMalformedPasswordFormat(String hash) {
        assertThat(new VersionedPasswordEncoder().matches("fixture-only-password", hash)).isFalse();
    }

    @Test void passwordPolicyAndWhitespaceAreNotSilentlyChanged() {
        var encoder = new VersionedPasswordEncoder();
        assertThatIllegalArgumentException().isThrownBy(() -> encoder.encode("short"));
        assertThatIllegalArgumentException().isThrownBy(() -> encoder.encode("x".repeat(129)));
        assertThatIllegalArgumentException().isThrownBy(() -> encoder.encode(null));
        String encoded = encoder.encode("  fixture passphrase  ");
        assertThat(encoder.matches("fixture passphrase", encoded)).isFalse();
        assertThat(encoder.matches("  fixture passphrase  ", encoded)).isTrue();
    }

    @Test void opaqueTokenVectorHashesOriginalBytesAndDefensivelyCopies() throws Exception {
        byte[] bytes = new byte[32];
        for (int i=0; i<32; i++) bytes[i]=(byte)i;
        SecureRandom fake = new SecureRandom() {
            @Override public void nextBytes(byte[] output) { System.arraycopy(bytes, 0, output, 0, 32); }
        };
        var tokens = new OpaqueTokens(fake);
        var issued = tokens.issue();
        assertThat(issued.value()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)).hasSize(43);
        assertThat(issued.hash()).isEqualTo(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertThat(tokens.hash(issued.value())).isEqualTo(issued.hash());
        byte[] altered = issued.hash(); altered[0] ^= 1;
        assertThat(issued.hash()).isNotEqualTo(altered);
        assertThat(issued.toString()).doesNotContain(issued.value(), HexFormat.of().formatHex(issued.hash()));
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings={"short", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA+", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB"})
    void rejectsMalformedOrNonCanonicalTokens(String token) {
        assertThatIllegalArgumentException().isThrownBy(() -> new OpaqueTokens().hash(token));
    }

    @Test void generatedTokensAreDistinct() {
        var tokens = new OpaqueTokens();
        assertThat(tokens.issue().value()).isNotEqualTo(tokens.issue().value());
    }

    @Test void internalModelsRedactSecretsAndSessionDoesNotStoreRawToken() throws Exception {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var user = new Usuario(1L,"user","Name","HASH_CANARY",EstadoUsuario.ACTIVO,Rol.VENDEDOR,1,now,now,null,null);
        byte[] hash = new byte[32]; hash[0]=99;
        var session = new Sesion(1L,1,hash,1,now,now.plusSeconds(60),null,null);
        hash[0]=0;
        assertThat(session.tokenHash()[0]).isEqualTo((byte)99);
        byte[] returned = session.tokenHash(); returned[0]=0;
        assertThat(session.tokenHash()[0]).isEqualTo((byte)99);
        assertThat(user.toString()).doesNotContain("HASH_CANARY");
        assertThat(session.toString()).doesNotContain(HexFormat.of().formatHex(session.tokenHash()));
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
        assertThat(mapper.writeValueAsString(user)).doesNotContain("HASH_CANARY","passwordHash");
        assertThat(mapper.writeValueAsString(session)).doesNotContain("tokenHash");
        assertThat(Sesion.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("token", "rawToken", "value");
    }
}
