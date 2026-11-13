package eci.edu.dosw.image.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImageValidatorTest {

    private ImageValidator validator;

    // Bytes minimos para cada formato valido
    private static final byte[] JPEG_BYTES = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x10};
    private static final byte[] PNG_BYTES = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    private static final byte[] WEBP_BYTES = new byte[]{
            'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8'
    };

    @BeforeEach
    void setUp() {
        validator = new ImageValidator();
    }

    @Test
    void detectaJpegCorrectamente() {
        assertEquals("image/jpeg", validator.validateAndDetectContentType(JPEG_BYTES));
    }

    @Test
    void detectaPngCorrectamente() {
        assertEquals("image/png", validator.validateAndDetectContentType(PNG_BYTES));
    }

    @Test
    void detectaWebpCorrectamente() {
        assertEquals("image/webp", validator.validateAndDetectContentType(WEBP_BYTES));
    }

    @Test
    void rechazaArchivoVacioONulo() {
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndDetectContentType(null));
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndDetectContentType(new byte[0]));
    }

    @Test
    void rechazaFormatoNoSoportado() {
        byte[] randomBytes = new byte[]{0x01, 0x02, 0x03, 0x04};
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndDetectContentType(randomBytes));
    }

    @Test
    void rechazaArchivoQueSuperaLimiteDe5MB() {
        byte[] bigArray = new byte[(int) (5L * 1024 * 1024 + 10)];
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndDetectContentType(bigArray));
    }
}
