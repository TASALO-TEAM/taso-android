package com.tasalo.android.domain

/** Un bloque del descargo: un titulo y sus parrafos. */
data class PrivacySection(val title: String, val lines: List<String>)

/**
 * Descargo de privacidad de TASALO Inventario (taso-inv, la pestana Tienda). Es el resumen de `docs/PRIVACIDAD.md`:
 * si cambia uno, cambia el otro. Lo redactado aqui sale de leer el codigo y las migraciones de taso-inv (v0.101.0.0), no de memoria.
 * Texto puro (sin Android) para poder probarlo en la JVM.
 */
object PrivacyNotice {
    const val UPDATED = "7 de noviembre de 2026"
    const val FULL_TEXT_URL = "$REPO_URL/blob/main/docs/PRIVACIDAD.md"

    const val INTRO =
        "TASALO Inventario (la pestaña Tienda) guarda algunos datos tuyos para poder atenderte. " +
            "Aquí está lo que se guarda de verdad, para qué y cómo manejarlo. Esto habla solo de la Tienda."

    val sections: List<PrivacySection> = listOf(
        PrivacySection(
            "Qué se guarda",
            listOf(
                "Tu cuenta: usuario, nombre, tu contacto si lo das, tus preferencias de apariencia y las fechas de creación y de último acceso. " +
                    "De la contraseña solo se guarda una huella cifrada, nunca la contraseña.",
                "Si entras con Google: el identificador de tu cuenta de Google, tu nombre y tu correo (queda como tu contacto). " +
                    "Si entras con Telegram: tu id de Telegram, tu nombre y tu @usuario.",
                "Tus pedidos: lo que pides, la fecha y el destino de entrega, las notas y el total, y la ficha de cliente con los datos de contacto y entrega que se hayan dado.",
                "Tus valoraciones (estrellas) y tus avisos. Si vinculas Telegram y no lo apagas, algunos avisos te llegan también por Telegram.",
                "Si haces un pedido por el bot de Telegram: el texto de tu mensaje y tu id de chat.",
                "Datos técnicos: por cada sesión, tu IP y el tipo de navegador; una cookie de sesión, necesaria para mantenerte dentro " +
                    "(caduca a los 14 días sin usarla o a los 30 como máximo); y los intentos fallidos de entrada (usuario e IP), durante 24 horas. " +
                    "El servidor web también lleva un registro de accesos (IP, navegador y dirección visitada) que se va renovando y guarda solo lo más reciente.",
            ),
        ),
        PrivacySection(
            "Para qué se usa",
            listOf(
                "Solo para atender tus pedidos, mantener tu sesión abierta, proteger la cuenta de abusos (límite de intentos) y llevar las cuentas del negocio. " +
                    "No hay publicidad ni perfiles, y no se vende ni se comparte con nadie más. " +
                    "Si entras con Google o con Telegram, esos servicios intervienen únicamente para comprobar que eres tú.",
            ),
        ),
        PrivacySection(
            "Quién lo ve",
            listOf(
                "El personal del negocio (administradores y empleados) ve tus pedidos y tus datos de contacto y entrega para atenderte. " +
                    "Las cuentas y su registro de actividad solo los ven los administradores. " +
                    "Los costos y las ganancias del negocio nunca se muestran a los clientes.",
            ),
        ),
        PrivacySection(
            "Cuánto tiempo",
            listOf(
                "Mientras tengas la cuenta. Se hace una copia de respaldo diaria de la base y se guardan las últimas 14: " +
                    "lo que borres puede seguir en esas copias hasta unos 14 días.",
                "Al eliminar tu cuenta, los pedidos y pagos se conservan sin tus datos personales, porque son parte de las cuentas del negocio.",
            ),
        ),
        PrivacySection(
            "Tus opciones",
            listOf(
                "En la Tienda, abre Mi cuenta y entra en «Privacidad y datos»: puedes descargar una copia de tus datos, apagar los avisos por Telegram (si lo tienes vinculado) y eliminar tu cuenta.",
                "Eliminar la cuenta borra tu cuenta, tus sesiones y tus avisos, quita tu nombre, tu contacto y las notas de tus pedidos, y deja tus valoraciones sin nombre. No se puede deshacer.",
                "Si eres parte del personal, pídele a un administrador que te dé de baja o corrija tus datos. " +
                    "Si no puedes hacerlo tú, pídelo al negocio por el mismo medio con el que haces tus pedidos.",
            ),
        ),
    )
}
