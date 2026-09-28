# Verificación de la actualización de dev-final

Fecha: 27 de septiembre de 2026, zona de Ciudad de México.

## Resultado

- APK de depuración y APK de pruebas: compilación correcta con el wrapper y dependencias del proyecto, sin añadir bibliotecas.
- **51 pruebas unitarias aprobadas:** búsqueda, filtros, formatos, estadísticas, prioridades de Inicio, perfil y decisiones del asistente.
- **22 casos instrumentados verificados:** migración, aislamiento por vehículo, asistente, buscador, perfil, gastos y recordatorios. La ejecución general terminó con 21 correctos y un fallo de desplazamiento en la propia prueba de gastos. Se corrigió el test para desplazar el botón real a la vista, sin cambiar producción ni relajar la aserción; la ejecución puntual posterior de sus dos casos terminó `OK (2 tests)`.
- `git diff --check` sin errores.

## Casos cubiertos

| Área | Verificación |
| --- | --- |
| Room 1 → 2 → 3 | Conservación de todos los perfiles y creación de las tablas de la aplicación |
| Room 2 → 3 | Conservación de campos y filas, principal inicial, recuperación de referencias a vehículos inexistentes y vínculo nullable |
| Datos por vehículo | Listas ordenadas de mantenimiento/gastos/recordatorios; eliminación de un vehículo sin tocar el otro; principal exclusivo y persistente |
| Buscador | Teclado real, `aceite`/`ACEITE`/`ace`, taller, kilometraje, fecha, pestañas combinadas, X, vacío, cambios de Room, recreación y navegación |
| Mantenimiento | Editar/eliminar desde el formulario; conservar vehículo, fecha, kilometraje y estado |
| Próxima revisión | Crear vínculo, editar el mismo recordatorio sin duplicarlo, quitar vínculo conservando plan y recordatorio independiente |
| Perfil | Obligatorios, contacto opcional, validación de nombre/teléfono, formato +52, edición, cancelar, continuar y cerrar perfil |
| Vehículos y gastos | Cambiar seleccionado sin modificar principal, persistencia, alta/edición/baja de gastos, borrador tras recreación y vehículo capturado al abrir el formulario |
| Asistente | Cuatro modos, señales de peligro, restauración de preguntas/resultados, cambiar respuesta, retroceder y reiniciar |
| Avisos | Preferencias persistentes y una entrega por objetivo, con archivos de preferencias exclusivos de prueba |

El emulador utilizado fue Pixel 9, Android API 36.1. Se conservó una copia temporal local antes de actualizar. La comparación de la base antes/después confirmó la migración v2 → v3 sin modificar el perfil ni el vehículo existentes; los datos de mantenimiento/gastos/recordatorios de las pruebas se crearon con identificadores exclusivos y se retiraron al terminar. Los escenarios con registros históricos y huérfanos se verificaron en bases temporales separadas.

## Correcciones encontradas durante la verificación

- La barra inferior de Material volvía a añadir el alto del teclado que MainActivity ya había reservado. Se centralizó el consumo de esos espacios para mantener visibles búsqueda, pestañas y resultados.
- El desplazamiento animado del asistente podía interceptar una respuesta rápida o usar posiciones anteriores al nuevo diseño. Ahora espera la medición de las tarjetas y desplaza directamente a la pregunta actual.
- Se conservaron las etiquetas de campos mientras se escribe, mediante un estilo específico de entradas con etiqueta.

## Comandos usados

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --offline
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.example.carcareformularioregistro.test/androidx.test.runner.AndroidJUnitRunner
```

La repetición puntual de gastos utilizó `-e class com.example.carcareformularioregistro.ui.VehiclesExpensesUiTest`. No es necesario que ejecutes estas pruebas para usar la app: sincroniza Gradle y pulsa Run.

Las notificaciones siguen sujetas al permiso del dispositivo y al aplazamiento de alarmas de Android. No se simuló un ciclo prolongado de ahorro de energía ni se probó la entrega en todas las marcas de teléfono. El borrado total de Perfil no se ejecutó sobre los datos originales del emulador.
