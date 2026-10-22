package pe.edu.cibertec.ferrestock.ventas.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.cibertec.ferrestock.ventas.dto.CompraRequestDto;
import pe.edu.cibertec.ferrestock.ventas.service.CompraService;

@RestController
@RequestMapping("/api/compras")
@RequiredArgsConstructor
public class CompraController {

    private final CompraService compraService;

    @PostMapping
    public ResponseEntity<String> registrarCompra(@Valid @RequestBody CompraRequestDto requestDto) {
        compraService.registrarCompra(requestDto);
        return new ResponseEntity<>("Compra registrada y stock actualizado con éxito", HttpStatus.CREATED);
    }
}
