package pe.edu.cibertec.ferrestock.authreportes.exception;

/** Solo codigos publicos; no conserva causas, SQL, credenciales o token. */
public final class ApiProblem extends RuntimeException {
    private final int status;
    private final String code;
    public ApiProblem(int status, String code) { super(code); this.status=status; this.code=code; }
    public int status() { return status; }
    public String code() { return code; }
    public static ApiProblem unauthorized() { return new ApiProblem(401,"CREDENCIALES_O_SESION_INVALIDAS"); }
    public static ApiProblem unavailable() { return new ApiProblem(503,"SERVICIO_NO_DISPONIBLE"); }
}
