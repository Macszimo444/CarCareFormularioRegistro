# CarCare integrado — dev-final

## Resultado y alcance

`dev-final` integra el historial de `master` y `angel-eduardo1`. El formulario original se conserva como `RegistroActivity` y después abre `MainActivity`, que contiene Inicio, Mantenimiento, Gastos y Perfil. Historial y Recordatorios continúan disponibles desde sus accesos existentes. Se trabaja con el mismo paquete, las mismas entidades y la misma base `carcare.db`.

El registro es **un perfil local en este dispositivo**, como en el proyecto original: no existía contraseña ni autenticación remota. Al reabrir se continúa el perfil registrado. Cerrar el acceso desde Perfil vuelve al formulario y permite continuar o actualizar ese mismo perfil. No hay cuentas aisladas ni un servicio de login en internet.

## Qué se encontró antes de modificar

- `data/Maintenance.kt`: tabla `maintenances`; `type` es el nombre/tipo del servicio. También contiene taller (`workshop`), descripción, estado, fechas y kilometrajes.
- `data/MaintenanceDao.kt`: crear, actualizar, eliminar y consultar, con consultas `Flow`.
- `adapter/MaintenanceAdapter.kt`: lista única de RecyclerView; clic para editar y botón de eliminación.
- `ui/MantenimientoFragment.kt` y `res/layout/fragment_mantenimiento.xml`: lista y pestañas Todos/Próximos/Realizados, sin buscador.
- `ui/AddMaintenanceActivity.kt` y su layout: formulario de alta/edición/eliminación. Al editar se forzaba el estado Realizado.
- `data/AppDatabase.kt`: Room v2 en angel, v1 en master. La migración destructiva podía borrar los usuarios de master.
- No había Repository ni ViewModel. Cada cambio de pestaña abría otro colector sin cerrar el anterior.
- `master/MainActivity.kt` era únicamente el registro; angel había sustituido esa pantalla con la navegación de la aplicación.

## CRUD corroborado

| Área | Funciones disponibles |
| --- | --- |
| Mantenimiento | Crear, consultar, editar y eliminar desde interfaz y Room |
| Gastos | Crear y consultar; las operaciones DAO de edición/eliminación no tenían controles en la interfaz original |
| Recordatorios | Crear, consultar, activar/desactivar y eliminar |
| Vehículo | Registrar, consultar y editar el vehículo principal |
| Historial | Consultar mantenimientos realizados |
| Perfil | Consultar y actualizar el perfil local al volver al formulario |

No se presentan como existentes controles CRUD de otras pantallas que no estaban implementados.

## Cómo funciona el buscador

```text
Room: carcare.db / maintenances
  → MaintenanceDao.getAllMaintenancesFlow()
  → MaintenanceRepository
  → MaintenanceViewModel (texto + pestaña + datos Room)
  → MaintenanceSearch, ejecutado en Dispatchers.Default
  → StateFlow<MaintenanceUiState>
  → MantenimientoFragment, repeatOnLifecycle(STARTED)
  → MaintenanceAdapter (ListAdapter + DiffUtil)
  → RecyclerView existente
```

Hay una sola consulta observable de mantenimientos. Escribir no hace consultas SQL nuevas: se filtra la última lista que emite Room fuera del hilo principal. Si se agrega, edita o elimina un registro, Room emite la nueva lista y se aplican los mismos filtros de texto y estado.

La búsqueda es parcial, ignora mayúsculas/minúsculas y acentos, y admite varias palabras. Busca tipo/nombre, taller, descripción, estado, kilometraje actual/siguiente y fecha actual/siguiente. Acepta `120000`, `120,000`, `120.000`, `120 000`; reconoce fechas ISO (`2026-09-15`) y de presentación (`15/09/2026`).

- Todos: cualquier estado, incluido Pendiente.
- Próximos: exactamente el estado Próximo, igual que en la rama original.
- Realizados: exactamente Realizado.
- Vaciar texto conserva la pestaña seleccionada.
- La X es la función `clear_text` de Material TextInputLayout.
- La acción Buscar del teclado y desplazar la lista quitan el foco. Navegar también oculta el teclado.
- Texto y pestaña sobreviven a rotaciones/recreaciones y navegación inferior mediante SavedStateHandle/ViewModel.
- Al no encontrar resultados aparece “No encontramos mantenimientos” y “Intenta buscar con otro término.”

Se mantienen los colores `#0A0B0D`, `#15171A`, `#1E2126`, `#2A2E35`, `#0A84FF`, `#12294A`, blanco y `#8B929E`.

## Cambios de integración

- Registro original recuperado bajo otro nombre para conservar tanto el formulario como el dashboard.
- Migración Room `1 → 2` explícita: preserva usuarios y crea tablas nuevas. Las instalaciones v2 conservan sus datos.
- Instalaciones nuevas sin usuarios ni servicios ficticios. Primero se registra el perfil y el vehículo.
- Estado de mantenimiento seleccionable: Próximo, Pendiente o Realizado. La edición conserva estado y vehículo original.
- Formularios validan fechas, kilometraje y costo, evitan guardar dos veces y avisan de errores.
- Si falta vehículo, se abre el formulario existente para registrarlo; no se inventa un ID.
- Botones con espacio suficiente, acciones rápidas en filas, controles de tarjeta separados y navegación inferior adaptable.
- El acceso al próximo servicio en Inicio abre el registro mostrado.
- Recordatorios usan fecha real, cancelan la alarma al desactivarse/eliminarse y comprueban el registro antes de notificar.
- Gastos mensuales/anuales y resumen anual del historial calculados con fechas reales, sin porcentajes ni totales ficticios.
- Barras del sistema con iconos claros e insets para evitar que tapen controles.

