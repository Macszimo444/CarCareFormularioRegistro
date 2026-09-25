# Validación de dev-final

Fecha: 25 de septiembre de 2026.

## Resultados ejecutados

- `:app:assembleDebug`: **BUILD SUCCESSFUL**. APK debug generado.
- `:app:testDebugUnitTest`: **34 pruebas, 0 fallos, 0 errores**.
- `:app:lintDebug`: **BUILD SUCCESSFUL**, sin errores. Permanecen advertencias no bloqueantes de recursos, estilo y accesibilidad; no se ocultaron con un baseline.
- `:app:assembleDebugAndroidTest`: **BUILD SUCCESSFUL**.
- Suite instrumentada completa mediante AndroidJUnitRunner en emulador: **OK (6 tests)**, 20.861 segundos.
- `git diff --check`: sin problemas.
- XML de recursos y AndroidManifest: parseo correcto.
- Inspección visual de Mantenimiento con resultados filtrados y estado vacío en el emulador: controles separados, texto legible, tema oscuro, estados y navegación visibles.

## Pruebas unitarias

| Clase | Cantidad | Cobertura |
| --- | ---: | --- |
| `MaintenanceSearchTest` | 22 | Búsqueda parcial, mayúsculas, acentos, taller, descripción, estado, kilometraje con/sin separadores, fechas, combinación de pestaña y texto, limpiar, sin resultados, lista vacía y orden |
| `FormValidationTest` | 3 | Fechas válidas/inválidas y bisiestos, kilometraje, importes, negativos y desbordamientos |
| `StatisticsCalculatorTest` | 8 | Mes/año actual, comparación con mes anterior, cambio de año, fechas heredadas e historial anual |
| `ExampleUnitTest` | 1 | Prueba de ejemplo original del repositorio |

## Pruebas instrumentadas

| Clase | Cantidad | Cobertura |
| --- | ---: | --- |
| `AppDatabaseMigrationTest` | 2 | Migración v1 de master a v2 sin perder usuarios, esquema validado por Room, IDs, instalación vacía, actualización de perfil sin duplicarlo y datos que sobreviven al cierre/reapertura de la base |
| `MaintenanceSearchUiTest` | 2 | Escritura en EditText, filtros combinados, X real de Material, mensaje vacío, cambios en Room, recreación, navegación y formulario real de editar/guardar/eliminar |
| `RegistrationUiTest` | 1 | Campos obligatorios, registro→Inicio, reanudación sin duplicar usuario, navegación a Gastos/Historial/Recordatorios/Perfil, cerrar y continuar el perfil local |
| `ExampleInstrumentedTest` | 1 | Prueba de contexto original del repositorio |

Las pruebas de migración usan una base separada. Las pruebas UI preservan los datos existentes: crean registros identificables, eliminan solamente esos registros y restauran perfil y sesión cuando corresponde. Se hizo además un respaldo local del emulador antes de instalar la integración.

## Correspondencia con los nueve casos solicitados

Los casos 1–8 están cubiertos por pruebas unitarias y el recorrido UI real: aceite, ACEITE, ace, taller, kilometraje, Próximos + aceite, limpiar conservando pestaña y término inexistente. El caso 9 se comprueba cerrando y reabriendo una base Room en disco, verificando el mantenimiento persistido. También se comprobó la recreación de la Activity manteniendo texto y pestaña.

## Cómo se ejecutó AndroidJUnitRunner

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.example.carcareformularioregistro.test/androidx.test.runner.AndroidJUnitRunner
```

`connectedDebugAndroidTest --offline` no pudo resolver un componente UTP que no estaba en la caché de Gradle. Por eso se ejecutaron los mismos APK de pruebas directamente con ADB y AndroidJUnitRunner; las pruebas sí se ejecutaron en Android, no se sustituyeron por una revisión conceptual.

## Límites de esta validación

No se probó cada modelo de teléfono ni la entrega de notificaciones después de un reinicio del dispositivo. La aplicación sigue siendo de perfil local, sin autenticación remota ni aislamiento multiusuario. Los recordatorios dependen de los permisos y de la programación inexacta de Android. Estos límites no afectan al almacenamiento ni al buscador implementado.
