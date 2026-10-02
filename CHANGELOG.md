# Changelog

Cada versión nueva se publica al subir a `main` un `VERSION_NAME` (en `gradle.properties`) sin Release.
El texto de la sección `## <versión>` es el que ven los usuarios en el aviso de actualización.

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