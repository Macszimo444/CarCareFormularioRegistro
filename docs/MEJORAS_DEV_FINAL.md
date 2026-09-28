# CarCare · actualización de dev-final

Esta actualización continúa la integración anterior (`df84779`). Conserva el formulario de registro, Room, RecyclerView, ViewBinding, Material, el buscador y la navegación de CarCare. No añade dependencias ni cambia la paleta.

## Qué cambió

- **Inicio:** vehículo en uso, actualización rápida del odómetro, próxima revisión calculada con tus registros, resumen de gastos y acceso a recordatorios y orientación. Se retiraron el progreso fijo y la afirmación de que un coche sin registros estaba «al día».
- **Mis vehículos:** crear, consultar, editar, seleccionar, designar principal y eliminar. Marca, modelo y año forman el nombre si no escribes un apodo; placas y apodo son opcionales. Principal y seleccionado son conceptos distintos: elegir otro coche no cambia el principal. Se conserva la última selección.
- **Datos separados:** mantenimiento, búsqueda, historial, gastos, gráficos y recordatorios muestran el vehículo seleccionado. Un formulario abierto conserva su vehículo de origen aunque cambie la selección.
- **Mantenimiento:** etiquetas persistentes, ejemplos breves y secciones desplegables para taller/notas y próxima revisión. Esta última puede quedar vacía. La fecha de un servicio realizado no puede ser futura y la siguiente revisión debe ser posterior al servicio. Guardar un servicio no actualiza automáticamente el odómetro actual del coche.
- **Recordatorio vinculado:** desde el mantenimiento se puede guardar una próxima revisión y activar su aviso. Servicio y vínculo se guardan juntos. Editar conserva el mismo recordatorio; desmarcar elimina solo ese vínculo. Eliminar un mantenimiento elimina también su recordatorio vinculado. Los recordatorios independientes permanecen intactos.
- **Gastos:** alta, consulta, edición y eliminación con confirmación, importes positivos, categoría válida, fecha no futura y notas opcionales. Los gastos y los costos de mantenimiento siguen siendo registros separados: guardar un servicio no crea automáticamente un gasto.
- **Recordatorios:** crear, editar, activar/desactivar y eliminar; objetivos por fecha, kilometraje o ambos; cantidad real de días/km restantes. Configuración general de avisos y hora, acceso al permiso de Android y restauración después de reiniciar. Hay un solo control para agregar en cada categoría.
- **Perfil:** edición desde Perfil, nombres validados, teléfono/dirección opcionales desplegables. Teléfono de México: diez dígitos, con `+52` opcional; se normaliza para guardar y se formatea al escribir. No se añadió edad ni se pide una contraseña inexistente.
- **Privacidad:** explicación del almacenamiento local, permisos, enlaces y eliminación. Botón para borrar todos los datos, con confirmación explícita. Sin contacto ficticio, analítica ni servidor. Esta versión excluye sus archivos del respaldo automático y de la transferencia de Android.
- **Asistente guiado:** preguntas preparadas sin internet ni proveedor de IA. Incluye frenos, sobrecalentamiento, testigos, arranque, vibraciones y fugas; primero revisa señales de peligro. Orienta a revisión profesional, sin diagnosticar ni indicar reparaciones peligrosas. Permite volver, cambiar respuesta y reiniciar; conserva el recorrido durante recreación de la pantalla, sin historial permanente de consultas.
- **Guía de cuidados:** explica para qué sirven los servicios y cómo elegir su frecuencia. Distingue ejemplos de captura, revisiones preventivas generales y planes del fabricante; no aplica intervalos universales a todos los coches. Las fuentes se abren solo al tocarlas. Consulta [las fuentes y el alcance](SOURCES_GUIDANCE.md).

## Datos existentes y arquitectura

Room pasa de **v2 a v3** con `MIGRATION_2_3`; sigue disponible la ruta `1 → 2 → 3`. Se añaden únicamente `vehicles.isPrimary` y `reminders.maintenanceId`. No hay migración destructiva. El vehículo que antes se usaba por defecto (el de mayor ID existente) se convierte en principal.

Si alguna versión anterior guardó registros con un `vehicleId` inexistente, la migración crea un «Vehículo recuperado» con ese ID. Los registros siguen asociados; completa sus datos desde Mis vehículos. Los valores históricos de nombre, marca, modelo, año, kilometraje, placas, foto, costos, notas y fechas no se borran.

