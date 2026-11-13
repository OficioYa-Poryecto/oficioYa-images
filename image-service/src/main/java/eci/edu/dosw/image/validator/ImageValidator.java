package eci.edu.dosw.image.validator;

import org.springframework.stereotype.Component;

/**
 * Valida el tipo real de un archivo de imagen inspeccionando sus bytes iniciales (magic numbers).
 * NO confia en la extension del archivo ni en el Content-Type enviado por el cliente.
 *
 * Tipos soportados: JPEG, PNG, WebP.
 * Tamano maximo: 5 MB (configurable mediante MAX_FILE_SIZE).
 */
@Component
public class ImageValidator {

    static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5 MB

    // Cuotas por tipo de imagen
    static final long MAX_REQUEST_IMAGES = 10;
    static final long MAX_REPORT_IMAGES = 10;

    /**
     * Verifica tamano y detecta el Content-Type real del archivo.
     * @return Content-Type real: "image/jpeg", "image/png" o "image/webp"
     * @throws IllegalArgumentException si el archivo esta vacio, es demasiado grande o no es una imagen valida
     */
    public String validateAndDetectContentType(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("El archivo de imagen esta vacio");
        }
        if (data.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "La imagen supera el tamano maximo permitido de 5 MB (recibido: " + data.length + " bytes)");
        }

        if (isJpeg(data)) return "image/jpeg";
        if (isPng(data))  return "image/png";
        if (isWebp(data)) return "image/webp";

        throw new IllegalArgumentException(
                "Formato no soportado. Solo se aceptan imagenes JPEG, PNG o WebP");
    }

    // JPEG: 0xFF 0xD8 0xFF
    private boolean isJpeg(byte[] b) {
        return b.length >= 3
                && (b[0] & 0xFF) == 0xFF
                && (b[1] & 0xFF) == 0xD8
                && (b[2] & 0xFF) == 0xFF;
    }

    // PNG: 0x89 P N G \r \n 0x1A \n
    private boolean isPng(byte[] b) {
        return b.length >= 8
                && (b[0] & 0xFF) == 0x89
                && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
    }

    // WebP: R I F F .... W E B P
    private boolean isWebp(byte[] b) {
        return b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
