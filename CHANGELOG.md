# Changelog

Cada versión nueva se publica al subir a `main` un `VERSION_NAME` (en `gradle.properties`) sin Release.
El texto de la sección `## <versión>` es el que ven los usuarios en el aviso de actualización.

## 0.10.0

### Historial de precios
- Toca cualquier tarjeta de **Tasas** para abrir su **detalle**: precio actual, gráfico de evolución (6 meses por defecto) y lista de precios por día (hoy y 6 días hacia atrás).
- Rangos **7D / 30D / 90D / 6M** o **fechas a elección** (solo dentro de los datos que existen); la lista se amplía a 14 o 30 días. Toca o desliza el dedo sobre el gráfico para ver la fecha y el valor; arriba se ven la variación del periodo, el valor inicial y final, el máximo y el mínimo.
- El precio de cada día es el de las **7:00 a. m. de Cuba** (o la lectura más cercana) y los días se agrupan por fecha de Cuba. Si un día no tiene dato se muestra «sin dato»: no se interpola ni se inventa. En el gráfico, el hueco se une con trazo discontinuo.
- Fuentes con poco historial (QvaPay): aviso de «historial corto» y el gráfico y la lista siguen funcionando con 1 o 2 días.
- Las tarjetas muestran de fondo, muy tenue y difuminada, la tendencia de los **últimos 30 días**. Esto necesita un endpoint nuevo en la API (`/tasas/history/summary`, ver `docs/HISTORIAL_DE_PRECIOS.md`); con la API actual las tarjetas se ven como siempre, sin fondo.

### Nuevo aspecto
- Paleta **turquesa sobre azul noche** en toda la app y en los widgets (reemplaza al índigo). La convención de sube/baja sigue siendo la de Ajustes (por defecto, sube rojo y baja verde).

## 0.9.1

### Letra grande del sistema
- Los selectores de **fuente** (Tasas y Calculadora), las **cuentas del Blog** y las opciones de **Ajustes** (tema, fuente por defecto, actualización en segundo plano) ya no se deforman con un tamaño de letra grande: si las etiquetas no caben en una fila, las opciones pasan a **dos por fila** (o una) en vez de partir el texto y dejar una cápsula más alta que las demás. El texto nunca se recorta.
- En pantallas justas, la marca de selección (✓) puede no mostrarse para dar espacio a las etiquetas; la opción elegida sigue resaltada.
- En **Ajustes**, los botones de *Avanzado* y *Actualizaciones* se acomodan en varias líneas con la misma altura cuando no caben juntos.
- La tarjeta del **año** apila "Año 2026" y el porcentaje cuando no caben en la misma línea.

## 0.9.0

### Nueva fuente: QvaPay
- En **Tasas** hay una cuarta fuente, **QvaPay**, con los promedios P2P por método de pago: Banco CUP, Banco MLC, Tropipay, saldo ETECSA, Zelle, Clásica, Bolsa TM, Bandec Prepago y Sberbank. Cada tarjeta muestra el **promedio** y, debajo, **compra y venta**.
- Ojo con la unidad: cada valor es **cuánto de ese método recibes por 1 USD de QvaPay** (no todos son CUP por unidad: Zelle ronda 1,02 y MLC 1,4). Los métodos sin operaciones recientes no aparecen.
- QvaPay también está en la **calculadora** (con el USD como base), en los **widgets** y en **Ajustes** (fuente por defecto y monedas visibles). Necesita la API actualizada; con la anterior, esa fuente simplemente aparece vacía.

### Calculadora
- Nuevo botón **×** junto al monto para borrarlo de un toque y escribir otro.

### Datos
- La API ahora actualiza las tasas cada **15 minutos** (antes 5), así que la flecha ▲ ▼ compara contra 15 minutos atrás.

## 0.8.1

### Encabezado que se recoge en Tasas y Blog
- Al bajar por la lista, la fila del título (nombre y botones) **se esconde** y las fuentes (Tasas) o las cuentas (Blog) se quedan **fijas arriba**, con el contenido pasando por detrás.
- Al subir, el encabezado **reaparece en cuanto empiezas a subir**, aunque estés a mitad de la lista. Si lo sueltas a medias, se asienta con un resorte.
- Al deslizar a los lados, solo cambia el contenido de debajo: las fuentes y las cuentas no se mueven. En la primera o la última, el gesto pasa a la sección vecina como antes.

