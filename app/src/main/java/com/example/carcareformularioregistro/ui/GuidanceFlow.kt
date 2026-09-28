package com.example.carcareformularioregistro.ui

/** Offline decision tree. Answers are stable IDs so saved UI state never depends on translated text. */
object GuidanceFlow {
    enum class Attention { INFORMATION, REVIEW, STOP }

    data class Choice(val id: String, val label: String, val next: String)
    data class Step(
        val id: String,
        val title: String,
        val body: String,
        val choices: List<Choice> = emptyList(),
        val attention: Attention = Attention.INFORMATION,
        val sourceIds: List<String> = emptyList()
    ) {
        val isResult get() = choices.isEmpty()
    }
    data class Answered(val step: Step, val choice: Choice)
    data class Conversation(val answered: List<Answered>, val current: Step) {
        val answerIds get() = answered.map { it.choice.id }
    }

    private fun choice(id: String, label: String, next: String) = Choice(id, label, next)
    private fun result(id: String, title: String, body: String, attention: Attention, vararg sources: String) =
        Step(id, title, body, attention = attention, sourceIds = sources.toList())

    val steps: Map<String, Step> = listOf(
        Step("safety", "Primero, tu seguridad", "Usa este asistente solo con el vehículo estacionado en un lugar seguro. ¿Has notado alguna de estas señales?", listOf(
            choice("fire", "Humo, fuego u olor intenso a combustible", "fire_result"),
            choice("control", "Pérdida de frenado o dificultad para controlar la dirección", "stop_result"),
            choice("uncertain", "No sé si hay peligro inmediato", "uncertain_result"),
            choice("none", "Ninguna de esas señales", "topic")
        )),
        Step("topic", "¿Qué has observado?", "Elige lo que más se parece a tu caso. No hagas pruebas de conducción para responder.", listOf(
            choice("brakes", "Ruido o cambios al frenar", "brakes"),
            choice("heat", "Temperatura alta del motor", "heat"),
            choice("lamp", "Un testigo en el tablero", "lamp"),
            choice("start", "Dificultad para encender", "start"),
            choice("vibration", "Vibraciones o movimientos extraños", "vibration"),
            choice("leak", "Gotas o una posible fuga", "leak")
        )),
        Step("brakes", "¿Qué cambió al frenar?", "Piensa en lo que ya observaste, sin volver a circular para comprobarlo.", listOf(
            choice("brake_control", "Frena menos, el pedal se hunde o se desvía mucho", "stop_result"),
            choice("brake_noise", "Hay ruido o vibración, sin ese cambio evidente", "brake_when"),
            choice("brake_unknown", "No puedo distinguirlo", "uncertain_result")
        )),
        Step("brake_when", "¿Cuándo aparece?", "Esta información ayuda al taller a reproducir el síntoma durante una revisión controlada.", listOf(
            choice("brake_repeat", "Se repite al frenar", "brake_review"),
            choice("brake_once", "Solo lo noté una vez", "brake_review"),
            choice("brake_when_unknown", "No estoy seguro", "brake_review")
        )),
        Step("heat", "¿Qué señal de temperatura viste?", "No abras el depósito ni el radiador caliente y no toques componentes del motor.", listOf(
            choice("heat_now", "Indicador alto, aviso de temperatura o vapor", "heat_stop"),
            choice("heat_before", "Ocurrió antes y luego desapareció", "heat_review"),
            choice("heat_unknown", "No identifico bien el indicador", "unknown_lamp")
        )),
        Step("lamp", "¿Qué testigo reconoces?", "El símbolo y el mensaje exactos importan. El color por sí solo no basta; consulta el manual de tu modelo.", listOf(
            choice("lamp_critical", "Presión de aceite, temperatura o frenos", "lamp_stop"),
            choice("lamp_engine", "Motor / Check engine", "engine_lamp"),
            choice("lamp_tire", "Presión de llantas", "tire_review"),
            choice("lamp_other", "Otro o no lo identifico", "unknown_lamp")
        )),
        Step("engine_lamp", "¿Cómo se presenta?", "No reinicies ni borres avisos para ocultarlos; conserva el mensaje para el taller.", listOf(
            choice("engine_flashes", "Parpadea o hay pérdida de potencia / tirones", "engine_stop"),
            choice("engine_steady", "Permanece fijo, sin esos síntomas", "engine_review"),
            choice("engine_unknown", "No estoy seguro", "unknown_lamp")
        )),
        Step("start", "¿Qué ocurrió al intentar encender?", "No hace falta repetir el intento ni manipular cables para responder.", listOf(
            choice("start_slow", "Gira lentamente o hace clics", "start_review"),
            choice("start_normal", "Gira, pero no enciende", "start_review"),
            choice("start_silent", "No hace nada o no sé distinguirlo", "start_review")
        )),
        Step("vibration", "¿Cómo es la vibración?", "No aceleres ni salgas a conducir para probarla.", listOf(
            choice("vibration_severe", "Es fuerte, apareció de golpe o dificulta el control", "stop_result"),
            choice("vibration_brake", "Aparece principalmente al frenar", "brake_when"),
            choice("vibration_other", "La noto al circular o con el motor encendido", "vibration_review"),
            choice("vibration_unknown", "No sé describirla", "uncertain_result")
        )),
        Step("leak", "¿Qué acompaña a las gotas?", "No pruebes el líquido, no lo toques ni te metas debajo del vehículo.", listOf(
            choice("leak_danger", "Olor a combustible, humo o fuego", "fire_result"),
            choice("leak_warning", "Hay un aviso de aceite, temperatura o frenos", "lamp_stop"),
            choice("leak_other", "Solo veo líquido; no sé qué es", "leak_review")
        )),
        result("fire_result", "Detén el uso y pide ayuda", "Si vas circulando, detente tan pronto como sea seguro y apaga el motor una vez parado. No vuelvas a conducir. Ante humo o fuego, sal del vehículo si puedes hacerlo con seguridad, aléjate del tránsito y llama a emergencias. No abras el cofre ni intentes apagar el fuego. Si hay olor intenso a combustible, evita reiniciar el motor y solicita asistencia profesional.", Attention.STOP, "fire"),
        result("stop_result", "No continúes conduciendo", "Una alteración del frenado, la dirección o el control requiere atención inmediata. Detente en cuanto sea seguro. Solicita asistencia vial y revisión profesional antes de volver a circular. No intentes una prueba en carretera para confirmar el problema.", Attention.STOP, "warnings", "tires"),
        result("uncertain_result", "Aclara el riesgo con un profesional", "Con esta información no se puede descartar una situación peligrosa. Mantén el vehículo detenido en un lugar seguro y consulta asistencia vial o un taller antes de conducir. Describe las señales que observaste; no necesitas conocer su causa.", Attention.STOP),
        result("brake_review", "Solicita una revisión de frenos", "Un sonido por sí solo no permite identificar una pieza dañada. Informa al taller si ocurrió una vez o se repite y si hubo vibración. No esperes al próximo intervalo de mantenimiento para consultar un cambio de frenado. Si aparece menor frenado o dificultad de control, detén el uso y pide asistencia.", Attention.REVIEW, "warnings"),
        result("heat_stop", "No sigas usando el motor", "Detente en un lugar seguro y apaga el motor una vez parado. Un aviso de temperatura necesita revisión; que después desaparezca no confirma que el problema terminó. No abras tapones calientes ni añadas líquidos como prueba. Solicita asistencia antes de continuar.", Attention.STOP, "heat"),
        result("heat_review", "Revisa el episodio de temperatura", "Comunica al taller qué mostró el tablero y cuándo ocurrió. Que la temperatura haya bajado no identifica ni resuelve la causa. Consulta antes de volver a usar el vehículo; si el aviso reaparece, detente en cuanto sea seguro y pide asistencia.", Attention.REVIEW, "heat"),
        result("lamp_stop", "Atiende el aviso antes de continuar", "Si el aviso de aceite, temperatura o frenos permanece con el motor en marcha, detente en cuanto sea seguro y no continúes conduciendo. Apaga el motor una vez detenido. Consulta el mensaje exacto en el manual y pide asistencia profesional. Este asistente no puede verificar el estado de esos sistemas.", Attention.STOP, "warnings"),
        result("engine_stop", "Solicita asistencia para el aviso del motor", "Un testigo que parpadea o va acompañado de tirones o pérdida de potencia requiere atención. Detente cuando sea seguro y consulta asistencia antes de continuar. No aceleres para probarlo ni borres el aviso.", Attention.STOP, "engine"),
        result("engine_review", "Consulta el manual y un taller", "Un aviso fijo puede tener causas diferentes y requiere evaluación. Anota el símbolo y el mensaje exactos para consultar al taller antes de continuar usando el vehículo. No se puede confirmar una avería ni autorizar la conducción desde esta pantalla.", Attention.REVIEW, "engine"),
        result("unknown_lamp", "Identifica primero el mensaje", "Consulta el apartado de testigos del manual específico de tu vehículo. No supongas que un color indica que es seguro circular. Si no puedes identificarlo, mantén el vehículo estacionado y pide orientación profesional; ante pérdida de control, humo o temperatura alta, solicita asistencia.", Attention.REVIEW, "warnings"),
        result("tire_review", "Revisa el aviso de llantas", "Consulta la presión especificada en la etiqueta del vehículo o en su manual. Un testigo no indica por sí solo cuál es la causa. Si hay daño visible, pérdida rápida de aire o cambios de control, no continúes conduciendo y pide asistencia. Para una evaluación, acude a un servicio de llantas.", Attention.REVIEW, "tires"),
        result("start_review", "Solicita una revisión del arranque", "Los clics o la dificultad para encender no confirman que debas cambiar la batería. Indica al taller qué escuchaste, qué luces aparecieron y si ocurrió antes. Evita intentos repetidos, puentes improvisados o manipular la batería. Si estás varado, solicita asistencia vial.", Attention.REVIEW),
        result("vibration_review", "Describe la vibración al taller", "Anota dónde la percibiste y en qué situación, sin volver a reproducirla conduciendo. Una vibración puede tener distintas causas; no permite indicar una reparación desde aquí. Solicita revisión profesional. Si aumenta de golpe o afecta el control, deja de circular y pide asistencia.", Attention.REVIEW, "tires"),
        result("leak_review", "Pide identificar el líquido", "El color o la ubicación de unas gotas no bastan para identificar una fuga. Describe al taller cuándo aparecen y si el vehículo mostró avisos. Evita tocar el líquido o entrar bajo el coche. Consulta antes de continuar; con olor a combustible, humo o avisos críticos, detén el uso y solicita asistencia.", Attention.REVIEW)
    ).associateBy { it.id }

    /** Invalid or outdated state is truncated at the first incompatible answer. */
    fun conversation(answerIds: List<String>): Conversation {
        val answered = mutableListOf<Answered>()
        var step = steps.getValue("safety")
        for (answerId in answerIds) {
            val choice = step.choices.firstOrNull { it.id == answerId } ?: break
            answered += Answered(step, choice)
            step = steps.getValue(choice.next)
        }
        return Conversation(answered, step)
    }

    fun answer(answerIds: List<String>, choiceId: String): List<String> {
        val state = conversation(answerIds)
        if (state.current.choices.none { it.id == choiceId }) return state.answerIds
        return state.answerIds + choiceId
    }

    fun back(answerIds: List<String>): List<String> = conversation(answerIds).answerIds.dropLast(1)
}
