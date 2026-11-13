package eci.edu.dosw.image.service;

import eci.edu.dosw.image.dto.ImageResponseDTO;
import eci.edu.dosw.image.exception.ImageNotFoundException;
import eci.edu.dosw.image.exception.ImageQuotaExceededException;
import eci.edu.dosw.image.exception.InvalidImageException;
import eci.edu.dosw.image.model.ImageDocument;
import eci.edu.dosw.image.model.ImageType;
import eci.edu.dosw.image.repository.ImageRepository;
import eci.edu.dosw.image.validator.ImageValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImageStorageServiceTest {

    private ImageRepository repository;
    private ImageValidator validator;
    private ImageStorageService service;

    private static final byte[] VALID_BYTES = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    @BeforeEach
    void setUp() {
        repository = mock(ImageRepository.class);
        validator = mock(ImageValidator.class);
        when(validator.validateAndDetectContentType(any())).thenReturn("image/jpeg");
        when(repository.save(any(ImageDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new ImageStorageService(repository, validator);
    }

    @Test
    void guardarImagenPerfilDesactivaLaAnterior() throws IOException {
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", VALID_BYTES);

        ImageDocument previa = new ImageDocument(UUID.randomUUID(), userId, ImageType.PROFILE, "old.jpg", "image/jpeg", 100L, VALID_BYTES, LocalDateTime.now(), null);
        when(repository.findFirstByRootIdAndTipoAndActivoTrueOrderByFechaCargaDesc(userId, ImageType.PROFILE))
                .thenReturn(Optional.of(previa));

        ImageResponseDTO response = service.saveImage(userId, ImageType.PROFILE, file, null);

        assertNotNull(response);
        assertEquals(userId, response.rootId());
        assertEquals(ImageType.PROFILE, response.tipo());
        assertFalse(previa.isActivo());
        verify(repository).save(previa);
    }

    @Test
    void guardarImagenSolicitudRespetaCuota() throws IOException {
        UUID requestId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "solicitud.jpg", "image/jpeg", VALID_BYTES);

        when(repository.countByRootIdAndTipoAndActivoTrue(requestId, ImageType.REQUEST)).thenReturn(10L);

        assertThrows(ImageQuotaExceededException.class,
                () -> service.saveImage(requestId, ImageType.REQUEST, file, null));
    }

    @Test
    void guardarImagenReporteRespetaCuota() throws IOException {
        UUID reportId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "reporte.jpg", "image/jpeg", VALID_BYTES);

        when(repository.countByRootIdAndTipoAndActivoTrue(reportId, ImageType.REPORT)).thenReturn(10L);

        assertThrows(ImageQuotaExceededException.class,
                () -> service.saveImage(reportId, ImageType.REPORT, file, "REP-01"));
    }

    @Test
    void guardarBatchGuardaMultiplesImagenes() throws IOException {
        UUID requestId = UUID.randomUUID();
        MockMultipartFile f1 = new MockMultipartFile("files", "img1.jpg", "image/jpeg", VALID_BYTES);
        MockMultipartFile f2 = new MockMultipartFile("files", "img2.jpg", "image/jpeg", VALID_BYTES);

        when(repository.countByRootIdAndTipoAndActivoTrue(requestId, ImageType.REQUEST)).thenReturn(2L);

        List<ImageResponseDTO> response = service.saveBatchImages(requestId, ImageType.REQUEST, List.of(f1, f2), "REF-BATCH");

        assertEquals(2, response.size());
        verify(repository, times(2)).save(any(ImageDocument.class));
    }

    @Test
    void guardarBatchRechazaSiSuperaElTotalPermitido() {
        UUID requestId = UUID.randomUUID();
        MockMultipartFile f1 = new MockMultipartFile("files", "img1.jpg", "image/jpeg", VALID_BYTES);
        MockMultipartFile f2 = new MockMultipartFile("files", "img2.jpg", "image/jpeg", VALID_BYTES);

        when(repository.countByRootIdAndTipoAndActivoTrue(requestId, ImageType.REQUEST)).thenReturn(9L);

        assertThrows(ImageQuotaExceededException.class,
                () -> service.saveBatchImages(requestId, ImageType.REQUEST, List.of(f1, f2), null));
    }

    @Test
    void obtenerImagenPorIdRetornaDocumentoSiExiste() {
        UUID imageId = UUID.randomUUID();
        ImageDocument doc = new ImageDocument(imageId, UUID.randomUUID(), ImageType.PROFILE, "img.jpg", "image/jpeg", 50L, VALID_BYTES, LocalDateTime.now(), null);
        when(repository.findByIdAndActivoTrue(imageId)).thenReturn(Optional.of(doc));

        ImageDocument resultado = service.getImageById(imageId);
        assertEquals(imageId, resultado.getId());
    }

    @Test
    void obtenerImagenPorIdLanzaNotFoundSiNoExiste() {
        UUID imageId = UUID.randomUUID();
        when(repository.findByIdAndActivoTrue(imageId)).thenReturn(Optional.empty());

        assertThrows(ImageNotFoundException.class, () -> service.getImageById(imageId));
    }

    @Test
    void listarPorRootIdFiltraCorrectamente() {
        UUID rootId = UUID.randomUUID();
        ImageDocument doc = new ImageDocument(UUID.randomUUID(), rootId, ImageType.REQUEST, "img.jpg", "image/jpeg", 50L, VALID_BYTES, LocalDateTime.now(), null);
        when(repository.findByRootIdAndTipoAndActivoTrue(rootId, ImageType.REQUEST)).thenReturn(List.of(doc));

        List<ImageResponseDTO> list = service.getImagesByRootId(rootId, ImageType.REQUEST);
        assertEquals(1, list.size());
        assertEquals(rootId, list.get(0).rootId());
    }

    @Test
    void eliminarImagenAplicaBorradoLogico() {
        UUID imageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ImageDocument doc = new ImageDocument(imageId, userId, ImageType.PROFILE, "img.jpg", "image/jpeg", 50L, VALID_BYTES, LocalDateTime.now(), null);
        when(repository.findByIdAndActivoTrue(imageId)).thenReturn(Optional.of(doc));

        service.deleteImage(imageId, userId, false);

        assertFalse(doc.isActivo());
        verify(repository).save(doc);
    }

    @Test
    void eliminarImagenPerfilDeOtroUsuarioLanzaNotFound() {
        UUID imageId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        ImageDocument doc = new ImageDocument(imageId, ownerId, ImageType.PROFILE, "img.jpg", "image/jpeg", 50L, VALID_BYTES, LocalDateTime.now(), null);
        when(repository.findByIdAndActivoTrue(imageId)).thenReturn(Optional.of(doc));

        assertThrows(ImageNotFoundException.class, () -> service.deleteImage(imageId, attackerId, false));
    }

    @Test
    void adminPuedeEliminarCualquierImagen() {
        UUID imageId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        ImageDocument doc = new ImageDocument(imageId, ownerId, ImageType.PROFILE, "img.jpg", "image/jpeg", 50L, VALID_BYTES, LocalDateTime.now(), null);
        when(repository.findByIdAndActivoTrue(imageId)).thenReturn(Optional.of(doc));

        service.deleteImage(imageId, adminId, true);

        assertFalse(doc.isActivo());
        verify(repository).save(doc);
    }

    @Test
    void guardarImagenValidaParametrosObligatorios() {
        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", VALID_BYTES);
        assertThrows(InvalidImageException.class, () -> service.saveImage(null, ImageType.PROFILE, file, null));
        assertThrows(InvalidImageException.class, () -> service.saveImage(UUID.randomUUID(), null, file, null));
        assertThrows(InvalidImageException.class, () -> service.saveImage(UUID.randomUUID(), ImageType.PROFILE, null, null));
    }
}
