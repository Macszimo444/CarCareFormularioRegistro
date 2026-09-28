# Horario por recordatorio

En `dev-final`, cada recordatorio con fecha permite elegir **Todo el día** (opción inicial) o una **hora específica**, seleccionada con el reloj de Android. Se eliminó el ajuste de hora global.

- **Todo el día:** no es una cita a una hora concreta. Si las notificaciones están activadas, el aviso se programa a las 09:00 del día elegido; la ayuda del formulario lo indica.
- **Hora específica:** se guarda la hora y los minutos del pendiente y se programa el aviso a esa misma hora, usando la zona horaria del teléfono. Android puede retrasar la notificación por ahorro de batería.
- **Por kilometraje:** no se muestra horario y se descarta cualquier hora previa al guardar. Se sigue comprobando al actualizar el odómetro.
- **Fecha o kilometraje:** puede incluir una hora para el objetivo por fecha; se mantiene la regla de lo que ocurra primero.
- Los avisos de una fecha/hora ya pasada mantienen el comportamiento anterior: se programan como pendientes para una próxima entrega, sin cambiar la fecha guardada.

El horario se ve en Recordatorios, en el detalle abierto desde Inicio y en la próxima revisión. Una cita de hoy cuya hora todavía no llega no se marca como alcanzada. Cambiar solo el título no repite un aviso entregado; cambiar la hora sí define un objetivo nuevo.

Los recordatorios creados desde un mantenimiento empiezan como Todo el día y se les puede asignar hora desde Recordatorios. Editar el mantenimiento conserva la hora de su aviso vinculado; cambiarlo a solo kilometraje la elimina.

## Datos y respaldos

Room migra de versión 4 a 5 añadiendo `dueTime` nullable a `reminders`. Los avisos anteriores quedan como Todo el día, con fecha, kilometraje, estado y vínculos intactos. La antigua preferencia de hora global deja de utilizarse. Se conservan los indicadores de avisos ya entregados para no repetirlos solo por actualizar la app.

Los nuevos respaldos usan formato 2 e incluyen el horario. La app también importa respaldos de formato 1, cuyos recordatorios se recuperan como Todo el día. Una app anterior no debe usarse para restaurar un respaldo nuevo.

No hay dependencias nuevas ni permisos adicionales. Las alarmas continúan siendo inexactas, conforme al funcionamiento anterior de Android; no se presenta esta función como un calendario de citas con entrega garantizada al minuto.

## Uso

1. Abre Recordatorios y crea o edita un pendiente.
2. Selecciona Por fecha o Fecha o kilometraje y elige la fecha.
3. Deja Todo el día activado, o desactívalo y pulsa Hora del pendiente para elegir hora y minutos.
4. Guarda. Comprueba el horario en la tarjeta del recordatorio.

## Validación específica

- Compilación de la aplicación y APK de pruebas.
- 9 pruebas unitarias de horarios y próximas revisiones: selección de hora, medianoche, formato inválido, kilometraje sin alarma por fecha, zona horaria, orden y condición de cita alcanzada.
- 7 casos instrumentados: guardar horario y recrear pantalla, volver a Todo el día, cambiar a kilometraje, conservar el horario al editar el servicio vinculado, migrar una base versión 4, respaldo con horario, respaldo antiguo y marcas de notificación entregada (algunos casos cubren varias acciones).
- Revisión visual del selector integrado en el formulario.

Se actualiza también la copia `dev-final` del Escritorio usada por Android Studio para evitar ejecutar una versión anterior contra la base de datos nueva. No es necesario borrar ni reinstalar desde cero la aplicación.
