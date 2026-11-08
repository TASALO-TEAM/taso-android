# Auditoría UI/UX de taso-android (2026-11-08, sobre v0.19.0)

Auditoría **solo de código** (sin móvil ni emulador): lo marcado «verificar» hay que confirmarlo en un teléfono real.
Reglas: dentro de Quiet Glass v3 (los radios 22/28 y el turquesa son marca; no se tocan), sin dependencias nuevas, sin colores nuevos,
respetar `docs/ESCALADO_DE_FUENTE.md` (se cambia la distribución, nunca se recorta ni se reduce el texto) y mantener `ContrastTest` en verde.
Las líneas son aproximadas (de v0.19.0). Estado: `[ ]` pendiente · `[x]` hecho (con la versión).

## Puntuación (estimada): 14/20, «Bueno»
Accesibilidad 2 · Rendimiento 3 · Apariencia y tema 3 · Conformidad Android 3 · Adaptabilidad 3.
Veredicto: pasa como app nativa (Compose + Material 3 con identidad propia, no una web metida en una app).

## Hallazgos

### P1
- [ ] **A1 Calculadora recorta el resultado.** `ui/calculator/CalculatorScreen.kt` ~273 (resultado 30 sp) y ~380 (filas de la factura 18 sp) llevan `maxLines = 1` sin salida; con 1.000 USD o más en letra grande la cifra se corta en silencio. Arreglo: `FlowRow` con el texto en `Modifier.weight(1f)` y `textAlign = End`, de modo que si no cabe baja a su propia línea (mismo patrón que Efectivo). Sin `maxLines`.
- [ ] **A2 TalkBack lee lo que está debajo de las pantallas superpuestas.** `MainActivity.kt` (Notificaciones, Blog, Detalle usan `.clickable {}` solo para tragar toques). Arreglo: `Modifier.clearAndSetSemantics {}` en el `HorizontalPager` mientras haya una superpuesta abierta. Verificar con TalkBack (incluye la pestaña Tienda, que es un WebView).