## 0.8.0

### Barra flotante
- La barra inferior es ahora una **cápsula que flota sobre el contenido**, sin la franja de fondo detrás ni el velo de la barra de navegación de Android. Las listas pasan por debajo y siempre puedes llegar al último elemento.
- La burbuja de la barra se puede **arrastrar con el dedo**: crece un poco al agarrarla y se asienta con un resorte (y una vibración ligera) en la sección más cercana.

### Transiciones más fluidas
- Al deslizar entre secciones, la sección se asienta con un **resorte** y el efecto de profundidad es más suave. Al tocar un icono lejano ya no recorre las secciones del medio.
- En **Tasas** (fuente) y **Blog** (cuenta), el contenido ahora **sigue al dedo** y se desliza al cambiar, en vez de cambiar de golpe.

## 0.7.1

### Actualizaciones
- Si Android rechaza la instalación automática de una actualización (pasaba en algunos Samsung con "verificación fallida"), la app lo reintenta una vez pidiendo la confirmación normal de Android.
- Si aun así falla, el mensaje explica qué revisar (Play Protect o Auto Blocker) y ofrece descargar con el navegador.

## 0.7.0

### Calculadora de tasas
- **Pantalla nueva en el centro de la barra**: escribe un monto y mira cuánto equivale. Por defecto USD → CUP con la tasa de El Toque, y puedes cambiar la fuente a BCC o CADECA.
- Botón para **invertir** la conversión (CUP → USD) y tarjeta "Equivale a" con el resto de monedas de la fuente; toca una fila para fijarla como destino.
- **Copiar y compartir** el resultado como un recibo de TASALO.

### Nueva barra inferior
- Ahora es una **isla flotante de cristal**, solo con iconos: Tasas, Combustible, Calculadora, Blog y Ajustes. Una burbuja sigue al dedo al cambiar de sección.
- Puedes **deslizar con el dedo** para pasar de una sección a otra. En Tasas y Blog, el deslizamiento solo cambia de fuente o cuenta si hay otra en esa dirección; en la primera o la última, pasa a la sección vecina.

## 0.6.1

### Arreglos del nuevo aspecto
- **Texto e iconos que no se veían en modo oscuro** (sobre todo en el Blog: el texto de los posts y los botones de actualizar, **+** y lápiz salían en negro sobre fondo oscuro). Ahora todo el contenido toma el color del tema: claro sobre fondo oscuro y oscuro sobre fondo claro.
- **Mejor lectura en modo claro**: el verde de "baja", el rojo de "sube", el acento y los bordes se oscurecieron un paso para que se lean bien sobre las tarjetas (antes el verde casi se perdía).
- **Iconos de las pestañas nuevos**: en lugar de emojis (que cada móvil dibuja distinto y no cambian con el tema), ahora son iconos vectoriales que siguen los colores de la app.

## 0.6.0

### Nuevo aspecto "Quiet Glass"
- La app estrena el mismo estilo visual que las extensiones de TASALO: fondo casi negro (o gris azulado en modo claro) con un suave resplandor índigo arriba, un único color de acento y tarjetas de cristal con borde fino y esquinas más redondeadas.
- Tipografías nuevas: **Space Grotesk** para el texto y **JetBrains Mono** para las cifras (tasas, precios, porcentaje del año y código), incluidas en la app, sin descargas adicionales.
- Colores de subida y bajada más suaves (rojo y verde menos saturados), iguales a los de la extensión.
- Los **widgets** usan la misma paleta y esquinas más redondeadas. Siguen con la fuente del sistema porque Android no permite fuentes propias en widgets.
- Sin cambios en los datos ni en los ajustes: el modo Auto/Claro/Oscuro y la opción de invertir colores funcionan igual.

## 0.5.3

