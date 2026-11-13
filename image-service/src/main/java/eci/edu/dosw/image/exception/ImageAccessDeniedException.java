package eci.edu.dosw.image.exception;

/** El usuario autenticado no puede subir o borrar esta imagen (403). */
public class ImageAccessDeniedException extends RuntimeException {
    public ImageAccessDeniedException(String message) {
        super(message);
    }
}
