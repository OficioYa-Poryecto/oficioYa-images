package eci.edu.dosw.image.service;

import eci.edu.dosw.image.dto.ImageResponseDTO;
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

    private static final long MAX_REQUEST_IMAGES = 10;
    private static final long MAX_REPORT_IMAGES = 10;

    private final ImageRepository repository;
    private final ImageValidator validator;

    public ImageStorageService(ImageRepository repository, ImageValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public ImageResponseDTO saveImage(UUID rootId, ImageType tipo, MultipartFile file, String referenciaExterna) throws IOException {
        if (rootId == null) {
            throw new InvalidImageException("rootId es obligatorio");
        }
        if (tipo == null) {
            throw new InvalidImageException("El tipo de imagen es obligatorio");
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("El archivo de imagen no puede estar vacio");
        }

        byte[] bytes = file.getBytes();
        String contentType;
        try {
            contentType = validator.validateAndDetectContentType(bytes);
        } catch (IllegalArgumentException ex) {
            throw new InvalidImageException(ex.getMessage());
        }

        // Reglas de cuotas y actualizacion segun el tipo
        if (tipo == ImageType.PROFILE) {
            // Perfil: solo 1 activa por usuario. Desactivar anteriores.
            repository.findFirstByRootIdAndTipoAndActivoTrueOrderByFechaCargaDesc(rootId, ImageType.PROFILE)
                    .ifPresent(prev -> {
                        prev.setActivo(false);
                        repository.save(prev);
                    });
        } else if (tipo == ImageType.REQUEST) {
            long currentCount = repository.countByRootIdAndTipoAndActivoTrue(rootId, ImageType.REQUEST);
            if (currentCount >= MAX_REQUEST_IMAGES) {
                throw new ImageQuotaExceededException("Se ha alcanzado el maximo de " + MAX_REQUEST_IMAGES + " imagenes para esta solicitud");
            }
        } else if (tipo == ImageType.REPORT) {
            long currentCount = repository.countByRootIdAndTipoAndActivoTrue(rootId, ImageType.REPORT);
            if (currentCount >= MAX_REPORT_IMAGES) {
                throw new ImageQuotaExceededException("Se ha alcanzado el maximo de " + MAX_REPORT_IMAGES + " imagenes para este reporte");
            }
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.bin";
        UUID imageId = UUID.randomUUID();

        ImageDocument doc = new ImageDocument(
                imageId,
                rootId,
                tipo,
                fileName,
                contentType,
                file.getSize(),
                bytes,
                LocalDateTime.now(),
                referenciaExterna
        );

        ImageDocument saved = repository.save(doc);
        return toDTO(saved);
    }

    public List<ImageResponseDTO> saveBatchImages(UUID rootId, ImageType tipo, List<MultipartFile> files, String referenciaExterna) throws IOException {
        if (rootId == null) {
            throw new InvalidImageException("rootId es obligatorio");
        }
        if (tipo == null) {
            throw new InvalidImageException("El tipo de imagen es obligatorio");
        }
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<MultipartFile> validFiles = files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (validFiles.isEmpty()) {
            return List.of();
        }

        long maxAllowed = (tipo == ImageType.REQUEST) ? MAX_REQUEST_IMAGES : MAX_REPORT_IMAGES;
        long currentCount = repository.countByRootIdAndTipoAndActivoTrue(rootId, tipo);
        if (currentCount + validFiles.size() > maxAllowed) {
            throw new ImageQuotaExceededException("El total de imagenes superaria el limite permitido de " + maxAllowed);
        }

        List<ImageResponseDTO> result = new ArrayList<>();
        for (MultipartFile f : validFiles) {
            result.add(saveImage(rootId, tipo, f, referenciaExterna));
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

    public void deleteImage(UUID id, UUID requestingUserId, boolean isAdmin) {
        ImageDocument doc = getImageById(id);
        // Si no es admin y la imagen es de perfil pero no es su dueno: 404
        if (!isAdmin && doc.getTipo() == ImageType.PROFILE && requestingUserId != null && !doc.getRootId().equals(requestingUserId)) {
            throw new ImageNotFoundException("Imagen no encontrada con id: " + id);
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
                downloadUrl
        );
    }
}
