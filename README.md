# CarCare — dev-final

Aplicación Android existente integrada: formulario original de `master` → aplicación de `angel-eduardo1`, con búsqueda de mantenimientos en tiempo real sobre Room y filtros por estado.

## Abrir y ejecutar

```bash
git fetch origin
git switch dev-final
git pull --ff-only origin dev-final
```

Abre la carpeta de este repositorio en Android Studio, sincroniza Gradle y ejecuta el módulo `app`. En una instalación nueva, registra tu perfil y agrega tu vehículo desde Inicio. El perfil es local; no se implementa autenticación por contraseña.

Se conservan Room, RecyclerView, ViewBinding y Material, la paleta oscura y las pantallas originales. El formulario de registro está en `RegistroActivity`; la navegación permanece en `MainActivity`.

- [Análisis, arquitectura, instalación y pruebas](docs/DEV_FINAL.md)
- [Inventario de archivos para reemplazo manual](docs/ARCHIVOS_DEV_FINAL.txt)
- [Resultados de compilación y pruebas](docs/VALIDACION.md)

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

El proyecto conserva SDK 37, Gradle 9.5.0 y la configuración de JDK 25 del repositorio. La migración Room de versión 1 a 2 preserva los usuarios existentes; no se utilizan migraciones destructivas.
