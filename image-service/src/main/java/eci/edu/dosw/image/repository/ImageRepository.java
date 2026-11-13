package eci.edu.dosw.image.repository;

import eci.edu.dosw.image.model.ImageDocument;
import eci.edu.dosw.image.model.ImageType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImageRepository extends MongoRepository<ImageDocument, UUID> {

    /** Busca una imagen activa por su ID */
    Optional<ImageDocument> findByIdAndActivoTrue(UUID id);

    /** Lista todas las imagenes activas de una entidad (solicitud, reporte, usuario) */
    List<ImageDocument> findByRootIdAndActivoTrue(UUID rootId);

    /** Lista imagenes activas de una entidad filtradas por tipo */
    List<ImageDocument> findByRootIdAndTipoAndActivoTrue(UUID rootId, ImageType tipo);

    /** Obtiene la foto de perfil mas reciente activa de un usuario */
    Optional<ImageDocument> findFirstByRootIdAndTipoAndActivoTrueOrderByFechaCargaDesc(UUID rootId, ImageType tipo);

    /** Cuenta imagenes activas de una entidad por tipo (para aplicar cuotas) */
    long countByRootIdAndTipoAndActivoTrue(UUID rootId, ImageType tipo);
}
