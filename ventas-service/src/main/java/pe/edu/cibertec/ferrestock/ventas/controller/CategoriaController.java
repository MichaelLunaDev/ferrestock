package pe.edu.cibertec.ferrestock.ventas.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.cibertec.ferrestock.ventas.dto.CategoriaDto;
import pe.edu.cibertec.ferrestock.ventas.entity.Categoria;
import pe.edu.cibertec.ferrestock.ventas.repository.CategoriaRepository;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;

    @GetMapping
    public ResponseEntity<List<CategoriaDto>> listarCategorias() {
        List<CategoriaDto> categorias = categoriaRepository.findAll().stream()
                .map(this::aDto)
                .toList();
        return ResponseEntity.ok(categorias);
    }

    private CategoriaDto aDto(Categoria categoria) {
        return CategoriaDto.builder()
                .id(categoria.getId())
                .nombre(categoria.getNombre())
                .descripcion(categoria.getDescripcion())
                .build();
    }
}
