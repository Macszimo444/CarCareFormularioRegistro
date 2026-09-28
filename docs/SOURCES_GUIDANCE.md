# Guía, asistente y privacidad de CarCare

Contenido incorporado el 27 de septiembre de 2026. La aplicación incluye el texto y el árbol de preguntas: no descarga respuestas y no llama a servicios de inteligencia artificial.

## Alcance y decisiones

- Seis recorridos: frenos, temperatura, testigos, arranque, vibraciones y posibles fugas.
- Todos comienzan preguntando por riesgo inmediato. Humo, fuego, olor intenso a combustible, pérdida de frenado/dirección y duda sobre peligro llevan a detener el uso y solicitar asistencia; el resultado no recomienda conducir al taller.
- Las preguntas se refieren a señales ya observadas. No se pide reproducir una falla, tocar líquidos, manipular una batería ni hacer reparaciones.
- Una señal crítica detectada después vuelve a escalar la orientación aunque la primera respuesta haya indicado que no había peligro.
- No se diagnostica una avería, recomienda comprar una pieza ni garantiza que sea seguro conducir.
- No se establece un intervalo de mantenimiento universal. La guía remite al plan de la versión concreta del vehículo, su motor, uso y manual.
- Los manuales enlazados son referencias de contenido, no se presentan como el manual del vehículo del usuario. Las precauciones conservadoras y el árbol de preguntas son decisiones de producto; no una reproducción de procedimientos de fabricante.
- Los enlaces tienen título y se abren en una aplicación externa solo cuando el usuario los pulsa. No reciben registros, respuestas, identificadores ni parámetros personales de CarCare.
- La descripción de privacidad documenta el producto local, no declara un cumplimiento legal completo ni inventa un responsable o canal de soporte. Antes de distribuirlo como servicio real corresponde revisarla con la identidad y operación de sus responsables.

## Fuentes primarias verificadas

1. [Honda — Maintenance Minder System](https://www.hondainfocenter.com/Shared-Technologies/Comfort-and-Convenience/Maintenance-Minder-System-All/). Respalda que ciertos modelos determinan mantenimiento según uso y condiciones, y no únicamente una distancia fija.
2. [Honda — Servicios del Accord 2024](https://techinfo.honda.com/rjanisis/pubs/OM/AH/A30A2424IOM/enu/details/131236047-12935.html). Ejemplo concreto de servicios y condiciones especiales. No se trasladan sus cifras o intervalos a otros vehículos.
3. [NHTSA — Tire Safety](https://www.nhtsa.gov/vehicle-safety/tires). Referencia para etiqueta/manual de presión, inspección de daños, vibraciones y atención profesional. CarCare no proporciona presiones universales.
4. [Ford — Warning Lamps and Indicators](https://www.fordservicecontent.com/Ford_Content/vdirsnet/OwnerManual/Home/Content?ProcUid=G1545681&Uid=G1532426&buildtype=web&countryCode=USA&div=f&languageCode=en&moidRef=G539493&userMarket=GBR&vFilteringEnabled=False&variantid=2673). Referencia sobre avisos de frenado, presión de aceite y temperatura; se evita asumir que el color de un testigo es suficiente para interpretarlo.
5. [Honda — Testigo del motor, HR-V 2025](https://techinfo.honda.com/rjanisis/pubs/om/ah/a3v02525iom/enu/details/131237047-15984.html). Referencia para distinguir aviso fijo y parpadeante. El asistente remite a asistencia; no adopta las instrucciones específicas de conducción del manual de ese modelo.
6. [Honda — Sobrecalentamiento, Pilot 2026](https://techinfo.honda.com/rjanisis/pubs/OM/AH/AT902626IOM/enu/details/131293047-15886.html). Referencia para riesgo de vapor y fluidos calientes. No se ofrece procedimiento de apertura, llenado o reparación.
7. [USFA — Vehicle Fire Safety](https://www.usfa.fema.gov/prevention/vehicle-fires/). Referencia para detenerse cuando sea seguro, apagar una vez parado, alejarse y solicitar emergencias; no abrir el cofre ante fuego ni intentar combatirlo.

Las explicaciones sobre campos, historial y datos locales provienen de la implementación de CarCare, no de estas fuentes mecánicas.

## Integración

Actividad: `com.example.carcareformularioregistro.ui.GuidanceActivity`.

```kotlin
startActivity(Intent(requireContext(), GuidanceActivity::class.java)
    .putExtra(GuidanceActivity.EXTRA_MODE, GuidanceActivity.MODE_ASSISTANT))
```

Modos: `MODE_ASSISTANT`, `MODE_GUIDE`, `MODE_HELP`, `MODE_PRIVACY`. La actividad no debe exportarse. No requiere dependencias ni permisos de red adicionales.

`GuidanceFlow` contiene el árbol determinista independiente de Android. Los ID estables de las opciones permiten reconstruir el recorrido. `GuidanceViewModel` utiliza `SavedStateHandle` para rotación y recreación del proceso por Android. Al cambiar una respuesta, las respuestas posteriores se descartan. El botón de regresar de Android recorre una respuesta atrás; la flecha del encabezado vuelve a CarCare. «Comenzar otra consulta» elimina el recorrido actual.

No se escribe un historial de consultas en Room ni en preferencias. Un inicio nuevo de la actividad comienza en seguridad; una actividad restaurada por Android puede recuperar temporalmente sus respuestas. La guía y la ayuda usan apartados expandibles que conservan su estado durante la recreación.

## Verificación

`GuidanceFlowTest` incluye diez pruebas relevantes: prioridad de señales críticas, seis temas accesibles, aparición tardía de riesgo, cambiar respuestas, reconstrucción de estado, descarte de estado inválido, no aceptar respuestas tras un resultado, reinicio, terminación de todo el árbol y referencias a fuentes existentes.

Comprobaciones manuales recomendadas:

- Elegir humo/fuego y verificar que la orientación inmediata aparece sin preguntas adicionales.
- Elegir sin peligro → testigo → motor → parpadea y comprobar el resultado de atención inmediata.
- Retroceder, elegir fijo y comprobar que no queda el resultado anterior.
- Rotar en una pregunta intermedia y confirmar que conserva el recorrido.
- Abrir cada uno de los seis temas, volver a empezar y salir mediante la flecha del encabezado.
- Activar modo avión: preguntas, guía, ayuda y privacidad siguen disponibles; solo los enlaces externos necesitan conexión.
- Probar fuente externa sin navegador: muestra un mensaje sin cerrar la aplicación.

Estas pruebas verifican comportamiento del software. No representan validación clínica, certificación mecánica ni aprobación de fabricante del contenido orientativo.
