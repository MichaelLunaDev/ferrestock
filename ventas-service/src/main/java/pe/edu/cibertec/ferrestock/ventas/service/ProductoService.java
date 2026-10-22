package pe.edu.cibertec.ferrestock.ventas.service;


import pe.edu.cibertec.ferrestock.ventas.dto.ProductoDto;

import java.util.List;

public interface ProductoService {

    List<ProductoDto> listarProductos();
    ProductoDto guardarProducto(ProductoDto productoDto);
    ProductoDto obtenerPorId(Long id);
}
