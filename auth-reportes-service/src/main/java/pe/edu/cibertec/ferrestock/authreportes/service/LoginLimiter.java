package pe.edu.cibertec.ferrestock.authreportes.service;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import pe.edu.cibertec.ferrestock.authreportes.exception.ApiProblem;

/** Limite conservador por nodo: 5/login y 20/origen por minuto, 4 KDF simultaneos. */
public final class LoginLimiter {
    private record Window(long minute, int attempts) { }
    private final Map<String,Window> windows=new HashMap<>();
    private final Semaphore workers=new Semaphore(4);
    private final Clock clock;
    public LoginLimiter(Clock clock) { this.clock=clock; }
    public synchronized void attempt(String login, String source) {
        long minute=clock.instant().getEpochSecond()/60;
        windows.entrySet().removeIf(e -> e.getValue().minute()!=minute);
        if(windows.size()>4094) throw limited();
        charge("u:"+digest(login),5,minute); charge("s:"+digest(source),20,minute);
    }
    private void charge(String key, int limit, long minute) {
        Window prior=windows.get(key); int attempts=prior==null?0:prior.attempts();
        if(attempts>=limit) throw limited();
        windows.put(key,new Window(minute,attempts+1));
    }
    public void acquire() { if(!workers.tryAcquire()) throw limited(); }
    public void release() { workers.release(); }
    private static ApiProblem limited() { return new ApiProblem(429,"LIMITE_LOGIN"); }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA256 no disponible"); }
    }
}
