# Kilometraje sin capturas repetidas

Actualización sobre `dev-final`, conservando las entidades, DAO, Room, formularios y navegación existentes.

## Comportamiento

- **Inicio:** es el lugar para actualizar la lectura actual del coche. Una explicación indica que sirve para calcular los kilómetros restantes para las revisiones. CarCare no mide recorridos automáticamente.
- **Vehículo nuevo:** solicita la lectura inicial una vez. **Editar vehículo:** muestra la lectura como información y orienta a Inicio para actualizarla. Guardar marca/modelo y otros datos conserva la lectura más reciente de Room, aunque haya cambiado después de abrir el formulario.
- **Mantenimiento nuevo:** usa la lectura guardada del coche y la muestra como resumen. `Cambiar kilometraje del servicio` permite introducir la lectura de un servicio anterior sin modificar el odómetro actual. Al editar un servicio se conserva su lectura histórica, no se sustituye por la del coche.
- **Próxima revisión y recordatorios:** selector `Por fecha`, `Por kilometraje` o `Fecha o kilometraje`. Solo se muestran los campos correspondientes. Fecha es la opción inicial; los registros existentes recuperan automáticamente su modalidad según sus datos.
- **Fecha o kilometraje:** se capturan ambos objetivos y se usa lo que ocurra primero. En el mantenimiento se puede dejar la próxima revisión completamente vacía si no se solicita un recordatorio.
- Al cambiar de modalidad se conservan temporalmente los campos como borrador, pero solo se guardan los objetivos de la opción elegida. Por ejemplo, guardar `Por fecha` elimina el objetivo en kilómetros del registro y de su mantenimiento vinculado, cuando existe.
- La selección y la edición de la lectura del servicio sobreviven a la recreación de pantalla. Se mantienen las validaciones y las operaciones de crear, editar, desvincular y eliminar recordatorios.

No se necesitan dependencias nuevas ni migraciones de base de datos. Los gastos no solicitan kilometraje.

## Comprobación de esta actualización

Compilación: `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --offline` completada.

Cuatro casos específicos instrumentados en el emulador, con registros de prueba aislados y limpieza de esos registros:

1. Crear un mantenimiento con kilometraje precargado, vincular un aviso por kilómetros, editarlo y desvincularlo conservando los avisos independientes.
2. Registrar una lectura histórica, recrear la pantalla y reabrir el mantenimiento: la lectura del servicio se conserva y la del vehículo permanece intacta.
3. Recuperar un aviso existente con ambos objetivos, cambiar de modalidad y recrear pantalla; guardar por fecha elimina el kilometraje oculto tanto del aviso como del mantenimiento vinculado.
4. Editar datos del vehículo mientras la lectura de Room se actualiza: guardar los datos generales conserva la lectura más reciente.

No se ejecutó lint ni la batería completa de pruebas en esta actualización.

## Instalar y probar

1. En Android Studio, selecciona `dev-final` y realiza Pull de `origin/dev-final`.
2. Sincroniza Gradle si Android Studio lo solicita. No agregues dependencias ni borres los datos de la aplicación.
3. Selecciona `app`, el emulador y pulsa ▶.
4. Agrega un mantenimiento: la lectura ya debe aparecer en el resumen. Prueba cambiarla para un servicio antiguo.
5. Abre un recordatorio: prueba las tres modalidades. `Por fecha` no debe mostrar ni exigir kilometraje.
6. Comprueba en Inicio que la lectura actual solo cambia cuando la actualizas expresamente.

Para reemplazo manual, usa los archivos completos de la entrega de kilometraje; conserva sus rutas relativas. `ReviewTargetMode.kt` y `mileage_flow_strings.xml` son archivos nuevos; los demás son reemplazos de los archivos existentes indicados en la entrega.