### P2
- [ ] **A3 Tarjetas de Notificaciones** (`ui/notifications/NotificationsScreen.kt` ~135 y ~193): `clickable` sin `role` ni `stateDescription`, y el chevron es un carácter de texto (▴/▾) en vez de `NavIcons.Chevron` como en Ajustes. Arreglo mínimo: mismo `clickable(onClickLabel, role = Button)` + `stateDescription` + chevron vector que rota. (Extraer un `ExpandableCard` común queda para 0.20.0.)
- [ ] **A4 Teclado en Ajustes.** `ui/settings/SettingsScreen.kt` (`AdvancedSection`, campo «URL base de la API», última categoría): la `LazyColumn` no usa `imePadding` y con edge-to-edge el teclado se superpone. Arreglo: el mismo patrón de `CashScreen.kt` (`imePadding()` + sin hueco de la isla mientras el teclado está visible). **Blog descartado**: su campo está dentro de un `AlertDialog`, que maneja el teclado solo.
- [ ] **A5 Barra isla** (`ui/components/GlassIslandBar.kt`): sin `selectableGroup()` en la fila, solo iconos sin etiqueta y sin foco/pulsado visible (`indication = null`). Arreglo: `selectableGroup`, etiqueta visible solo en la pestaña activa, foco visible.
- [ ] **A6 «Quitar animaciones» casi no se respeta.** Solo `CollapsingHeader.kt` (~90, `ANIMATOR_DURATION_SCALE == 0`). La isla, el parallax del paginador (`MainActivity.kt`), el gráfico (650 ms, `PriceCharts.kt`) y el slide del Detalle lo ignoran. Arreglo: un único `LocalReduceMotion` y usarlo en todos.
- [ ] **A7 Superpuestas sin transición.** El Detalle entra con fundido + deslizamiento; Notificaciones y Blog aparecen y desaparecen en seco. Arreglo: el mismo `AnimatedVisibility` de 200 ms para los tres.
- [ ] **A8 Atrás predictivo apagado.** `AndroidManifest.xml` sin `android:enableOnBackInvokedCallback="true"` (targetSdk 35 lo deja opcional). Hay 4 `BackHandler` en `MainActivity.kt`: probar los casos al activarlo.
- [ ] **A9 Sin tokens de espaciado/radio.** Padding de tarjeta 14 dp (`RateCard`, Notificaciones) vs 16 dp (Año, Frase, Ajustes); ~37 valores fuera de 4/8 (6, 10, 14); `RoundedCornerShape(16.dp)` suelto en `CurrencyPicker` frente a `shapes.small` (14). Arreglo: `Spacing` y `Radius` en `ui/theme`.
- [ ] **A10 Dos lenguajes de iconos.** `NavIcons` (trazo 2, redondeado) frente a 24 usos de `Icons.Filled.*` (campana y refrescar junto al Blog en Tasas; cerrar, invertir, copiar, compartir en Calculadora; Blog; MainActivity). Arreglo: dibujar los que faltan al estilo `NavIcons` y migrar; después evaluar quitar `material-icons-extended`.
- [ ] **A11 `blur(3.dp)` por tarjeta** (`ui/history/PriceCharts.kt`, `SparklineBackground`, 9–12 a la vez). El Samsung A07 de pruebas es gama baja y ya dio `POCA_MEMORIA`. Medir; si se nota, quitar el blur (el trazo ya es tenue) o cachear con `drawWithCache`.
- [ ] **A12 Objetivo táctil** del `CurrencyPicker` de la Calculadora: ~40 dp de alto (<48) y filas de la factura con `clickable` sin `role`. Arreglo: `heightIn(min = 48.dp)` y `role = Role.Button`.
- [ ] **A13 `DateRangePicker` con alto fijo** `height(500.dp)` (`ui/history/HistoryDetailScreen.kt` ~406): se corta en pantallas bajas, horizontal o con letra grande. Arreglo: `heightIn(max)` con scroll.
- [ ] **A14 Cuadrícula de Tasas siempre en 2 columnas** (`ui/home/HomeScreen.kt`) aunque la letra esté al 150–200 % (cifra de 26 sp). Verificar. Arreglo: 1 columna según `fontScale`.
- [ ] **A15 Sin splash.** El fondo de la ventana depende del modo del sistema (`values` / `values-night`); si el tema elegido en la app difiere, puede verse un destello del color contrario al arrancar. Verificar.

### P3
- [ ] **A16 `SkeletonBlock`** mide 96 dp y la tarjeta real más (salto al cargar); el plan pedía pulso suave (finito y respetando reducir animaciones).
- [ ] **A17 `RateCard` duplica ~50 líneas** (lista y cuadrícula) y los marcadores difieren: `-` frente a `—`, `\u00B7` frente a `·`.
- [ ] **A18 `StatusBanner`:** «Datos desactualizados» usa el color de error y no tiene icono.
- [ ] **A19 Imports sin uso en `MainActivity.kt`** (`LocalGasStation`, `Article`, `Calculate`, `SwapHoriz`, `Settings`...), resto de lo ya quitado. Anotar; limpiar al tocar el archivo.
- [ ] **A20 `FontWeight.ExtraBold`** (mes activo de `YearCard`) no existe en la familia (solo Normal–Bold): cae a Bold.

## Lo que está bien (conservar)
Cero colores `Color(0x…)` sueltos fuera de `Theme.kt`/`Palettes.kt` · 9 paletas validadas por `ContrastTest` · dos familias (Space Grotesk + JetBrains Mono) y un solo acento · sombra solo en la isla · `AdaptiveSegmentedChoice` con escalera y tests · semántica buena en categorías de Ajustes, gráfico y campos · sube/baja nunca solo por color · edge-to-edge bien resuelto · listas con `key`.

## Orden de entrega (versiones pequeñas, cada una por CI)
1. **0.19.1** correcciones: A1, A2, A3, A4.
2. **0.20.0** base común: A9, A10, A17 (+ `ExpandableCard` de A3, A12).
3. **0.20.1** movimiento: A6, A7, A8, A5.
4. **0.21.0** estados y arranque: A16, A18, A15, A14, A13, A11.
