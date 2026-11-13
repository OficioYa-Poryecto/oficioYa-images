package eci.edu.dosw.image.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Documento MongoDB para almacenar imagenes.
 * Coleccion "imagenes" (igual que en Oficioya-service para reutilizar la misma instancia Mongo).
 *
 * Patron de copia defensiva de bytes tomado de TechCup (TeamLogoStorageService):
 * - getData() y setData() siempre clonan el array para evitar mutacion de buffers externos.
 */
@Document(collection = "imagenes")
@CompoundIndex(name = "idx_root_tipo_activo", def = "{'rootId': 1, 'tipo': 1, 'activo': 1}")
public class ImageDocument {

    @Id
    private UUID id;

    /** Id de la entidad duena de la imagen: userId, requestId o reportId */
    private UUID rootId;

    /** Tipo de imagen: PROFILE, REQUEST o REPORT */
    private ImageType tipo;

    /** Nombre original del archivo (nunca se usa para almacenar, solo informativo) */
    private String nombre;

    /** Content-Type real detectado por magic bytes: image/jpeg, image/png, image/webp */
    private String tipoContenido;

    /** Tamano en bytes */
    private Long tamano;

    /** Binario de la imagen */
    private byte[] datos;

    private LocalDateTime fechaCarga;

    /** Referencia textual opcional (p.ej. numero de solicitud, codigo de reporte) */
    private String referenciaExterna;

    /** false = borrado logico */
    private boolean activo;

    public ImageDocument() {
        this.activo = true;
    }

    public ImageDocument(UUID id, UUID rootId, ImageType tipo, String nombre,
                         String tipoContenido, Long tamano, byte[] datos,
                         LocalDateTime fechaCarga, String referenciaExterna) {
        this.id = (id != null) ? id : UUID.randomUUID();
        this.rootId = rootId;
        this.tipo = tipo;
        this.nombre = nombre;
        this.tipoContenido = tipoContenido;
        this.tamano = tamano;
        this.datos = (datos != null) ? datos.clone() : new byte[0];
        this.fechaCarga = (fechaCarga != null) ? fechaCarga : LocalDateTime.now();
        this.referenciaExterna = referenciaExterna;
        this.activo = true;
    }

    // ---- Getters y Setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getRootId() { return rootId; }
    public void setRootId(UUID rootId) { this.rootId = rootId; }

    public ImageType getTipo() { return tipo; }
    public void setTipo(ImageType tipo) { this.tipo = tipo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTipoContenido() { return tipoContenido; }
    public void setTipoContenido(String tipoContenido) { this.tipoContenido = tipoContenido; }

    public Long getTamano() { return tamano; }
    public void setTamano(Long tamano) { this.tamano = tamano; }

    /** Copia defensiva al leer (patron TechCup) */
    public byte[] getDatos() { return (datos != null) ? datos.clone() : new byte[0]; }

    /** Copia defensiva al escribir (patron TechCup) */
    public void setDatos(byte[] datos) { this.datos = (datos != null) ? datos.clone() : new byte[0]; }

    public LocalDateTime getFechaCarga() { return fechaCarga; }
    public void setFechaCarga(LocalDateTime fechaCarga) { this.fechaCarga = fechaCarga; }

    public String getReferenciaExterna() { return referenciaExterna; }
    public void setReferenciaExterna(String referenciaExterna) { this.referenciaExterna = referenciaExterna; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
