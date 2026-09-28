# Revisiones realizadas, comprobantes, respaldos e historial PDF

Actualización sobre `dev-final`, posterior a la simplificación del kilometraje (`0498e80`). Se mantienen los formularios, Room, RecyclerView, navegación y tema oscuro existentes.

## Qué cambia

### Inicio y revisiones

Inicio conserva el cálculo real de días y kilómetros restantes, añade el número de servicios por realizar y abre la revisión concreta desde su tarjeta. No calcula diagnósticos ni intervalos mecánicos nuevos.

En Recordatorios, **Marcar como realizada** abre el formulario de mantenimiento con servicio, fecha actual y lectura guardada precargados. El usuario confirma la fecha real, costo y demás datos antes de guardar:

- Si el recordatorio está vinculado a un mantenimiento pendiente, se actualiza ese mismo registro.
- Si corresponde a la próxima revisión de un mantenimiento ya realizado, se conserva el servicio anterior y se registra uno nuevo. Solo se resuelven los objetivos de próxima revisión del servicio anterior.
- Si el recordatorio era independiente, se crea un mantenimiento para su vehículo.
- Guardar el servicio y retirar el recordatorio es una sola transacción. Cancelar o fallar al guardar no resuelve el aviso. Volver a intentar resolver un aviso ya retirado no crea otro mantenimiento.
- El servicio queda como Realizado y aparece en Historial. Desde el mismo formulario se puede programar una próxima revisión opcional.

### Comprobantes

Cada mantenimiento admite **un comprobante opcional**: JPG, PNG, WebP o PDF sin contraseña, hasta 10 MB. Para varias páginas, se puede adjuntar un solo PDF.

`Adjuntar comprobante` abre el selector de archivos de Android. La app copia el archivo a su almacenamiento privado, valida el formato y permite verlo, reemplazarlo o quitarlo. La selección no se confirma en el registro hasta guardar el formulario. El historial permite abrir un servicio para consultar su comprobante. No se añaden permisos de cámara ni acceso general a todos los archivos.

### Respaldo y restauración

En **Perfil → Respaldo y restauración** se guarda un ZIP mediante el selector de ubicación de Android. También se puede restaurar desde el registro inicial, sin crear antes un perfil nuevo.

Incluye perfiles locales, vehículos, mantenimientos, gastos, recordatorios, relaciones entre ellos y los comprobantes adjuntos a servicios. No exporta permisos del sistema, preferencias de notificación, URI antiguas de fotos del vehículo ni la sesión del teléfono.

Antes de restaurar se muestra un resumen y se confirma la incorporación de los registros. Se conservan los datos actuales: los vehículos y registros importados reciben identificadores nuevos; el perfil existente permanece, y en una instalación sin perfil se recupera el del respaldo. Si ya existe un vehículo principal, se conserva.

El mismo archivo no se restaura dos veces en esa instalación. Respaldos **diferentes** del mismo coche pueden añadir copias adicionales: no se intenta adivinar si son la misma historia o mezclar cambios por nombre. El formato tiene versión, valida referencias y rutas internas y limita el archivo a 128 MB y 20,000 registros. Un respaldo inválido no escribe datos parciales. Si falta un comprobante al exportar, se informa el fallo para no entregar una copia aparentemente completa.

El ZIP no tiene contraseña. El usuario decide dónde guardarlo. No se crean copias automáticas en la nube. Los archivos exportados siguen existiendo fuera de la app aunque se borren sus datos locales.

### Historial PDF

**Historial → Guardar historial en PDF** exporta solo los servicios realizados del vehículo seleccionado: nombre del coche, lectura, servicio, fecha, kilometraje, costo, taller y notas, con total de servicios e importe. Ajusta los textos largos y crea páginas adicionales cuando se necesitan.

No incluye perfil, placas ni las imágenes/PDF de los comprobantes; indica si hay un comprobante disponible en CarCare. No envía mensajes a terceros. El usuario elige dónde guardar el documento y puede compartirlo posteriormente desde su gestor de archivos.

### Ayudas

Los campos conservan una explicación corta. Los detalles sobre la lectura del servicio y las modalidades de revisión se consultan mediante **¿Qué significa?**. Se actualizan la ayuda y la información de privacidad para describir adjuntos y exportaciones.

## Archivos y compatibilidad

Se reutilizan `AddMaintenanceActivity`, `RecordatoriosActivity`, `HistorialActivity`, sus adaptadores, `InicioFragment`, `PerfilFragment` y `RegistroActivity`.

Archivos nuevos principales:

- `data/ReviewCompletion.kt`: consumo transaccional del recordatorio.
- `data/BackupArchive.kt`, `ImportedBackup.kt`: archivo portable e identificación de respaldos importados.
- `utils/ReceiptStore.kt`: almacenamiento y apertura de comprobantes.
- `utils/HistoryPdf.kt`: generación nativa del PDF.
- `ui/BackupActivity.kt` y `activity_backup.xml`: interfaz de respaldo/restauración y estado que sobrevive a recreación.
- `res/xml/shared_files.xml`: acceso temporal al comprobante elegido mediante FileProvider.
- `res/values/next_improvements.xml`: textos de la actualización.

Room pasa de **3 a 4** mediante migración: añade `receipt` nullable a `maintenances` y una tabla que identifica los archivos ya importados. Se conservan las migraciones desde versiones 1 y 2. No hay migración destructiva ni dependencias nuevas.

## Instalar y probar

1. En Android Studio, selecciona `dev-final` y haz Pull de `origin/dev-final`.
2. Sincroniza Gradle si lo solicita. No reemplaces el proyecto por uno nuevo ni borres los datos de la app.
3. Selecciona `app`, tu emulador y pulsa ▶. Room actualiza la base de datos automáticamente.
4. En Recordatorios, pulsa Marcar como realizada, revisa el formulario y guarda. Confirma que el aviso desaparece y el servicio aparece en Historial.
5. Abre un mantenimiento, adjunta un comprobante y guarda. Reabre el servicio desde Historial para verlo.
6. Exporta el historial a PDF y ábrelo con tu visor.
7. Desde Perfil guarda un respaldo. Para probar la recuperación sin crear copias en tu instalación habitual, usa otro emulador y restaura desde el registro inicial. Revisa el vehículo, sus servicios y comprobantes.
8. Intenta importar otra vez el mismo archivo: debe indicar que ya fue restaurado.

Para reemplazo manual, la entrega contiene el código completo de los archivos modificados y nuevos con sus rutas. La actualización debe aplicarse completa, incluida la migración, el manifiesto y los recursos.

## Validación de esta actualización

Compilación de aplicación y pruebas instrumentadas con `:app:assembleDebug :app:assembleDebugAndroidTest --offline`. Se verificaron específicamente:

- Migraciones desde versiones 1, 2 y 3 y reapertura de datos: 5 casos.
- Respaldo/restauración con comprobantes, identificadores remapeados, perfil existente y rechazo del mismo archivo; archivo inválido; transacción de revisión y reversión ante fallo; PDF con notas largas: 4 casos.
- Formularios de mantenimiento y recordatorios, persistencia al recrear, lectura histórica y actual, los tres tipos de resolución de revisión y quitar comprobante: 7 casos.
- Pantalla de respaldo y recreación: 1 caso.

17 casos instrumentados distintos comprobados durante la actualización. Se revisó visualmente el PDF de ejemplo de cinco páginas y la pantalla de respaldo. No se ejecutó lint ni la batería completa de pruebas ajenas a estas funciones.
