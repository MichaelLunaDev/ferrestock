package pe.edu.cibertec.ferrestock.ventas.exception;

import java.util.List;

public class StockInsuficienteException extends RuntimeException {

    private final List<String> faltantes;

    public StockInsuficienteException(List<String> faltantes) {
        super("Stock insuficiente. La venta fue rechazada y no se modificó ningún producto.");
        this.faltantes = List.copyOf(faltantes);
    }

    public List<String> getFaltantes() {
        return faltantes;
    }
}
