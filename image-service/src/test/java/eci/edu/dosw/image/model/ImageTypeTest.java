package eci.edu.dosw.image.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageTypeTest {

    @Test
    void limitesAcordadosPorDominio() {
        assertEquals(1, ImageType.PROFILE.getMaxActive());
        assertEquals(50, ImageType.WORK.getMaxActive());
        assertEquals(5, ImageType.SERVICE_EVIDENCE.getMaxActive());
        assertEquals(5, ImageType.REQUEST.getMaxActive());
        assertEquals(5, ImageType.REVIEW.getMaxActive());
        assertEquals(5, ImageType.REPORT.getMaxActive());
    }

    @Test
    void soloElPerfilReemplazaLaAnterior() {
        assertTrue(ImageType.PROFILE.replacesPrevious());
        for (ImageType tipo : ImageType.values()) {
            if (tipo != ImageType.PROFILE) {
                assertFalse(tipo.replacesPrevious(), tipo.name());
            }
        }
    }
}
