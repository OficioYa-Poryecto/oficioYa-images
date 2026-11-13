package eci.edu.dosw.image.security;

import eci.edu.dosw.image.controller.GlobalExceptionHandler;
import eci.edu.dosw.image.controller.ImageController;
import eci.edu.dosw.image.dto.ImageResponseDTO;
import eci.edu.dosw.image.exception.ImageAccessDeniedException;
import eci.edu.dosw.image.model.ImageDocument;
import eci.edu.dosw.image.model.ImageType;
import eci.edu.dosw.image.service.ImageStorageService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba las reglas de acceso de SecurityConfig con el filtro JWT real:
 * qué rutas son públicas y cuáles exigen un token válido.
 */
@WebMvcTest(ImageController.class)
@Import({SecurityConfig.class, ImageJwtAuthFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class SecurityConfigTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ImageStorageService service;

    @Value("${jwt.secret}")
    private String secret;

    private String token(UUID userId, String... roles) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("roles", List.of(roles))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .compact();
    }

    private ImageResponseDTO dto(UUID id, UUID rootId) {
        return new ImageResponseDTO(id, rootId, ImageType.PROFILE, "a.jpg", "image/jpeg", 4L,
                LocalDateTime.now(), null, "/api/v1/images/" + id, null);
    }

    @Test
    void descargaPorIdEsPublica() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getImageById(id)).thenReturn(new ImageDocument(id, UUID.randomUUID(), ImageType.PROFILE,
                "a.jpg", "image/jpeg", 4L, JPEG, LocalDateTime.now(), null));

        mvc.perform(get("/api/v1/images/" + id)).andExpect(status().isOk());
    }

    @Test
    void metadatosPorIdSonPublicos() throws Exception {
        UUID id = UUID.randomUUID();
        ImageDocument doc = new ImageDocument(id, UUID.randomUUID(), ImageType.PROFILE,
                "a.jpg", "image/jpeg", 4L, JPEG, LocalDateTime.now(), null);
        when(service.getImageById(id)).thenReturn(doc);
        when(service.toDTO(doc)).thenReturn(dto(id, doc.getRootId()));

        mvc.perform(get("/api/v1/images/" + id + "/metadata")).andExpect(status().isOk());
    }

    @Test
    void listarPorRootSinTokenDevuelve401ConCuerpoJson() throws Exception {
        mvc.perform(get("/api/v1/images/by-root/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void listarPorRootConTokenValidoDevuelve200() throws Exception {
        UUID rootId = UUID.randomUUID();
        when(service.getImagesByRootId(rootId, null)).thenReturn(List.of());

        mvc.perform(get("/api/v1/images/by-root/" + rootId)
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), "CONTRATANTE")))
                .andExpect(status().isOk());
    }

    @Test
    void subirSinTokenDevuelve401() throws Exception {
        mvc.perform(multipart("/api/v1/images")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG))
                        .param("rootId", UUID.randomUUID().toString())
                        .param("tipo", "PROFILE"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subirConTokenFirmadoConOtraClaveDevuelve401() throws Exception {
        String ajeno = Jwts.builder().subject(UUID.randomUUID().toString())
                .claim("roles", List.of("TRABAJADOR"))
                .signWith(Jwts.SIG.HS384.key().build())
                .compact();

        mvc.perform(multipart("/api/v1/images")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG))
                        .param("rootId", UUID.randomUUID().toString())
                        .param("tipo", "PROFILE")
                        .header("Authorization", "Bearer " + ajeno))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subirConTokenValidoDevuelve201() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        when(service.saveImage(eq(userId), eq(ImageType.PROFILE), any(), isNull(), eq(userId), eq(false)))
                .thenReturn(dto(imageId, userId));

        mvc.perform(multipart("/api/v1/images")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG))
                        .param("rootId", userId.toString())
                        .param("tipo", "PROFILE")
                        .header("Authorization", "Bearer " + token(userId, "CONTRATANTE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(imageId.toString()));
    }

    @Test
    void borrarSinTokenDevuelve401() throws Exception {
        mvc.perform(delete("/api/v1/images/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void borrarConTokenPasaElUsuarioYSiEsAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        mvc.perform(delete("/api/v1/images/" + id)
                        .header("Authorization", "Bearer " + token(adminId, "ADMIN")))
                .andExpect(status().isNoContent());

        verify(service).deleteImage(id, adminId, true);
    }

    @Test
    void borrarImagenAjenaDevuelve403() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        doThrow(new ImageAccessDeniedException("Solo quien subio la imagen o un ADMIN puede borrarla"))
                .when(service).deleteImage(id, userId, false);

        mvc.perform(delete("/api/v1/images/" + id)
                        .header("Authorization", "Bearer " + token(userId, "TRABAJADOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void corsPermiteElOrigenConfigurado() throws Exception {
        mvc.perform(options("/api/v1/images")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void corsRechazaOrigenesNoConfigurados() throws Exception {
        mvc.perform(options("/api/v1/images")
                        .header("Origin", "https://sitio-ajeno.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void borrarConTokenDeUsuarioNormalNoEsAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mvc.perform(delete("/api/v1/images/" + id)
                        .header("Authorization", "Bearer " + token(userId, "TRABAJADOR")))
                .andExpect(status().isNoContent());

        verify(service).deleteImage(id, userId, false);
    }
}