### Blog
- Nuevo botón **+** junto a las pestañas del Blog: escribe un usuario de Hive (con o sin @, o pega el enlace de su blog en Ecency) y se carga su blog como una tercera pestaña. Se recuerda al cerrar la app.
- Con un usuario elegido, el botón ✎ permite **cambiarlo por otro** o **quitarlo**.
- Los posts se leen ahora con un lector de Markdown **completo**: **tablas** (con scroll horizontal si no caben), **citas** con barra lateral, listas anidadas y de tareas (☐/☑), texto tachado, **enlaces** (también los sueltos, con título o con paréntesis) y menciones `@usuario` que abren su perfil. Los saltos de línea se respetan como en Hive.
- Las imágenes admiten enlace (por ejemplo, la miniatura de un vídeo abre el vídeo) y varias en una misma línea.
- Los posts que traen **HTML** (de otras apps de Hive) se entienden: tablas, citas, listas, código, imágenes y enlaces se convierten. Los vídeos embebidos aparecen como enlace al original, y los scripts y estilos se descartan para no mostrar cosas raras.
- Los posts guardados se vuelven a descargar una vez para aplicar el nuevo formato. Las **Alertas** del equipo siguen con el formato sencillo de Telegram.

## 0.5.2

### Blog
- La pestaña Blog tiene ahora **dos cuentas**: **@tasalo** (el blog del equipo, la que se abre primero) y **@ersusoficial**. Cámbialas con las pestañas de arriba o **deslizando** a izquierda o derecha, como entre las fuentes de tasas. Cada cuenta guarda sus últimos posts para leerlos sin conexión.

### Ajustes
- En **Acerca de** hay un acceso directo al **bot de Telegram (@tasalobot)** y el enlace al blog apunta ahora al blog de TASALO.

## 0.5.1

### Tasas
- Ahora puedes **deslizar a izquierda o derecha** sobre las tasas para cambiar entre El Toque, BCC y CADECA. Tocar el selector de arriba sigue funcionando igual.

## 0.5.0

### Blog
- Nueva pestaña **Blog** en la barra inferior (📰), entre Combustible y Ajustes.
- Muestra los **últimos 10 posts** con portada, título, un resumen y la fecha. Toca uno para leerlo completo dentro de la app, con sus imágenes, y usa **Abrir en Ecency** si prefieres verlo allí.
- Las imágenes se cargan solo cuando llegas a ellas, así gastas menos datos.
- Los posts se guardan en el móvil: puedes releerlos **sin conexión**. Se actualizan al abrir la pestaña (si pasaron más de 15 minutos), con el botón de refrescar o arrastrando la lista hacia abajo.
- Desde un post, el botón *Atrás* vuelve a la lista.

## 0.4.1

### Arreglos
- **Xiaomi, Redmi y POCO:** la actualización dentro de la app fallaba con "Permission denied". Ahora Android muestra su confirmación de instalación: pulsa **Instalar**. Si se cancela, el mensaje explica qué hacer.
- La ventana de error de la actualización ya no se corta: los botones *Reintentar*, *Descargar con el navegador* y *Cerrar* se ajustan al ancho de la pantalla y el texto del error se puede desplazar.
- Los reportes de fallos ya no incluyen como errores las interrupciones normales del refresco en segundo plano.

## 0.4.0

### Alertas
- La sección **Alertas** de las notificaciones ya recibe los **mensajes del equipo TASALO**. Se ven contraídos (título y fecha) y se expanden al tocarlos. El formato (negrita, cursiva, enlaces) es el mismo que en Telegram.
- La campana muestra un punto, y la pestaña Alertas un contador, cuando hay mensajes sin leer.
- Los mensajes se descargan junto con las tasas (cada 30 min por defecto) y se guardan los últimos 20, así puedes releerlos sin conexión.

## 0.3.0

### Actualizar sin salir de la app
- Al pulsar **Actualizar**, la app descarga el APK con una barra de progreso, comprueba que no esté dañado (huella SHA-256), que sea de TASALO y que tenga la misma firma, y lo instala. Ya no te manda al navegador.
- En Android 12 o superior la instalación puede hacerse **sin ninguna confirmación**; en Android 8–11 el sistema pide un toque para confirmar (lo exige Android).
- La primera vez Android pedirá permitir a TASALO "instalar aplicaciones": solo se usa para actualizarse a sí misma.
- Si algo falla, el diálogo ofrece *Reintentar* o *Descargar con el navegador*.
- Cada Release incluye ahora el archivo `.sha256` y la huella del certificado de firma, que también aparece en *Ajustes → Acerca de* para que puedas comprobar que tu instalación es auténtica.

