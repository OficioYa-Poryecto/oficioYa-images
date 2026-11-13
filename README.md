# oficioYa-images

Microservicio de imágenes de OficioYa. Guarda y entrega las imágenes que usan los demás dominios:
foto de perfil (Users), fotos de solicitudes, evidencias de reportes, etc.

- Spring Boot 4 · Java 21 · MongoDB
- Autenticación con el mismo JWT que emite Identity (`sub` = UUID del usuario, `roles` = lista de roles)
- Las imágenes se validan por su contenido real (JPEG, PNG o WebP), no por la extensión. Máximo 5 MB.

## Endpoints

Base: `/api/v1/images`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `POST` | `/` | Token | Sube una imagen (`multipart/form-data`: `file`, `rootId`, `tipo`, `referenciaExterna` opcional) |
| `POST` | `/batch` | Token | Sube varias imágenes (`files`, `rootId`, `tipo`) |
| `GET` | `/{id}` | Público | Devuelve el binario de la imagen (se puede usar directo en un `<img src>`) |
| `GET` | `/{id}/metadata` | Público | Devuelve los datos de la imagen sin los bytes |
| `GET` | `/by-root/{rootId}?tipo=` | Token | Lista las imágenes activas de una entidad |
| `DELETE` | `/{id}` | Token | Borrado lógico. Solo quien subió la imagen o un `ADMIN` |

Las respuestas incluyen `subidoPor` (el usuario que subió la imagen) y `url` para descargarla.

### Tipos de imagen

`rootId` es el id de la entidad dueña de la imagen. El límite es de imágenes **activas** por `rootId`.

| Tipo | Dominio | Uso | `rootId` | Límite |
|---|---|---|---|---|
| `PROFILE` | Users | Foto de perfil | Id del usuario | 1 (la nueva reemplaza a la anterior) |
| `WORK` | Worker | Galería de trabajos | Id del trabajador | 50 |
| `SERVICE_EVIDENCE` | Worker | Fotos al completar un servicio | Id del servicio | 5 |
| `REQUEST` | Solicitudes | Fotos de una solicitud | Id de la solicitud | 5 |
| `REVIEW` | Reseñas | Fotos de una reseña | Id de la reseña | 5 |
| `REPORT` | Reportes | Evidencias de un reporte | Id del reporte | 5 |

### Permisos

- Para subir, listar y borrar hay que enviar `Authorization: Bearer <token>`.
- `PROFILE`: cada usuario solo puede cambiar **su propia** foto (`rootId` = su id). Un `ADMIN` puede cambiar cualquiera.
- Borrar: solo quien subió la imagen (o el dueño de la foto de perfil) o un `ADMIN`. Si no, responde `403`.
- Descargar y ver metadatos por id es público, para poder usar la URL directo en el front.

### Códigos de error

| Código | Cuándo |
|---|---|
| `400` | Archivo vacío o con formato no soportado, parámetro faltante o inválido (`tipo`, `rootId`), límite del tipo alcanzado |
| `401` | Sin token o token inválido |
| `403` | Sin permiso: foto de perfil ajena o borrar imagen de otro |
| `404` | La imagen no existe o fue borrada |
| `413` | Archivo de más de 5 MB |

Documentación interactiva: `/swagger-ui.html`

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `JWT_SECRET` | Sí | Clave HS256/384 en **Base64**, la misma de Identity. Sin ella el servicio no arranca |
| `MONGODB_URI` | No | Por defecto `mongodb://localhost:27017/oficioya_images` |
| `PORT` | No | Por defecto `8083` |
| `CORS_ALLOWED_ORIGINS` | No | URLs del front que pueden llamar al servicio, separadas por coma. Por defecto `http://localhost:5173` |

En local se pueden poner en `image-service/.env` (copiar de `.env.example`). Nunca se suben al repositorio.

## Ejecutar en local

```bash
# MongoDB en Docker (solo la primera vez; luego: docker start oficioya-images-mongo)
docker run -d --name oficioya-images-mongo -p 27017:27017 -v oficioya_images_mongodata:/data/db mongo:8

cd image-service
./mvnw spring-boot:run
```

## Pruebas y cobertura

```bash
cd image-service
./mvnw verify
```

Corre las pruebas y exige mínimo 80 % de cobertura con JaCoCo. Reporte en `target/site/jacoco/index.html`.

## Flujo de ramas

`feature/*` desde `develop` → PR a `develop` → cuando esté estable, PR de `develop` a `main` (producción).
