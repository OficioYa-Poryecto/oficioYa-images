package eci.edu.dosw.image.model;

/**
 * Tipos de imagen soportados y cuantas imagenes activas admite cada uno por entidad (rootId).
 * - PROFILE          (Users)       : foto de perfil, rootId = id del usuario. Solo 1 activa: la nueva reemplaza a la anterior.
 * - WORK             (Worker)      : galeria de trabajos del trabajador, rootId = id del trabajador.
 * - SERVICE_EVIDENCE (Worker)      : fotos al completar un servicio, rootId = id del servicio/solicitud.
 * - REQUEST          (Solicitudes) : fotos adjuntas a una solicitud, rootId = id de la solicitud.
 * - REVIEW           (Resenas)     : fotos de una resena, rootId = id de la resena.
 * - REPORT           (Reportes)    : evidencias de un reporte, rootId = id del reporte.
 */
public enum ImageType {
    PROFILE(1),
    WORK(50),
    SERVICE_EVIDENCE(5),
    REQUEST(5),
    REVIEW(5),
    REPORT(5);

    private final int maxActive;

    ImageType(int maxActive) {
        this.maxActive = maxActive;
    }

    /** Maximo de imagenes activas por rootId */
    public int getMaxActive() {
        return maxActive;
    }

    /** Si al subir una nueva se desactiva la anterior en lugar de aplicar la cuota */
    public boolean replacesPrevious() {
        return this == PROFILE;
    }
}