### Notificaciones
- Nueva **campana** a la izquierda del botón de actualizar (con un punto cuando hay una versión nueva).
- Pantalla de notificaciones con dos secciones: **Alertas** (aquí llegarán los mensajes del equipo) y **Actualizaciones** (las últimas versiones con sus novedades). Las novedades se expanden al tocarlas.

### Ajustes
- *Acerca de* incluye accesos directos a GitHub, el blog y el correo del equipo.

## 0.2.3

### Widgets
- Muestran la **hora real del dato** ("act. 14:32") en lugar de "hace X min". Un widget es una imagen fija: "hace 3 min" se quedaba congelado y parecía actualizado aunque llevara horas sin datos.

### Segundo plano
- Nuevo ajuste **Actualización en segundo plano**: 15 min, 30 min (por defecto), 1 h o solo manual. Menos intervalos = menos batería, datos y consultas a la API.
- *Ajustes → Diagnóstico* indica cuándo fue el último refresco en segundo plano. Si es muy antiguo, tu móvil está frenando la app: en Xiaomi activa el *Autoinicio* y pon la batería en *Sin restricciones* para TASALO.

### Reportes de fallos
- Cada fallo guardado lleva la versión de la app y se descartan los de versiones anteriores, así no se vuelve a preguntar por fallos ya corregidos.
- Se quitan del reporte las salidas normales del sistema (app cerrada por el usuario, actualización, memoria liberada en segundo plano).

### Seguridad
- Los enlaces en el lector de novedades solo se abren si son `https`, `http` o `tg`.

## 0.2.2

### Correcciones
- **Cierre al abrir la app en Android 12 o anterior** (visto en Android 11): la app se cerraba al instante por usar una función que solo existe desde Android 13. Afectaba a las versiones 0.1.0, 0.2.0 y 0.2.1. Si tu teléfono la cerraba al abrir, instala esta versión a mano.
- La compilación ahora comprueba que no se use ninguna función que falte en Android 8–12, para que no vuelva a pasar.

## 0.2.1

### Novedades
- **Nuevo icono de la app**: el logo de TASALO (la T con la flecha de tendencia), también en iconos temáticos de Android 13+.

### Widgets
- **Tasas 2x1**: ya no se corta el valor. En una celda de alto todo va en una fila: código a la izquierda y valor a la derecha, y tocar la hora refresca los datos.
- **Bloque**: pasa a tamaño 4x2 por defecto y usa dos columnas cuando hay ancho, así la misma información ocupa menos alto. (Los widgets ya colocados conservan su tamaño; puedes redimensionarlos o añadir uno nuevo.)

## 0.2.0

### Novedades
- **Aviso de actualizaciones**: al abrir la app se comprueba si hay una versión nueva en GitHub y se te pregunta si quieres actualizar, con la lista de cambios. Puedes omitir una versión o buscar a mano en *Ajustes → Actualizaciones*.
- **Reportes de fallos (beta)**: si la app se cierra sola, te ofrece enviar un reporte a **tasaloteam@gmail.com**. Se abre tu app de correo con el texto ya escrito para que lo revises; incluye el modelo del teléfono, la versión de Android y el error, sin datos personales.
- Nuevo *Ajustes → Diagnóstico* para enviar el registro cuando quieras o desactivar la pregunta.

### Widgets
- Se adaptan mejor a cada launcher: usan el radio de esquina del sistema en Android 12+ y su tamaño en celdas de la cuadrícula.
- Las columnas de **Bloque** (sobre todo CADECA) reparten el ancho en vez de usar medidas fijas, así no se desbordan.
- Con *tamaño de fuente muy grande* el texto ya no se sale de las filas.
- El tema **Claro/Oscuro** elegido en Ajustes también se aplica a los widgets.
- Fondo más opaco para que se lea sobre cualquier fondo de pantalla.

## 0.1.0
- Primera versión beta: tasas de El Toque, BCC y CADECA, combustible, año y frase del día, y 3 widgets.