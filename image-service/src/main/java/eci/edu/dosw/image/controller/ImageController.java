package eci.edu.dosw.image.controller;

import eci.edu.dosw.image.dto.ImageResponseDTO;
import eci.edu.dosw.image.model.ImageDocument;
import eci.edu.dosw.image.model.ImageType;
import eci.edu.dosw.image.service.ImageStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/images")
@Tag(name = "Images", description = "Microservicio para gestion y consulta de imagenes en MongoDB")
public class ImageController {

    private final ImageStorageService storageService;

    public ImageController(ImageStorageService storageService) {
        this.storageService = storageService;
    }

    @Operation(summary = "Descarga de imagen binaria por ID (acceso universal inter-servicios y web)")
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> downloadImage(@PathVariable UUID id) {
        ImageDocument image = storageService.getImageById(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getTipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + image.getNombre() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(image.getDatos());
    }

    @Operation(summary = "Consulta de metadatos de la imagen por ID (sin cargar bytes)")
    @GetMapping("/{id}/metadata")
    public ResponseEntity<ImageResponseDTO> getImageMetadata(@PathVariable UUID id) {
        ImageDocument image = storageService.getImageById(id);
        return ResponseEntity.ok(storageService.toDTO(image));
    }

    @Operation(summary = "Listar imagenes por rootId (asociadas a una solicitud, reporte o usuario)")
    @GetMapping("/by-root/{rootId}")
    public ResponseEntity<List<ImageResponseDTO>> getImagesByRoot(
            @PathVariable UUID rootId,
            @RequestParam(value = "tipo", required = false) ImageType tipo) {
        return ResponseEntity.ok(storageService.getImagesByRootId(rootId, tipo));
    }

    @Operation(summary = "Subir una imagen individual (PROFILE, REQUEST o REPORT)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageResponseDTO> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("rootId") UUID rootId,
            @RequestParam("tipo") ImageType tipo,
            @RequestParam(value = "referenciaExterna", required = false) String referenciaExterna) throws IOException {

        ImageResponseDTO response = storageService.saveImage(rootId, tipo, file, referenciaExterna);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Subir multiples imagenes en lote (0 o mas para solicitudes o reportes)")
    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ImageResponseDTO>> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("rootId") UUID rootId,
            @RequestParam("tipo") ImageType tipo,
            @RequestParam(value = "referenciaExterna", required = false) String referenciaExterna) throws IOException {

        List<ImageResponseDTO> response = storageService.saveBatchImages(rootId, tipo, files, referenciaExterna);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Borrado logico de imagen")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID id, Authentication auth) {
        UUID requestingUserId = null;
        boolean isAdmin = false;
        if (auth != null && auth.getPrincipal() instanceof UUID userId) {
            requestingUserId = userId;
            isAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }
        storageService.deleteImage(id, requestingUserId, isAdmin);
        return ResponseEntity.noContent().build();
    }
}
