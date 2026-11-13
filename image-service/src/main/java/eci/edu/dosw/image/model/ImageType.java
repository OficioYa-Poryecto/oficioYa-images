package eci.edu.dosw.image.model;

/**
 * Los tres tipos de imagen soportados por el sistema.
 * - PROFILE  : Foto de perfil (maximo 1 activa por usuario; al subir una nueva se desactiva la anterior).
 * - REQUEST  : Fotos adjuntas a una solicitud de servicio (0 o mas por solicitud).
 * - REPORT   : Fotos de evidencia en un reporte de incidencia (0 o mas por reporte).
 */
public enum ImageType {
    PROFILE,
    REQUEST,
    REPORT
}