## Instalar en Android Studio

La forma recomendada es cambiar a la rama completa, para no omitir archivos al copiarlos:

1. Guarda o haz commit de tus cambios locales antes de cambiar de rama.
2. En la terminal del repositorio:

   ```bash
   git fetch origin
   git switch dev-final
   git pull --ff-only origin dev-final
   ```

   Si la rama todavía no existe localmente: `git switch --track origin/dev-final`.
3. Abre la carpeta que contiene `settings.gradle.kts` en Android Studio.
4. Pulsa **Sync Project with Gradle Files**.
5. Conserva los SDK y versiones del proyecto: compile/target SDK 37, min SDK 26, Gradle 9.5.0 y AGP 9.3.3. El proyecto ya solicita JDK 25 para el daemon de Gradle; Android Studio puede descargarlo si no está disponible.
6. Selecciona el módulo **app**, un emulador o teléfono compatible y pulsa **Run**.
7. Si es una instalación nueva, completa el formulario original. En Inicio, pulsa Agregar vehículo y registra tu auto. Después agrega mantenimientos.

No desinstales la aplicación para actualizar si deseas conservar los datos. Una actualización con la misma firma mantiene Room y ejecuta la migración necesaria. Si tu dispositivo tiene una compilación firmada con otra clave, Android no permite actualizarla con este APK; compila desde tu Android Studio con la firma original.

## Archivos y dependencias

El repositorio contiene los archivos completos. No debes crear otra entidad ni otro RecyclerView/Adapter. El inventario de archivos de esta entrega aparece en `ARCHIVOS_DEV_FINAL.txt`.

Se declaran explícitamente `lifecycle-viewmodel-ktx` y `lifecycle-viewmodel-savedstate` usando la versión Lifecycle **2.11.0** ya utilizada. Son las utilidades del ViewModel y SavedStateHandle; no se incorpora Compose, otra base de datos ni otro framework. Room, Material, RecyclerView y ViewBinding se conservan.

Si decides copiar manualmente, reemplaza cada archivo modificado completo y crea los nuevos en sus rutas exactas según el inventario; sincroniza Gradle después. Elimina solamente el duplicado que estaba fuera del source set en `app/src:main/java/com/example/carcareformularioregistro/data/Maintenance.kt`, si tu copia lo contiene.

## Prueba manual del buscador

Crea cinco servicios, por ejemplo aceite Realizado (120000 km, Taller López), aceite Próximo, frenos Próximo, batería Realizado y afinación Pendiente.

| Caso | Acción | Resultado esperado |
| --- | --- | --- |
| 1 | Todos, escribir `aceite` | Solo servicios de aceite |
| 2 | Escribir `ACEITE` | Mismos resultados |
| 3 | Escribir `ace` | Encuentra Cambio de aceite |
| 4 | Escribir `Taller López` | Servicios de ese taller |
| 5 | Escribir `120000` | Servicio con ese kilometraje |
| 6 | Próximos y `aceite` | Solo aceite Próximo |
| 7 | Pulsar X | Todos los registros de la pestaña actual |
| 8 | Escribir `zzzinexistente` | Mensaje claro sin resultados |
| 9 | Cerrar y volver a abrir la app | Los mantenimientos siguen almacenados |

Prueba además editar un Próximo sin cambiar su estado, marcarlo Realizado, eliminarlo y cambiar de pestaña varias veces. Abre Inicio, Historial, Gastos, Recordatorios y Perfil. En un teléfono estrecho, revisa que los botones y la navegación no se monten unos sobre otros.

## Ejecutar pruebas

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

Alternativa para las pruebas instrumentadas si el componente UTP de Gradle no está disponible en caché:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.example.carcareformularioregistro.test/androidx.test.runner.AndroidJUnitRunner
```

Las pruebas de migración utilizan una base separada. Las de interfaz crean registros identificables y eliminan solo sus propios registros, sin limpiar `carcare.db`.

## Límites existentes

La aplicación maneja un perfil y un vehículo principal locales; no implementa autenticación multiusuario. El buscador trabaja en memoria sobre la lista Room, apropiado para este proyecto universitario. Los recordatorios se programan aproximadamente a las 09:00 de la fecha indicada; Android puede retrasarlos y necesitan permiso de notificaciones. Si se crea o reactiva un recordatorio cuya hora ya pasó, se programa para el siguiente minuto. No se añadió reprogramación automática de alarmas tras reiniciar el dispositivo; los registros sí persisten.

Referencias de las APIs usadas: [coroutines y ciclo de vida](https://developer.android.com/topic/libraries/architecture/coroutines), [migraciones de Room](https://developer.android.com/training/data-storage/room/migrating-db-versions).
