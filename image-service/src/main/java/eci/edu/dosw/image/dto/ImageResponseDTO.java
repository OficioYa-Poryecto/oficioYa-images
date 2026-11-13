package eci.edu.dosw.image.dto;

import eci.edu.dosw.image.model.ImageType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de respuesta con metadatos de la imagen.
 * NO incluye los bytes binarios para no saturar la red en listados.
 * Para descargar el binario usar: GET /api/v1/images/{id}
 */
public record ImageResponseDTO(
        UUID id,
        UUID rootId,
        ImageType tipo,
        String nombre,
        String tipoContenido,
        Long tamano,
        LocalDateTime fechaCarga,
        String referenciaExterna,
        /** URL relativa para descargar directamente la imagen */
        String url
) {}
