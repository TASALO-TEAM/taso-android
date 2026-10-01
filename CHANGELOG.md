# Changelog

Cada versión nueva se publica al subir a `main` un `VERSION_NAME` (en `gradle.properties`) sin Release.
El texto de la sección `## <versión>` es el que ven los usuarios en el aviso de actualización.

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