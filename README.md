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

- [Actualización: kilometraje sin capturas repetidas](docs/KILOMETRAJE.md)
- [Actualización: vehículos, asistente, CRUD, ayudas y privacidad](docs/MEJORAS_DEV_FINAL.md)
- [Fuentes de orientación mecánica](docs/SOURCES_GUIDANCE.md)
- [Análisis de la integración inicial](docs/DEV_FINAL.md)
- [Inventario de archivos para reemplazo manual](docs/ARCHIVOS_DEV_FINAL.txt)
- [Resultados actuales de compilación y pruebas](docs/VALIDACION_MEJORAS.md)

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

El proyecto conserva SDK 37, Gradle 9.5.0 y la configuración de JDK 25 del repositorio. Las migraciones Room de versión 1 → 2 → 3 preservan los usuarios y registros existentes; no se utilizan migraciones destructivas.