La versión anterior rellenaba la próxima fecha y el próximo kilometraje con los del servicio cuando se dejaban vacíos. Cuando ambos valores son exactamente iguales y no hay recordatorio vinculado, se presentan como un plan sin definir al editar y se omiten de las próximas acciones. No se reescriben esos datos hasta guardar el formulario.

`VehicleRepository` centraliza la selección y las operaciones por vehículo. Los DAO ofrecen `getForVehicleFlow(vehicleId)`. `MaintenanceRepository` observa al vehículo seleccionado; el `MaintenanceViewModel` conserva `StateFlow`, búsqueda parcial sin distinguir mayúsculas/acentos y combinación con pestañas, procesada fuera del hilo principal. Se reutiliza `MaintenanceAdapter` y el único RecyclerView de la pantalla.

La pestaña **Próximos** muestra el estado `Próximo`; **Realizados**, `Realizado`; **Todos** también incluye `Pendiente`. El historial reúne los realizados del vehículo seleccionado. Su resumen corresponde al año actual y su lista incluye todos los años.

Eliminar un vehículo requiere confirmar cuántos mantenimientos, gastos y recordatorios se borrarán. La operación se realiza en una transacción y cancela sus avisos. Las bajas no afectan a otro vehículo.

## Alcance de los avisos

CarCare usa exclusivamente las fechas y lecturas introducidas. No lee el odómetro ni el estado del vehículo. Los avisos de kilometraje se evalúan cuando actualizas el odómetro; no son seguimiento en tiempo real del coche. Un mismo objetivo se notifica una vez, mientras no cambie su fecha o kilometraje.

Las alarmas por fecha son inexactas: Android puede retrasarlas por ahorro de energía o sus ajustes. Se requiere permiso de notificaciones. Desactivar avisos no borra datos y un aviso no marca el mantenimiento como realizado. No se solicita acceso a alarmas exactas.

## Abrir esta versión en Android Studio

1. Guarda cualquier trabajo local que tengas y actualiza la rama `dev-final` desde Git → Pull. Desde terminal, con el repositorio abierto:
   ```bash
   git fetch origin
   git switch dev-final
   git pull --ff-only origin dev-final
   ```
2. Abre la carpeta que contiene `settings.gradle.kts`, pulsa **Sync Project with Gradle Files** y espera a que termine.
3. No necesitas añadir dependencias ni reemplazar archivos manualmente al usar Git. El proyecto conserva su configuración SDK 37 / Gradle 9.5 / JDK 25; usa el SDK y el JDK configurados para el proyecto.
4. Selecciona la configuración **app**, el dispositivo o emulador y pulsa **▶ Run**.
5. Conserva la instalación anterior si quieres comprobar la migración. No desinstales ni borres el almacenamiento: eso eliminaría tus registros.

Si eliges reemplazar archivos manualmente, el inventario `ARCHIVOS_MEJORAS_DEV_FINAL.txt` distingue archivos modificados y nuevos. El paquete de entrega incluye el contenido completo de cada archivo, el proyecto y el APK de depuración. Al usar Git no hace falta copiar esos archivos uno por uno.

## Recorrido sencillo para probar

1. En Inicio abre Mis vehículos, crea dos coches sin placas ni apodo y elige uno como principal. Selecciona el otro y verifica que el principal no cambió.
2. Añade un gasto y un mantenimiento para cada coche. Cambia la selección y comprueba que sus listas y totales estén separados.
3. Busca `aceite`, `ACEITE`, `ace`, un taller, una fecha y un kilometraje; combina el texto con Próximos. Usa la X y busca un término inexistente para revisar el estado vacío.
4. Abre un mantenimiento, despliega Próxima revisión y activa su recordatorio. Guarda, edita su objetivo y confirma que existe un solo recordatorio. Desmárcalo y comprueba que no desaparezca un recordatorio independiente.
5. Actualiza el kilometraje desde Inicio y revisa la distancia restante. Una lectura menor exige confirmar que se trata de una corrección.
6. En Recordatorios abre Configurar avisos, habilita las notificaciones del dispositivo y elige la hora. Revisa también la opción de desactivar avisos.
7. Abre el asistente desde Inicio, recorre las preguntas, vuelve para cambiar una respuesta y prueba una señal de peligro. Lee las ayudas del kilometraje y las frecuencias en la guía.
8. Edita el perfil sin teléfono/dirección, prueba datos inválidos y vuelve a abrir la app. Tus registros deben permanecer guardados.
9. Para comprobar una baja de vehículo, usa uno de prueba: lee la confirmación y comprueba que los registros del otro coche permanezcan. El borrado total de Perfil es irreversible y elimina todos los datos de esta instalación.
