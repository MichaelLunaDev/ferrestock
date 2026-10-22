package pe.edu.cibertec.ferrestock.ventas.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.cibertec.ferrestock.ventas.dto.ProductoDto;
import pe.edu.cibertec.ferrestock.ventas.service.ProductoService;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    public ResponseEntity<List<ProductoDto>> listarProductos() {
        return ResponseEntity.ok(productoService.listarProductos());
    }

    @PostMapping
    public ResponseEntity<ProductoDto> guardarProducto(
            @RequestBody ProductoDto productoDto) {

        ProductoDto nuevoProducto =
                productoService.guardarProducto(productoDto);

        return new ResponseEntity<>(nuevoProducto, HttpStatus.CREATED);
    }
}