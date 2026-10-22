package pe.edu.cibertec.ferrestock.ventas.service.Impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.cibertec.ferrestock.ventas.dto.ProductoDto;
import pe.edu.cibertec.ferrestock.ventas.entity.Categoria;
import pe.edu.cibertec.ferrestock.ventas.entity.Producto;
import pe.edu.cibertec.ferrestock.ventas.exception.RecursoNoEncontradoException;
import pe.edu.cibertec.ferrestock.ventas.repository.CategoriaRepository;
import pe.edu.cibertec.ferrestock.ventas.repository.ProductoRepository;
import pe.edu.cibertec.ferrestock.ventas.service.ProductoService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductoDto> listarProductos() {
        return productoRepository.findAll().stream()
                .map(this::mapearADto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoDto obtenerPorId(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto con ID " + id + " no encontrado"));
        return mapearADto(producto);
    }

    @Override
    @Transactional
    public ProductoDto guardarProducto(ProductoDto dto) {
        Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada"));

        Producto producto = new Producto();
        producto.setNombre(dto.getNombre());
        producto.setDescripcion(dto.getDescripcion());
        producto.setPrecio(dto.getPrecio());
        producto.setStock(dto.getStock());
        producto.setStockMinimo(dto.getStockMinimo());
        producto.setCategoria(categoria);

        Producto guardado = productoRepository.save(producto);
        return mapearADto(guardado);
    }
    private ProductoDto mapearADto(Producto producto) {
        return ProductoDto.builder()
                .id(producto.getId())
                .nombre(producto.getNombre())
                .descripcion(producto.getDescripcion())
                .precio(producto.getPrecio())
                .stock(producto.getStock())
                .stockMinimo(producto.getStockMinimo())
                .categoriaId(producto.getCategoria() != null ? producto.getCategoria().getId() : null)
                .build();
    }
}
