# Privacidad de TASALO Inventario

Última actualización: 7 de noviembre de 2026. Cubre **TASALO Inventario (taso-inv)**: la Tienda para clientes y la herramienta del personal, que la app Android muestra en la pestaña Tienda. No habla de las tasas, el blog ni del resto de la app.

Este texto sale de leer el código y las migraciones de taso-inv (v0.101.0.0), no de memoria. Resumen en lenguaje llano; la app muestra una versión corta en **Ajustes → Privacidad**.

## Qué se guarda

**Tu cuenta** (tabla `usuarios`)
- Usuario, nombre y rol (cliente, empleado o administrador).
- La contraseña **no se guarda**: solo una huella cifrada (hash).
- Contacto, si lo das. Si entras con Google o Telegram, se rellena solo (ver abajo).
- Preferencias de apariencia (tema, paleta, avatar), fecha de creación y de último acceso.
- Si eres personal: estado de la cuenta y, si hubo, motivo y fechas de una sanción.

**Si entras con Google:** el identificador estable de tu cuenta de Google, tu nombre y tu correo (queda como tu contacto). No se guarda tu foto.
**Si entras con Telegram:** tu id numérico de Telegram, tu nombre y tu @usuario (queda como tu contacto).

**Tus pedidos y tu ficha de cliente**
- Lo que pides, estado, fecha y destino de entrega, notas y total.
- La ficha de cliente (nombre, teléfono, Telegram, dirección, nota) con los datos de contacto y entrega que se hayan dado.
- Los costos y ganancias de cada pedido son del negocio y no se muestran a los clientes.

**Valoraciones y avisos**
- Tus estrellas sobre pedidos y productos.
- Los avisos que te llegan dentro de la tienda (se guardan los últimos 50 por persona) y, si vinculas Telegram y no lo apagas, algunos también por Telegram.

**Pedidos por el bot de Telegram:** el texto de tu mensaje y tu id de chat.

**Registro de actividad:** quién hizo qué sobre qué cuenta (cambios de rol, sanciones, bajas, vínculos con Telegram o Google) y las acciones sensibles del personal (por ejemplo, anular una compra).

**Datos técnicos**
- Cookie de sesión (necesaria para mantenerte dentro): HttpOnly y SameSite estricta; caduca a los 14 días sin usarla o a los 30 como máximo.
- Por cada sesión, tu IP y el tipo de navegador (hasta 200 caracteres).
- Intentos fallidos de entrada (usuario e IP): se borran a las 24 horas.
- Registro de accesos del servidor web (IP, navegador, dirección visitada): rota por tamaño y conserva solo lo más reciente (unos pocos MB).

**Si eres personal del negocio:** además puede haber una ficha de Personal (nombre, cargo, contacto, fecha de ingreso, nota), visible solo para administradores. Hoy no se guardan sueldos.

## Para qué se usa
Atender tus pedidos, mantener tu sesión, proteger las cuentas de abusos (límite de intentos) y llevar las cuentas del negocio. No hay publicidad ni perfiles, y no se vende ni se comparte con terceros. Google y Telegram solo intervienen, si los usas, para comprobar que eres tú al entrar.

## Quién lo ve
- **Personal del negocio** (administradores y empleados): tus pedidos y tus datos de contacto y entrega.
- **Solo administradores:** la gestión de cuentas, su registro de actividad y la ficha de Personal.
- **Nadie más.** Los costos y ganancias del negocio no se muestran a los clientes.

## Cuánto tiempo
- Mientras tengas la cuenta.
- Respaldo: cada día se copia la base de datos y se guardan las últimas 14 copias. Lo que borres puede seguir en esas copias hasta unos 14 días.
- Al eliminar tu cuenta, los pedidos y pagos se conservan **sin tus datos personales**, porque son parte de las cuentas del negocio.

## Tus opciones
**Clientes**, en la Tienda → Mi cuenta → «Privacidad y datos»:
- **Descargar mis datos:** un archivo JSON con tu cuenta, tus pedidos y tus valoraciones (sin la huella de la contraseña, sin los ids de Telegram o Google y sin costos del negocio).
- **Avisos por Telegram:** se pueden apagar (si tienes Telegram vinculado).
- **Eliminar mi cuenta:** borra tu cuenta, tus sesiones y tus avisos; quita tu nombre, tu contacto y las notas y destinos de tus pedidos, y los mensajes que mandaste al bot; deja tus valoraciones sin nombre y quita tu nombre del registro de actividad. No se puede deshacer.
- **Vincular o desvincular Google y Telegram:** desde Mi cuenta en el navegador. Dentro de la app, el vínculo con Google no está disponible por ahora.

**Personal:** no se da de baja solo. Pídele a un administrador que lo haga o corrija tus datos desde Usuarios.

**Si no puedes hacerlo tú**, pídelo al negocio por el mismo medio con el que haces tus pedidos.

## Mantenimiento de este documento
Si cambian las tablas o lo que guarda taso-inv, hay que actualizar este archivo y `PrivacyNotice.kt` (app Android) a la vez. Fuentes revisadas: `migrations/0001` a `0020`, `app/auth.py`, `app/cuentas.py`, `app/google_login.py`, `app/telegram_login.py`, `app/respaldo.py`, `web/js/views/cuenta.js` y el bloque de registro de Caddy en el VPS.
