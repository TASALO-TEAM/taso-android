# Escalado de fuente: cómo se comportan las cápsulas de TASO

Principio: la app **respeta** el tamaño de fuente del sistema (no se limita, no se reduce, no se corta el texto
para que "quepa"). Cuando el texto necesita más espacio, **cambia la distribución**, no el texto.

## Regla para cualquier selector de opciones

Usa `AdaptiveSegmentedChoice` (`ui/components/AdaptiveSegmentedChoice.kt`). No uses `SingleChoiceSegmentedButtonRow`
directamente: con letra grande Material 3 deja que una etiqueta pase a dos líneas y la fila entera se ve más alta.

Escalera (la decide `planSegments`, que tiene tests):

1. una fila, con check de selección;
2. una fila, sin check (la selección sigue viéndose por el relleno y por la semántica);
3. 2 por fila, y luego 1 por fila;
4. solo si una etiqueta sola no cabe en una línea, se permite el salto de línea (nunca se recorta).

## Otras reglas

- Alturas con `heightIn(min = …)`, nunca `height(…)` en contenedores que llevan texto.
- Filas de botones: `FlowRow` + `Modifier.fillMaxRowHeight()` en cada botón (si se parten, se ven del mismo alto).
- Pares "título + valor" en una fila: `FlowRow` con `SpaceBetween`, para que apilen cuando no caben.
- Evitar `maxLines` + `Ellipsis` salvo en texto no esencial con acceso al contenido completo.
- Íconos de solo imagen (barra inferior): `contentDescription` obligatorio.

## Cómo probar (sin tocar código)

Ajustes del teléfono → Pantalla → Tamaño de fuente (y "Tamaño de pantalla"). Probar 85 %, 100 %, 130 %, 150 %, 200 %
en Tasas, Calculadora, Blog y Ajustes, en un teléfono de ~360 dp y en uno de ~411 dp.
