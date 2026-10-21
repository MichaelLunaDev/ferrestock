package pe.edu.cibertec.ferrestock.ventas.service.Impl;


import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.edu.cibertec.ferrestock.ventas.dto.CompraRequestDto;
import pe.edu.cibertec.ferrestock.ventas.dto.DetalleCompraDto;
import pe.edu.cibertec.ferrestock.ventas.entity.Compra;
import pe.edu.cibertec.ferrestock.ventas.entity.DetalleCompra;
import pe.edu.cibertec.ferrestock.ventas.entity.Producto;
import pe.edu.cibertec.ferrestock.ventas.exception.RecursoNoEncontradoException;
import pe.edu.cibertec.ferrestock.ventas.repository.CompraRepository;
import pe.edu.cibertec.ferrestock.ventas.repository.ProductoRepository;
import pe.edu.cibertec.ferrestock.ventas.service.CompraService;

import java.math.BigDecimal;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class CompraServiceImpl implements CompraService {

    private final CompraRepository compraRepository;
    private final ProductoRepository productoRepository;

    @Override
    @Transactional
    public void registrarCompra(CompraRequestDto requestDto) {
        Compra compra = new Compra();
        compra.setProveedor(requestDto.getProveedor());
        compra.setTotal(BigDecimal.ZERO);
        compra.setDetalles(new ArrayList<>());

        BigDecimal totalCompra = BigDecimal.ZERO;

        for (DetalleCompraDto item : requestDto.getDetalles()) {
            Producto producto = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Producto con ID " + item.getProductoId() + " no encontrado"));

            BigDecimal subtotal = item.getPrecioUnitario().multiply(BigDecimal.valueOf(item.getCantidad()));
            totalCompra = totalCompra.add(subtotal);

            DetalleCompra detalle = new DetalleCompra();
            detalle.setCompra(compra);
            detalle.setProducto(producto);
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(item.getPrecioUnitario());
            detalle.setSubtotal(subtotal);

            compra.getDetalles().add(detalle);

            // Aumento automático del stock por la compra realizada
            producto.setStock(producto.getStock() + item.getCantidad());
            productoRepository.save(producto);
        }

        compra.setTotal(totalCompra);
        compraRepository.save(compra);
    }
}
