package eci.edu.dosw.image.controller;

import eci.edu.dosw.image.dto.ImageResponseDTO;
import eci.edu.dosw.image.exception.ImageNotFoundException;
import eci.edu.dosw.image.exception.ImageQuotaExceededException;
import eci.edu.dosw.image.model.ImageDocument;
import eci.edu.dosw.image.model.ImageType;
import eci.edu.dosw.image.service.ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ImageControllerTest {

    private MockMvc mvc;
    private ImageStorageService service;

    private static final byte[] VALID_BYTES = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    @BeforeEach
    void setUp() {
        service = mock(ImageStorageService.class);
        ImageController controller = new ImageController(service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void descargaImagenBinariaCorrectamente() throws Exception {
        UUID id = UUID.randomUUID();
        ImageDocument doc = new ImageDocument(id, UUID.randomUUID(), ImageType.PROFILE, "avatar.jpg", "image/jpeg", (long) VALID_BYTES.length, VALID_BYTES, LocalDateTime.now(), null);

        when(service.getImageById(id)).thenReturn(doc);

        mvc.perform(get("/api/v1/images/" + id))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"avatar.jpg\""))
                .andExpect(content().bytes(VALID_BYTES));
    }

    @Test
    void consultaMetadatosSinBytes() throws Exception {
        UUID id = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        ImageDocument doc = new ImageDocument(id, rootId, ImageType.PROFILE, "avatar.jpg", "image/jpeg", 500L, VALID_BYTES, LocalDateTime.now(), null);
        ImageResponseDTO dto = new ImageResponseDTO(id, rootId, ImageType.PROFILE, "avatar.jpg", "image/jpeg", 500L, LocalDateTime.now(), null, "/api/v1/images/" + id, null);

        when(service.getImageById(id)).thenReturn(doc);
        when(service.toDTO(doc)).thenReturn(dto);

        mvc.perform(get("/api/v1/images/" + id + "/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.tipo").value("PROFILE"))
                .andExpect(jsonPath("$.url").value("/api/v1/images/" + id));
    }

    @Test
    void listarImagenesPorRootIdRetornaLista() throws Exception {
        UUID rootId = UUID.randomUUID();
        ImageResponseDTO dto = new ImageResponseDTO(UUID.randomUUID(), rootId, ImageType.REQUEST, "req1.jpg", "image/jpeg", 300L, LocalDateTime.now(), null, "/api/v1/images/1", null);

        when(service.getImagesByRootId(eq(rootId), eq(ImageType.REQUEST))).thenReturn(List.of(dto));

        mvc.perform(get("/api/v1/images/by-root/" + rootId).param("tipo", "REQUEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("REQUEST"))
                .andExpect(jsonPath("$[0].nombre").value("req1.jpg"));
    }

    @Test
    void subirImagenIndividualRetorna201Created() throws Exception {
        UUID rootId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", VALID_BYTES);

        ImageResponseDTO dto = new ImageResponseDTO(imageId, rootId, ImageType.PROFILE, "test.png", "image/png", (long) VALID_BYTES.length, LocalDateTime.now(), null, "/api/v1/images/" + imageId, null);
        when(service.saveImage(eq(rootId), eq(ImageType.PROFILE), any(), isNull(), any(), anyBoolean())).thenReturn(dto);

        mvc.perform(multipart("/api/v1/images")
                        .file(file)
                        .param("rootId", rootId.toString())
                        .param("tipo", "PROFILE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(imageId.toString()));
    }

    @Test
    void subirBatchRetornaLista201Created() throws Exception {
        UUID rootId = UUID.randomUUID();
        MockMultipartFile f1 = new MockMultipartFile("files", "test1.png", "image/png", VALID_BYTES);
        MockMultipartFile f2 = new MockMultipartFile("files", "test2.png", "image/png", VALID_BYTES);

        ImageResponseDTO dto1 = new ImageResponseDTO(UUID.randomUUID(), rootId, ImageType.REQUEST, "test1.png", "image/png", 100L, LocalDateTime.now(), null, "/url1", null);
        when(service.saveBatchImages(eq(rootId), eq(ImageType.REQUEST), anyList(), isNull(), any(), anyBoolean())).thenReturn(List.of(dto1));

        mvc.perform(multipart("/api/v1/images/batch")
                        .file(f1)
                        .file(f2)
                        .param("rootId", rootId.toString())
                        .param("tipo", "REQUEST"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].nombre").value("test1.png"));
    }

    @Test
    void eliminarImagenRetorna204NoContent() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(service).deleteImage(eq(id), any(), anyBoolean());

        mvc.perform(delete("/api/v1/images/" + id))
                .andExpect(status().isNoContent());
    }

    @Test
    void imagenNoEncontradaDevuelve404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getImageById(id)).thenThrow(new ImageNotFoundException("No encontrada"));

        mvc.perform(get("/api/v1/images/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void cuotaSuperadaDevuelve400() throws Exception {
        UUID rootId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", VALID_BYTES);

        when(service.saveImage(any(), any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new ImageQuotaExceededException("Limite alcanzado"));

        mvc.perform(multipart("/api/v1/images")
                        .file(file)
                        .param("rootId", rootId.toString())
                        .param("tipo", "REQUEST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Quota Exceeded"));
    }
}
