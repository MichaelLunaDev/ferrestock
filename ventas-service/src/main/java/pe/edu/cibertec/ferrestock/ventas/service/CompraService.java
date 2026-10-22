package pe.edu.cibertec.ferrestock.ventas.service;

import pe.edu.cibertec.ferrestock.ventas.dto.CompraRequestDto;

public interface CompraService {
    void registrarCompra(CompraRequestDto requestDto);
}
