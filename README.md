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
| `DELETE` | `/{id}` | Token | Borrado lógico |

`rootId` es el id de la entidad dueña de la imagen (usuario, solicitud, reporte...). Tipos actuales:

| Tipo | Uso | Regla |
|---|---|---|
| `PROFILE` | Foto de perfil, `rootId` = id del usuario | Solo una activa: al subir otra, la anterior se desactiva |
| `REQUEST` | Fotos de una solicitud | Máximo 10 activas |
| `REPORT` | Evidencias de un reporte | Máximo 10 activas |

Documentación interactiva: `/swagger-ui.html`

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `JWT_SECRET` | Sí | Clave HS256/384 en **Base64**, la misma de Identity. Sin ella el servicio no arranca |
| `MONGODB_URI` | No | Por defecto `mongodb://localhost:27017/oficioya_images` |
| `PORT` | No | Por defecto `8083` |

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
