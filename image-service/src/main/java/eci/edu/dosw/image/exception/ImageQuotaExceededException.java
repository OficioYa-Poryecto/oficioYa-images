package eci.edu.dosw.image.exception;

public class ImageQuotaExceededException extends RuntimeException {
    public ImageQuotaExceededException(String message) {
        super(message);
    }
}
