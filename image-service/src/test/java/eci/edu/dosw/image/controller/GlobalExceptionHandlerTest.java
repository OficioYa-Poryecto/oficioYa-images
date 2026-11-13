package eci.edu.dosw.image.controller;

import eci.edu.dosw.image.exception.ImageAccessDeniedException;
import eci.edu.dosw.image.exception.ImageNotFoundException;
import eci.edu.dosw.image.exception.ImageQuotaExceededException;
import eci.edu.dosw.image.exception.InvalidImageException;
import eci.edu.dosw.image.model.ImageType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void noEncontradaDevuelve404() {
        ResponseEntity<ErrorResponse> res = handler.handleNotFound(new ImageNotFoundException("no existe"));

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertEquals("no existe", res.getBody().message());
    }

    @Test
    void parametroConFormatoInvalidoDevuelve400() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "FOTO", ImageType.class, "tipo", null, new IllegalArgumentException());

        ResponseEntity<ErrorResponse> res = handler.handleTypeMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("El parametro 'tipo' tiene un valor invalido", res.getBody().message());
    }

    @Test
    void rutaInexistenteConservaSu404() {
        ResponseEntity<ErrorResponse> res = handler.handleGeneric(
                new NoResourceFoundException(HttpMethod.DELETE, "/api/v1/images/", "api/v1/images/"));

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertEquals(404, res.getBody().status());
    }

    @Test
    void metodoNoPermitidoConservaSu405() {
        ResponseEntity<ErrorResponse> res = handler.handleGeneric(new HttpRequestMethodNotSupportedException("PUT"));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, res.getStatusCode());
        assertEquals("Method Not Allowed", res.getBody().error());
    }

    @Test
    void sinPermisoDevuelve403() {
        ResponseEntity<ErrorResponse> res = handler.handleForbidden(new ImageAccessDeniedException("no es tuya"));

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
        assertEquals("no es tuya", res.getBody().message());
    }

    @Test
    void imagenInvalidaDevuelve400() {
        ResponseEntity<ErrorResponse> res = handler.handleBadRequest(new InvalidImageException("formato"));

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("Bad Request", res.getBody().error());
    }

    @Test
    void argumentoInvalidoDevuelve400() {
        ResponseEntity<ErrorResponse> res = handler.handleBadRequest(new IllegalArgumentException("malo"));

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("malo", res.getBody().message());
    }

    @Test
    void cuotaSuperadaDevuelve400ConErrorPropio() {
        ResponseEntity<ErrorResponse> res = handler.handleQuota(new ImageQuotaExceededException("limite"));

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("Quota Exceeded", res.getBody().error());
    }

    @Test
    void archivoMuyGrandeDevuelve413() {
        ResponseEntity<ErrorResponse> res = handler.handleMaxSize(new MaxUploadSizeExceededException(5));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, res.getStatusCode());
        assertEquals(413, res.getBody().status());
    }

    @Test
    void errorInesperadoDevuelve500SinExponerElDetalle() {
        ResponseEntity<ErrorResponse> res = handler.handleGeneric(new IllegalStateException("password=secreto"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode());
        assertFalse(res.getBody().message().contains("secreto"));
    }
}
