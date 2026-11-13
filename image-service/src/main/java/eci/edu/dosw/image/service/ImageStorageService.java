package eci.edu.dosw.image.service;

import eci.edu.dosw.image.dto.ImageResponseDTO;
import eci.edu.dosw.image.exception.ImageAccessDeniedException;
import eci.edu.dosw.image.exception.ImageNotFoundException;
import eci.edu.dosw.image.exception.ImageQuotaExceededException;
import eci.edu.dosw.image.exception.InvalidImageException;
import eci.edu.dosw.image.model.ImageDocument;
import eci.edu.dosw.image.model.ImageType;
import eci.edu.dosw.image.repository.ImageRepository;
import eci.edu.dosw.image.validator.ImageValidator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ImageStorageService {

    private final ImageRepository repository;
    private final ImageValidator validator;

    public ImageStorageService(ImageRepository repository, ImageValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    /**
     * Guarda una imagen.
     * @param requesterId usuario autenticado que sube la imagen (sub del JWT)
     * @param isAdmin     si el usuario tiene rol ADMIN
     */
    public ImageResponseDTO saveImage(UUID rootId, ImageType tipo, MultipartFile file, String referenciaExterna,
                                      UUID requesterId, boolean isAdmin) throws IOException {
        validarParametros(rootId, tipo);
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("El archivo de imagen no puede estar vacio");
        }
        verificarPermisoDeSubida(rootId, tipo, requesterId, isAdmin);

        byte[] bytes = file.getBytes();
        String contentType;
        try {
            contentType = validator.validateAndDetectContentType(bytes);
        } catch (IllegalArgumentException ex) {
            throw new InvalidImageException(ex.getMessage());
        }

        if (tipo.replacesPrevious()) {
            // Perfil: solo 1 activa por usuario. La nueva reemplaza a la anterior.
            repository.findFirstByRootIdAndTipoAndActivoTrueOrderByFechaCargaDesc(rootId, tipo)
                    .ifPresent(prev -> {
                        prev.setActivo(false);
                        repository.save(prev);
                    });
        } else if (repository.countByRootIdAndTipoAndActivoTrue(rootId, tipo) >= tipo.getMaxActive()) {
            throw new ImageQuotaExceededException(
                    "Se ha alcanzado el maximo de " + tipo.getMaxActive() + " imagenes de tipo " + tipo);
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.bin";
        ImageDocument doc = new ImageDocument(
                UUID.randomUUID(),
                rootId,
                tipo,
                fileName,
                contentType,
                file.getSize(),
                bytes,
                LocalDateTime.now(),
                referenciaExterna
        );
        doc.setSubidoPor(requesterId);
        return toDTO(repository.save(doc));
    }

    public List<ImageResponseDTO> saveBatchImages(UUID rootId, ImageType tipo, List<MultipartFile> files,
                                                  String referenciaExterna, UUID requesterId, boolean isAdmin)
            throws IOException {
        validarParametros(rootId, tipo);
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<MultipartFile> validFiles = files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (validFiles.isEmpty()) {
            return List.of();
        }
        verificarPermisoDeSubida(rootId, tipo, requesterId, isAdmin);

        if (tipo.replacesPrevious()) {
            if (validFiles.size() > 1) {
                throw new InvalidImageException("Solo se puede subir una imagen de tipo " + tipo);
            }
        } else {
            long currentCount = repository.countByRootIdAndTipoAndActivoTrue(rootId, tipo);
            if (currentCount + validFiles.size() > tipo.getMaxActive()) {
                throw new ImageQuotaExceededException(
                        "El total de imagenes superaria el limite permitido de " + tipo.getMaxActive()
                                + " para el tipo " + tipo);
            }
        }

        List<ImageResponseDTO> result = new ArrayList<>();
        for (MultipartFile f : validFiles) {
            result.add(saveImage(rootId, tipo, f, referenciaExterna, requesterId, isAdmin));
        }
        return result;
    }

    public ImageDocument getImageById(UUID id) {
        return repository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ImageNotFoundException("Imagen no encontrada con id: " + id));
    }

    public List<ImageResponseDTO> getImagesByRootId(UUID rootId, ImageType tipo) {
        if (rootId == null) {
            throw new InvalidImageException("rootId no puede ser nulo");
        }
        List<ImageDocument> list = (tipo != null)
                ? repository.findByRootIdAndTipoAndActivoTrue(rootId, tipo)
                : repository.findByRootIdAndActivoTrue(rootId);
        return list.stream().map(this::toDTO).toList();
    }

    /** Borrado logico: solo quien subio la imagen (o el dueno de la foto de perfil) o un ADMIN. */
    public void deleteImage(UUID id, UUID requesterId, boolean isAdmin) {
        ImageDocument doc = getImageById(id);
        if (!isAdmin && !esDueno(doc, requesterId)) {
            throw new ImageAccessDeniedException("Solo quien subio la imagen o un ADMIN puede borrarla");
        }
        doc.setActivo(false);
        repository.save(doc);
    }

    public ImageResponseDTO toDTO(ImageDocument doc) {
        String downloadUrl = "/api/v1/images/" + doc.getId();
        return new ImageResponseDTO(
                doc.getId(),
                doc.getRootId(),
                doc.getTipo(),
                doc.getNombre(),
                doc.getTipoContenido(),
                doc.getTamano(),
                doc.getFechaCarga(),
                doc.getReferenciaExterna(),
                downloadUrl,
                doc.getSubidoPor()
        );
    }

    private static void validarParametros(UUID rootId, ImageType tipo) {
        if (rootId == null) {
            throw new InvalidImageException("rootId es obligatorio");
        }
        if (tipo == null) {
            throw new InvalidImageException("El tipo de imagen es obligatorio");
        }
    }

    /** Hay que estar autenticado; la foto de perfil solo la sube su dueno (rootId = usuario) o un ADMIN. */
    private static void verificarPermisoDeSubida(UUID rootId, ImageType tipo, UUID requesterId, boolean isAdmin) {
        if (requesterId == null) {
            throw new ImageAccessDeniedException("Se requiere un usuario autenticado para subir imagenes");
        }
        if (tipo == ImageType.PROFILE && !isAdmin && !rootId.equals(requesterId)) {
            throw new ImageAccessDeniedException("Solo puedes cambiar tu propia foto de perfil");
        }
    }

    /** Dueno = quien la subio; en fotos de perfil tambien el usuario de la foto (cubre imagenes sin subidoPor). */
    private static boolean esDueno(ImageDocument doc, UUID requesterId) {
        if (requesterId == null) {
            return false;
        }
        return requesterId.equals(doc.getSubidoPor())
                || (doc.getTipo() == ImageType.PROFILE && requesterId.equals(doc.getRootId()));
    }
}
