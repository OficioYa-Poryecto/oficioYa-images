package eci.edu.dosw.image.controller;

import eci.edu.dosw.image.exception.ImageNotFoundException;
import eci.edu.dosw.image.exception.ImageQuotaExceededException;
import eci.edu.dosw.image.exception.InvalidImageException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
