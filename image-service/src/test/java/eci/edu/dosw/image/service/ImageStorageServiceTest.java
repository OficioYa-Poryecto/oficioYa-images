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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    private static MockMultipartFile archivo(String nombre) {
        return new MockMultipartFile("file", nombre, "image/jpeg", VALID_BYTES);
    }

    private static ImageDocument documento(UUID rootId, ImageType tipo) {
        return new ImageDocument(UUID.randomUUID(), rootId, tipo, "img.jpg", "image/jpeg", 50L, VALID_BYTES,
                LocalDateTime.now(), null);
    }

    // ---------- Subida ----------

    @Test
    void guardarImagenPerfilPropiaDesactivaLaAnterior() throws IOException {
        UUID userId = UUID.randomUUID();
        ImageDocument previa = documento(userId, ImageType.PROFILE);
        when(repository.findFirstByRootIdAndTipoAndActivoTrueOrderByFechaCargaDesc(userId, ImageType.PROFILE))
                .thenReturn(Optional.of(previa));

        ImageResponseDTO response = service.saveImage(userId, ImageType.PROFILE, archivo("avatar.jpg"), null, userId, false);

        assertEquals(userId, response.rootId());
        assertEquals(ImageType.PROFILE, response.tipo());
        assertEquals(userId, response.subidoPor());
        assertFalse(previa.isActivo());
        verify(repository).save(previa);
    }

    @Test
    void noSePuedeSubirLaFotoDePerfilDeOtroUsuario() {
        UUID duenoId = UUID.randomUUID();
        UUID otroId = UUID.randomUUID();

        assertThrows(ImageAccessDeniedException.class,
                () -> service.saveImage(duenoId, ImageType.PROFILE, archivo("a.jpg"), null, otroId, false));
        verify(repository, never()).save(any());
    }

    @Test
    void adminPuedeSubirLaFotoDePerfilDeOtroUsuario() throws IOException {
        UUID duenoId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(repository.findFirstByRootIdAndTipoAndActivoTrueOrderByFechaCargaDesc(duenoId, ImageType.PROFILE))
                .thenReturn(Optional.empty());

        ImageResponseDTO response = service.saveImage(duenoId, ImageType.PROFILE, archivo("a.jpg"), null, adminId, true);

        assertEquals(duenoId, response.rootId());
        assertEquals(adminId, response.subidoPor());
    }

    @Test
    void subirSinUsuarioAutenticadoSeRechaza() {
        assertThrows(ImageAccessDeniedException.class,
                () -> service.saveImage(UUID.randomUUID(), ImageType.WORK, archivo("a.jpg"), null, null, false));
    }

    @Test
    void guardarImagenRegistraQuienLaSubio() throws IOException {
        UUID workerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repository.countByRootIdAndTipoAndActivoTrue(workerId, ImageType.WORK)).thenReturn(0L);

        service.saveImage(workerId, ImageType.WORK, archivo("trabajo.jpg"), "galeria", userId, false);

        ArgumentCaptor<ImageDocument> captor = ArgumentCaptor.forClass(ImageDocument.class);
        verify(repository).save(captor.capture());
        assertEquals(userId, captor.getValue().getSubidoPor());
        assertEquals(ImageType.WORK, captor.getValue().getTipo());
        assertEquals("galeria", captor.getValue().getReferenciaExterna());
    }

    @ParameterizedTest
    @EnumSource(value = ImageType.class, names = "PROFILE", mode = EnumSource.Mode.EXCLUDE)
    void cadaTipoRespetaSuLimite(ImageType tipo) {
        UUID rootId = UUID.randomUUID();
        when(repository.countByRootIdAndTipoAndActivoTrue(rootId, tipo)).thenReturn((long) tipo.getMaxActive());

        assertThrows(ImageQuotaExceededException.class,
                () -> service.saveImage(rootId, tipo, archivo("a.jpg"), null, UUID.randomUUID(), false));
    }

    @Test
    void debajoDelLimiteSePuedeSubir() throws IOException {
        UUID solicitudId = UUID.randomUUID();
        when(repository.countByRootIdAndTipoAndActivoTrue(solicitudId, ImageType.REQUEST)).thenReturn(4L);

        ImageResponseDTO response = service.saveImage(solicitudId, ImageType.REQUEST, archivo("a.jpg"), null,
                UUID.randomUUID(), false);

        assertEquals(ImageType.REQUEST, response.tipo());
    }

    @Test
    void imagenConFormatoInvalidoSeRechaza() {
        when(validator.validateAndDetectContentType(any())).thenThrow(new IllegalArgumentException("Formato no soportado"));

        InvalidImageException ex = assertThrows(InvalidImageException.class,
                () -> service.saveImage(UUID.randomUUID(), ImageType.REVIEW, archivo("a.gif"), null, UUID.randomUUID(), false));
        assertEquals("Formato no soportado", ex.getMessage());
    }

    @Test
    void guardarImagenValidaParametrosObligatorios() {
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = archivo("img.jpg");
        assertThrows(InvalidImageException.class, () -> service.saveImage(null, ImageType.WORK, file, null, userId, false));
        assertThrows(InvalidImageException.class, () -> service.saveImage(userId, null, file, null, userId, false));
        assertThrows(InvalidImageException.class, () -> service.saveImage(userId, ImageType.WORK, null, null, userId, false));
        MockMultipartFile vacio = new MockMultipartFile("file", "v.jpg", "image/jpeg", new byte[0]);
        assertThrows(InvalidImageException.class, () -> service.saveImage(userId, ImageType.WORK, vacio, null, userId, false));
    }

    // ---------- Lote ----------

    @Test
    void guardarBatchGuardaMultiplesImagenes() throws IOException {
        UUID solicitudId = UUID.randomUUID();
        when(repository.countByRootIdAndTipoAndActivoTrue(solicitudId, ImageType.REQUEST)).thenReturn(2L);

        List<ImageResponseDTO> response = service.saveBatchImages(solicitudId, ImageType.REQUEST,
                List.of(archivo("1.jpg"), archivo("2.jpg")), "REF", UUID.randomUUID(), false);

        assertEquals(2, response.size());
        verify(repository, times(2)).save(any(ImageDocument.class));
    }

    @Test
    void guardarBatchRechazaSiSuperaElTotalPermitido() {
        UUID solicitudId = UUID.randomUUID();
        when(repository.countByRootIdAndTipoAndActivoTrue(solicitudId, ImageType.REQUEST)).thenReturn(4L);

        assertThrows(ImageQuotaExceededException.class,
                () -> service.saveBatchImages(solicitudId, ImageType.REQUEST,
                        List.of(archivo("1.jpg"), archivo("2.jpg")), null, UUID.randomUUID(), false));
    }

    @Test
    void batchDePerfilSoloAdmiteUnaImagen() {
        UUID userId = UUID.randomUUID();

        assertThrows(InvalidImageException.class,
                () -> service.saveBatchImages(userId, ImageType.PROFILE,
                        List.of(archivo("1.jpg"), archivo("2.jpg")), null, userId, false));
    }

    @Test
    void batchDePerfilConUnaImagenFunciona() throws IOException {
        UUID userId = UUID.randomUUID();
        when(repository.findFirstByRootIdAndTipoAndActivoTrueOrderByFechaCargaDesc(userId, ImageType.PROFILE))
                .thenReturn(Optional.empty());

        List<ImageResponseDTO> response = service.saveBatchImages(userId, ImageType.PROFILE,
                List.of(archivo("1.jpg")), null, userId, false);

        assertEquals(1, response.size());
    }

    @Test
    void batchDePerfilAjenoSeRechaza() {
        assertThrows(ImageAccessDeniedException.class,
                () -> service.saveBatchImages(UUID.randomUUID(), ImageType.PROFILE,
                        List.of(archivo("1.jpg")), null, UUID.randomUUID(), false));
    }

    @Test
    void batchVacioONuloDevuelveListaVacia() throws IOException {
        UUID rootId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MockMultipartFile vacio = new MockMultipartFile("files", "v.jpg", "image/jpeg", new byte[0]);

        assertTrue(service.saveBatchImages(rootId, ImageType.WORK, null, null, userId, false).isEmpty());
        assertTrue(service.saveBatchImages(rootId, ImageType.WORK, List.of(), null, userId, false).isEmpty());
        assertTrue(service.saveBatchImages(rootId, ImageType.WORK, List.of(vacio), null, userId, false).isEmpty());
        assertThrows(InvalidImageException.class,
                () -> service.saveBatchImages(null, ImageType.WORK, List.of(archivo("a.jpg")), null, userId, false));
    }

    // ---------- Consulta ----------

    @Test
    void obtenerImagenPorIdRetornaDocumentoSiExiste() {
        ImageDocument doc = documento(UUID.randomUUID(), ImageType.PROFILE);
        when(repository.findByIdAndActivoTrue(doc.getId())).thenReturn(Optional.of(doc));

        assertEquals(doc.getId(), service.getImageById(doc.getId()).getId());
    }

    @Test
    void obtenerImagenPorIdLanzaNotFoundSiNoExiste() {
        UUID imageId = UUID.randomUUID();
        when(repository.findByIdAndActivoTrue(imageId)).thenReturn(Optional.empty());

        assertThrows(ImageNotFoundException.class, () -> service.getImageById(imageId));
    }

    @Test
    void listarPorRootIdFiltraPorTipo() {
        UUID rootId = UUID.randomUUID();
        when(repository.findByRootIdAndTipoAndActivoTrue(rootId, ImageType.REQUEST))
                .thenReturn(List.of(documento(rootId, ImageType.REQUEST)));

        List<ImageResponseDTO> list = service.getImagesByRootId(rootId, ImageType.REQUEST);

        assertEquals(1, list.size());
        assertEquals(rootId, list.get(0).rootId());
    }

    @Test
    void listarPorRootIdSinTipoDevuelveTodas() {
        UUID rootId = UUID.randomUUID();
        when(repository.findByRootIdAndActivoTrue(rootId))
                .thenReturn(List.of(documento(rootId, ImageType.WORK), documento(rootId, ImageType.SERVICE_EVIDENCE)));

        assertEquals(2, service.getImagesByRootId(rootId, null).size());
        assertThrows(InvalidImageException.class, () -> service.getImagesByRootId(null, null));
    }

    // ---------- Borrado ----------

    @Test
    void quienSubioLaImagenPuedeBorrarla() {
        UUID userId = UUID.randomUUID();
        ImageDocument doc = documento(UUID.randomUUID(), ImageType.WORK);
        doc.setSubidoPor(userId);
        when(repository.findByIdAndActivoTrue(doc.getId())).thenReturn(Optional.of(doc));

        service.deleteImage(doc.getId(), userId, false);

        assertFalse(doc.isActivo());
        verify(repository).save(doc);
    }

    @Test
    void otroUsuarioNoPuedeBorrarUnaImagenAjena() {
        ImageDocument doc = documento(UUID.randomUUID(), ImageType.REQUEST);
        doc.setSubidoPor(UUID.randomUUID());
        when(repository.findByIdAndActivoTrue(doc.getId())).thenReturn(Optional.of(doc));

        assertThrows(ImageAccessDeniedException.class, () -> service.deleteImage(doc.getId(), UUID.randomUUID(), false));
        assertTrue(doc.isActivo());
    }

    @Test
    void elDuenoDeLaFotoDePerfilPuedeBorrarlaAunqueNoTengaSubidoPor() {
        UUID userId = UUID.randomUUID();
        ImageDocument doc = documento(userId, ImageType.PROFILE);
        when(repository.findByIdAndActivoTrue(doc.getId())).thenReturn(Optional.of(doc));

        service.deleteImage(doc.getId(), userId, false);

        assertFalse(doc.isActivo());
    }

    @Test
    void sinUsuarioNoSePuedeBorrar() {
        ImageDocument doc = documento(UUID.randomUUID(), ImageType.REVIEW);
        when(repository.findByIdAndActivoTrue(doc.getId())).thenReturn(Optional.of(doc));

        assertThrows(ImageAccessDeniedException.class, () -> service.deleteImage(doc.getId(), null, false));
    }

    @Test
    void adminPuedeEliminarCualquierImagen() {
        ImageDocument doc = documento(UUID.randomUUID(), ImageType.REPORT);
        doc.setSubidoPor(UUID.randomUUID());
        when(repository.findByIdAndActivoTrue(doc.getId())).thenReturn(Optional.of(doc));

        service.deleteImage(doc.getId(), UUID.randomUUID(), true);

        assertFalse(doc.isActivo());
        verify(repository).save(doc);
    }
}
