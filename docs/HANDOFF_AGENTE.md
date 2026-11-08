# Traspaso para otro agente (continuar como Claude en este proyecto)

Última actualización: 2026-10-09. Si eres otro agente y lees esto, sigue el trabajo tal cual está aquí.
Actualiza la sección «Estado» al terminar cada paso.

## Quién es el usuario y cómo trabaja
- Ernesto (usuario `ersus93`), único desarrollador y quien decide en TASALO (ecosistema multi-repo de tasas CUP, combustible y cripto para Cuba).
- Habla español informal y se le responde en **español**, directo y breve. Escribe rápido y con erratas: interpreta la intención.
- Sube **directo a `main`** y deja que **GitHub Actions compile**. No hay Android SDK local ni Java 17 (Java 1.8): no se puede compilar en su PC.
  Por eso el código se escribe con mucho cuidado y se revisa a ojo; si el CI falla, se lee el error y se corrige en otro commit.
- Cada versión nueva = subir `VERSION_NAME` en `gradle.properties` (sin Release existente) + sección en `CHANGELOG.md` (ese texto lo ven los usuarios en el aviso de actualización).
  El CI hace lint (`NewApi`), tests unitarios y APK debug; si la versión es nueva en `main`, firma y publica Release.
- Antes de tocar el **backend** (`taso-api`) hay que decírselo. Esta vez lo aprobó (ver «API»).
- Comprobar CI: `gh run list -L 3` desde `C:\Users\ernes\Documents\tasalo\taso-android` (la API pública de GitHub da rate limit desde el sandbox).

## Mapa del disco (Windows)
`C:\Users\ernes\Documents\tasalo\` → `taso-android` (app, Compose/Kotlin, **este repo**), `taso-api` (FastAPI, Python 3.13), `taso-bot`, `taso-ext`, `taso-extmf`, etc.
Herramientas útiles: Desktop Commander (`start_process` con PowerShell, `edit_block`, `write_file`, `start_search`) y Filesystem (`read_multiple_files`).
`edit_block` hace reemplazos exactos de texto (incluye saltos de línea). Para parches grandes, escribe un script Python temporal y bórralo después.

## App Android (taso-android)
- Compose + Material 3 (BOM 2024.12.01), Kotlin 2.0.21, `minSdk` 26, DI manual (`AppContainer`), sin librería de navegación.
- Navegación: `MainActivity` con un `HorizontalPager` de 5 secciones (Tasas · Efectivo · Calculadora · Blog · Ajustes) + `GlassIslandBar`. Las pantallas
  que van «encima» (Notificaciones y el **detalle histórico**) son overlays dentro de `TasaloRoot`, con `BackHandler`.
- Tema: `ui/theme/Theme.kt` («Quiet Glass» v3: **turquesa `#3DD6C0` sobre azul noche `#0E1621`**). `ContrastTest` exige WCAG (4,5:1 texto, 3:1 UI): al tocar colores,
  recalcular. Sube/baja: `ChangeColors` + ajuste `invertColors` (por defecto **sube rojo, baja verde**). Widgets: `widget/WidgetUi.kt` (`WidgetPalette`).
- Datos: `TasaloRepository` (tasas, año, mensajes; caché en DataStore) y **`HistoryRepository`** (historial, caché en `filesDir/history/*.csv`).
- Historial (v0.10.0, en `main`, CI verde):
  - `domain/PriceHistory.kt`: `PricePoint`, `RawPoint`, `PriceHistory.daily/rows/stats/between/lastDays/trend/encode/decode`. Día = fecha de **America/Havana**; precio = lectura más cercana a las **7:00** de Cuba.
  - `data/parse/HistoryParsers.kt` (`summary`, `daily`, `raw`), `TasaloApi` (`historySummary`, `historyDaily`, `historyRaw`).
  - `ui/history/`: `PriceCharts.kt` (`SparklineBackground`, `PriceChart` en Canvas), `HistoryDetailScreen.kt`, `HistoryViewModel.kt`.
  - `RateCard(rate, source, history, onClick)` dibuja el fondo difuminado y abre el detalle. `HomeScreen` recibe `summary` y `onOpenDetail`.
  - Test: `app/src/test/.../data/HistoryTest.kt`. Doc de decisiones: `docs/HISTORIAL_DE_PRECIOS.md`.
- Widgets (Glance): `widget/TasasWidget.kt`, `BloqueWidget.kt`, `AnioFraseWidget.kt`, `WidgetConfigActivity.kt`, `WidgetUi.kt`. Solo leen caché (nunca red).
  `WidgetUpdater.updateAll` los refresca; `RefreshWorker` los actualiza en segundo plano.

## API (taso-api) — contrato APROBADO por el usuario el 2026-10-09
Ya existe `GET /api/v1/tasas/history?source=&currency=&days=` (lecturas sueltas cada 15 min, `rate_snapshots`: `buy_rate`, `sell_rate`, `fetched_at`; QvaPay → promedio de compra y venta).
Hay que **añadir** en `src/routers/rates.py` (prefijo `/api/v1/tasas`; ojo con el orden de rutas frente a `/history/local` y `/history/cubanomic`):
1. `GET /history/daily?source=&currency=&days=180` → `{ok, source, currency, tz:"America/Havana", data:[{date:"YYYY-MM-DD", rate}]}`
2. `GET /history/summary?days=30` → `{ok, tz, days, data:{<fuente>:{<MONEDA>:[{date, rate}]}}}`
Regla: un punto por **fecha local de Cuba**, la lectura más cercana a las **7:00** de ese día; los días sin lectura **no aparecen**; QvaPay `rate` = (compra+venta)/2; resto: `sell_rate` o `buy_rate`.
Servicio nuevo sugerido: `src/services/history_service.py` + schemas en `src/schemas/history.py` + tests en `tests/test_routers/`. La app ya está lista y cae al endpoint viejo si `daily` da 404.
Desplegar la API es cosa del usuario (servidor `tasalo.duckdns.org`); no se despliega desde aquí.

## Pendiente (en este orden, pedido por el usuario)
Ver la sección «Estado» abajo para marcar lo hecho.
1. Este archivo de traspaso.
2. Implementar los endpoints `daily` y `summary` en `taso-api` (aprobado).
3. Dibujar **Blog** en la maqueta de Design (artefacto abajo).
4. Widgets: fondo con gráfico de tendencia en los widgets actuales + **widgets nuevos** basados en los gráficos (usar el resumen de 30 días; los widgets solo leen caché).
5. Todo se compila en GitHub: subir a `main` con versión nueva y comprobar `gh run list`.

## Maqueta (artefacto Design)
URL: https://claude.ai/artifact/Ba4ZDY9C3rqD9As8L7jWKx (tipo Design: **no cambiar a otro tipo**). Tableros: `Main` (detalle), `Tasas`, `Combustible` (la sección ya no existe en la app: la sustituye Efectivo desde v0.14.0), `Calculadora`, `Ajustes`, `Corto` (historial corto QvaPay).
Se genera con scripts Python (`/tmp/gen.py`, `/tmp/gen2.py` del sandbox, pueden no existir): HTML estático por tablero (`*.dc.html`) + `canvas.json`, publicado con la herramienta `Artifact` (action publish, `url` del artefacto, `file_path` + `files`).
Estilo: fondo `#0E1621`, tarjetas `#16212F`, acento `#3DD6C0`, coral `#FF8F7E`, texto `#E8EEF5`/`#9FB0C3`, Manrope, esquinas 20 px, barra inferior tipo cápsula.

## Estado
- [x] **v0.16.1 Tienda en blanco (2026-11-07).** Reporte .eml (Samsung A07, Android 16): `W/Tienda: el proceso de render del WebView termino (fallo=false)` + varios `POCA_MEMORIA`. Cambios en `ui/tienda/TiendaController.kt` y `TiendaScreen.kt`: contenedor `FrameLayout` propio por montaje (evita que un `onRelease` tardio quite el WebView al nuevo), `checkAlive()` al volver (si no responde o `body` vacio, se recrea y recarga `lastUrl`), el render muerto ya no marca error (maximo 3 seguidos), y `onTrimMemory` suelta el WebView si esta oculto. Commit a599036, CI verde, Release v0.16.1. Sin probar en un movil real: pendiente confirmar que ya no se queda en blanco. Hay cambios sin commitear en `HomeScreen.kt` (`onOpenBlog` duplicado, no compila): no son de este paso. Siguiente: `docs/plans/2026-11-07-android-ux-premium-notificaciones-privacidad.md` (pendiente de aprobacion).
- [x] **v0.15.0 Paletas de color + Efectivo de mayor a menor (2026-11-01).** `Palette` (9 paletas de taso-inv) en Models.kt, ajuste `palette` en SettingsStore, `ui/theme/Palettes.kt` (esquemas y cristal por paleta; sube/baja se ajustan al fondo con `readableOn`), selector «Paleta de color» en Ajustes -> Apariencia, `ContrastTest` audita todas. Billetes de `CashCurrency` ahora de mayor a menor (pantalla y recibo). CI verde (commit 0f7ca9d). Pendiente: probar en movil las 9 paletas en claro/oscuro; los widgets (`WidgetPalette`) NO siguen la paleta; el resumen de Apariencia cerrada no muestra la paleta.
- [~] **v0.14.0 Efectivo (2026-10-13).** Se partió de v0.13.0 (HEAD `12e77a7`). Hecho: `domain/CashCalc.kt` (+ `CashReceipt`, recibo `.txt` de 50 columnas) con `CashCalcTest`, `ui/cash/CashScreen.kt` (selector CUP/USD/EUR, billetes, cápsula de total, **Limpiar** con confirmación y **Guardar** individual/total con vista previa y `CreateDocument`), icono `NavIcons.Cash`, barra Tasas · Efectivo · Calculadora · Blog · Ajustes, y **eliminada toda la cadena de combustible** (pantalla, tarjeta, repositorio, API, parser, caché, modelos, tests y `fuel.json`; los datos viejos de DataStore se ignoran). Sin cambios en `taso-api`. No se pudo compilar en local: **comprobar el CI** (`gh run list -L 3`). Probar en el móvil: Efectivo en la 2.ª posición; cantidades conservadas al cambiar de moneda y al rotar; máximo 6 dígitos; Limpiar; Guardar individual y total (abrir el `.txt`); fuente al 130/150/200 % (las filas deben apilarse sin recortar; es lo que más puede fallar: `FlowRow` con `weight`); claro y oscuro. Diseño aprobado: https://claude.ai/artifact/K1Ygwf9ZKG46sNzBhncvHj. Pendiente aparte: comparar los tokens de `taso-app/docs/DISENO.md` con `Theme.kt` (taso-app copia el diseño de esta app; no se cambió el tema).
- [x] v0.10.0 historial + paleta turquesa publicada (CI verde, run 37874660220).
- [x] Endpoints `daily`/`summary` en taso-api: commit 6ede75b (v0.8.0.0) subido a `main`. Falta que el usuario los despliegue en el VPS (git pull + restart) y corra pytest allí. `uv.lock` quedó modificado en su disco por `uv` y NO se commiteó (no es parte de esto).
- [x] Blog en la maqueta (2026-10-08). La URL anterior no era accesible, se creó una nueva desde el tipo Design: https://claude.ai/artifact/3HRLajTbpMkCpjuEYRCiV9 (tableros `Main`=Blog lista, `BlogPost`, `Widgets`; este último ya define los 2 widgets mejorados y los 2 nuevos: «Tendencia» 30D y «Mini tasas» con sparklines).
- [x] Widgets con fondo de gráfico + widgets nuevos (Tendencia, Mini tasas) + iconos nuevos de la barra (`ui/components/NavIcons.kt`, los de la maqueta). Código en `widget/WidgetChart.kt`; los widgets leen `HistoryRepository.cachedSummary()`.
- [x] Tasas: selector tarjetas/lista (v0.12.0): ajuste `ratesListView` en SettingsStore, `RateCard(listMode)`, `ViewToggle` en la cabecera de HomeScreen, iconos `NavIcons.CardsView/ListView`. Maqueta: tableros TasasGrid y TasasLista del mismo artefacto Design. Pendiente: comprobar en un móvil.
- [x] **v0.13.0 Ajustes por categorías: IMPLEMENTADA y subida a `main` (2026-10-09).** 4 categorías colapsables (`SettingsCategory` en `SettingsScreen.kt`), `settingsSummaries` + `SettingsSummaryTest`, selector de vista movido a Apariencia, «Acerca de» centrado con avatar de GitHub (Coil `SubcomposeAsyncImage`) e iconos de redes de la maqueta (`SocialIcons.kt`). Comprueba CI y Release con `gh run list -L 1` y `gh release list -L 1`; si el CI falló, corrige lo que diga el log (no se pudo compilar en local). Pendiente: probar en un móvil real (categorías, avatar sin conexión, cabecera de Tasas sin selector).
- [x] v0.11.0 compilada en GitHub (commit ce84ac3): lint, tests y APK verdes, Release v0.11.0 publicada. Pendiente del usuario: desplegar taso-api (daily/summary) para que se vean las curvas; hasta entonces los widgets salen sin curva. Pendiente de comprobar en un móvil real: aspecto de los widgets.

## Cosas que NO se hicieron y se dijeron al usuario
- La skill `.claude/skills/ui-ux-pro-max` existe pero no se leyó.
- No se recuerda el último rango/días del detalle (haría falta `SettingsStore`).
- El indicador de cambio de las tarjetas compara con la **lectura anterior** (15 min), no con «ayer».


---

# SIGUIENTE TAREA (aprobar con el usuario antes si cambia algo): Ajustes por categorías colapsables — v0.13.0

**Estado: IMPLEMENTADO en v0.13.0 (la spec se conserva como referencia de lo que se hizo y por qué).** El usuario pidió (2026-10-09): organizar Ajustes en categorías colapsables **con el mismo estilo que Notificaciones**, y **mover el selector tarjetas/lista de la cabecera de Tasas a Ajustes**, en la categoría de estilo/tema.

## Maqueta (fuente de verdad visual)
Artefacto Design vigente: https://claude.ai/artifact/3HRLajTbpMkCpjuEYRCiV9 (tipo Design, **no cambiar de tipo**; la URL `Ba4ZDY9C3rqD9As8L7jWKx` de arriba ya no es accesible). Tableros nuevos de esta tarea: `AjustesColapsado` (las 4 categorías cerradas + la sección Acerca de al final) y `AjustesApariencia` (Apariencia abierta con Tema, Vista de Tasas y Colores). `TasasGrid`/`TasasLista` ya NO llevan el selector en la cabecera (así debe quedar la app).
Para cambiarla: leer con `Artifact` action `read` + `url`, editar los `.dc.html` de `project/` y volver a publicar (`file_path` + `files`, o `edits`). Los tableros de Ajustes se generaron con un script (ya no existe); edítalos a mano.

## Categorías (orden y contenido)
Hoy `SettingsScreen.kt` (322 líneas) es una `LazyColumn` de 9 `Section(...)` sueltas. Agruparlas en **4 tarjetas colapsables** así (la 9.ª, «Acerca de», NO es tarjeta: ver su sección más abajo):

| Categoría | Contiene (código actual) | Resumen visible cerrada |
|---|---|---|
| **Apariencia** | «Tema» (Auto/Oscuro/Claro) · **«Vista de Tasas» (nuevo: Tarjetas/Lista)** · «Colores de subida y bajada» | `Oscuro · Tarjetas · Sube en rojo` |
| **Tasas** | «Fuente por defecto» · «Monedas visibles» · `RefreshSection` («Actualización en segundo plano») | `El Toque · actualiza cada 30 min` (0 min = «solo manual») |
| **Actualizaciones** | `UpdateSection` | `Versión X instalada` (+ ` · nueva Y` si `state.update != null`) |
| **Diagnóstico y avanzado** | `DiagnosticsSection` + `AdvancedSection` (URL base) | `Informe de fallos: sí/no · URL de la API` |

Iconos de categoría (trazo 2, redondeados, cuadrícula 24; ya dibujados en la maqueta): Apariencia = círculo con línea vertical y media luna; Tasas = tendencia (`NavIcons.Rates`); Actualizaciones = flecha abajo con base; Diagnóstico = `>_`. Añádelos a `NavIcons.kt` (o a un `SettingsIcons` que reutilice `build`, que hoy es `private`: hazlo `internal`).

## Acerca de (sección fija al final, NO colapsable, centrada)
El usuario la sacó de las tarjetas (2026-10-09). Dibujada al final de los dos tableros de Ajustes (el avatar de la maqueta es una copia incrustada porque las páginas publicadas no cargan imágenes remotas). Es el último `item` de la `LazyColumn`, después de las 4 tarjetas, separado por un divisor fino (1 dp) y con el título «Acerca de» centrado (`titleSmall`, `primary`). Todo centrado (`Column(horizontalAlignment = Alignment.CenterHorizontally)`):
1. **Perfil del desarrollador**, `GlassCard` clicable (≥ 48 dp) que abre `https://github.com/ersus93` con `LocalUriHandler` (`runCatching`, como `SOCIAL_LINKS`): avatar circular de 64 dp con borde `primary` al 40 %, `ersus93` (`titleMedium` bold), «Desarrollador de TASALO» (`bodySmall`, `onSurfaceVariant`) y la línea `github.com/ersus93` en `primary` con `ic_social_code` de 16 dp.
2. «TASALO Android · versión X» (como hoy).
3. Fila centrada de 4 `IconButton` = `SOCIAL_LINKS` actuales (mismos destinos) **con los iconos de la maqueta, que el usuario quiere conservar**: trazo 2, extremos redondeados, cuadrícula 24, color `primary` (Telegram `M21 3L10 14M21 3l-7 18-4-7-7-4z`; código `M8 7l-5 5 5 5M16 7l5 5-5 5`; Blog `M6 3h9l4 4v14H6z` + `M9 12h7M9 16h7M9 8h3`; correo `rect x3 y5 w18 h14 rx2` + `M3 7l9 6 9-6`). Impleméntalos como `ImageVector` con la misma técnica que `NavIcons.build` (hazlo `internal`), cambia `SocialLink.icon` de `Int` (drawable) a `ImageVector` y usa `Icon(link.icon, ...)`; borra `ic_social_*.xml` solo si un `grep` confirma que nadie más los usa (el «GitHub» de la fila sigue apuntando a la organización TASALO-TEAM; el perfil personal es el de arriba).
4. Huella SHA-256 del certificado de firma: mismo texto y misma `SelectionContainer` + `TasaloMono` + `labelSmall`, pero centrado y dentro de una caja redondeada `surfaceVariant`.
5. Aviso «Las tasas son referenciales. TASALO no es una aplicación oficial.» centrado (`bodySmall`).
**Avatar real**: `AsyncImage` de Coil (ya en el proyecto: `libs.coil.compose`, lo usa `BlogScreen`) con `model = "https://github.com/ersus93.png?size=160"` (GitHub redirige a la foto actual de ersus93, así que si la cambia, la app la sigue sin tocar nada), `contentScale = ContentScale.Crop`, `Modifier.size(64.dp).clip(CircleShape)` y `contentDescription = "Foto de perfil de ersus93 en GitHub"`. Si falla (sin conexión), muestra un círculo `surfaceVariant` con `ic_social_code`; no debe haber error visible ni reintentos propios. Define `private const val DEVELOPER_GITHUB = "ersus93"` y construye ambas URLs desde ahí. INTERNET ya está en el manifest. **No guardes la imagen en el repo.**

## Comportamiento (copiar de `ui/notifications/NotificationsScreen.kt`, `ReleaseItem`/`AlertItem`)
- Cada categoría es una `GlassCard(Modifier.fillMaxWidth().clickable(onClick = onToggle))` con `Column(padding 14–16.dp)`: fila con icono + `Column(weight 1f)` (título `titleMedium` bold + resumen `bodySmall` en `onSurfaceVariant`) + chevron; debajo, si `expanded`, el contenido.
- Estado: `var expanded by rememberSaveable { mutableStateOf<String?>(null) }` → **una sola abierta a la vez**, todas cerradas al entrar (igual que Notificaciones). Tocar la abierta la cierra.
- Chevron `M6 9l6 6 6-6` que gira 180° al abrir (`animateFloatAsState`); en Notificaciones hoy usan texto, aquí vector.
- Accesibilidad: fila ≥ 48 dp; `semantics { role = Role.Button; stateDescription = if (expanded) "Expandido" else "Contraído" }`; iconos decorativos con `contentDescription = null`.
- Dentro de una categoría con varias secciones (Apariencia, Tasas, Diagnóstico y avanzado) conserva el título pequeño de cada una (`Section` actual, `titleSmall` en `primary`); en categorías de una sola sección (Actualizaciones, Acerca de) **no repitas el título**.
- Espaciado: `spacedBy(10.dp)` entre tarjetas; deja el `floatingContentPadding()` actual de la `LazyColumn`.

## Mover el selector de vista (hecho en v0.12.0, hay que MOVERLO, no duplicarlo)
1. `HomeScreen.kt`: borrar `ViewToggle`, `ViewToggleButton`, sus imports no usados, el parámetro `onToggleListView` y los parámetros `listView`/`onToggleListView` de `Header(...)`. Mantén `listView: Boolean` en `HomeScreen` (solo lectura, viene de `state.settings.ratesListView`). Revisa si el `Modifier.weight(1f)` del `Column` de «TASALO» sigue haciendo falta (inofensivo).
2. `MainActivity.kt`: quitar `onToggleListView = vm::setRatesListView` de la llamada a `HomeScreen`.
3. `SettingsScreen.kt`, en Apariencia: `AdaptiveSegmentedChoice(options = listOf(false to "Tarjetas", true to "Lista"), isSelected = { it.first == settings.ratesListView }, label = { it.second }, onSelect = { vm.setRatesListView(it.first) })`. Texto de ayuda: «Cómo se muestran las monedas en la pantalla Tasas». `vm.setRatesListView` y `SettingsStore.setRatesListView` ya existen.
4. `NavIcons.CardsView/ListView` quedan sin uso: bórralos (y `roundRect`) salvo que los uses como icono en las opciones. No dejes código muerto.

## Calidad / pruebas
- Función pura `settingsSummaries(settings: AppSettings, version: String, newVersion: String?): Map<String, String>` (sin Android) + `SettingsSummaryTest` (tema/vista/colores, 0 min = «solo manual», con y sin versión nueva, fallos sí/no).
- No cambies claves del DataStore ni valores por defecto (nadie pierde sus ajustes).
- CHANGELOG `## 0.13.0` → «### Ajustes por categorías» (4 categorías colapsables como Notificaciones; el selector tarjetas/lista pasa de Tasas a Ajustes → Apariencia; «Acerca de» centrado con el perfil de GitHub del desarrollador y su foto). `gradle.properties`: `VERSION_NAME=0.13.0`.
- Flujo habitual: parche con script Python con `assert count == 1` por reemplazo, commit, `git push origin main`, `gh run list -L 1` y esperar Release (la Release solo se publica con CI verde). Esperar en tandas cortas (el MCP corta a ~4 min). No commitear `.claude/` ni `uv.lock`.

## Pendiente heredado (no tocar sin que el usuario lo pida)
- Desplegar `taso-api` (`daily`/`summary`) en el VPS: lo hace el usuario; hasta entonces no hay curvas en tarjetas/widgets.
- Comprobar en un móvil real: widgets nuevos, iconos de la barra, cabecera de Tasas y el selector.
- Backlog: recordar rango del detalle (requiere `SettingsStore`), leer la skill `ui-ux-pro-max`.
- Si este bloque se completa, marcar aquí `[x]` y subir la versión en «Estado».


---

# SESIÓN 2026-11-06 — Acceso a taso-inv desde la app (PLAN, sin código)
Pedido de Ernesto: pestaña Blog → cupcake que abre camiersus.duckdns.org (tienda/herramienta de taso-inv); Blog pasa a Tasas junto a la campana. Quiere que la app cambie lo menos posible cuando crezca taso-inv.
- [~] Plan completo y decisiones: `C:\Users\ernes\Documents\tasalo\docs\plans\2026-11-06-taso-android-acceso-taso-inv.md` (WebView en la pestaña 3, Blog como overlay, v0.16.0). **Falta el «continuar» de Ernesto**; no escribas código antes.
- Hallazgos clave: sesión de taso-inv = cookie HttpOnly SameSite=Strict (sin tokens), así que un WebView sirve y una pantalla nativa exigiría API nueva; Google no deja iniciar sesión en WebViews (ocultar su botón con UA `TasaloAndroid`); `input type=file` (catalogo/marca) pide `onShowFileChooser`; `window.print()` no funciona en WebView.
- Orden: primero taso-inv (ocultar Google por UA), luego esta app. No compilar en local: CI de GitHub.
- Dominio comprobado: `https://camiersus.duckdns.org/health` → 200 (taso-inv 0.97.0.1), servido desde la laptop.

- (2026-11-06, 2.º mensaje) Ernesto pidió que el WebView se sienta nativo y funcione como el navegador: el plan de taso-inv añade la sección §10 (puente `TasaloAndroid` con `print()` y Google nativo, tema sincronizado, v0.16.0 / v0.17.0). Google nativo exige que Ernesto cree un ID de cliente OAuth Android en Google Cloud (paquete + SHA-1 del keystore).
- (2026-11-06) QvaPay: nueva moneda **CUPCASH** (Cash) sin mostrar porque taso-api pide solo 9 códigos fijos; Ernesto quiere reconocimiento automático sin actualizar APK. Plan: `tasalo\docs\plans\2026-11-06-qvapay-monedas-automaticas.md` (API descubre monedas vía `/coins/v2` con límite por 429; campos `name/kind/count`; la app los lee). Toca `taso-api`: antes de empezar, que Ernesto diga «continuar». Desplegar la API lo hace él.

- (2026-11-06, noche) **CUPCASH hecho**: app v0.15.1 (commit 63227b7, solo Models.kt + CHANGELOG + gradle.properties) y taso-api v0.11.0.0 (8d61f72). Detalle en `docs\plans\2026-11-06-qvapay-monedas-automaticas.md` §5. Falta que Ernesto despliegue taso-api en el VPS.
- **(Resuelto: lo revisé, lo adopté y lo terminé en v0.16.0, ver la nota siguiente.) Trabajo previo sin commitear:** `MainActivity.kt`, `ui/components/NavIcons.kt` y `ui/tienda/` (TiendaController/TiendaNav/TiendaScreen, escritos hacia las 16:25) son un inicio de la pestaña Tienda de v0.16.0 que NO hice yo en esta sesión; no los toqué ni los incluí en el commit. Revisarlos contra el plan antes de seguir (no los pises). La versión 0.16.0 sigue sin publicarse.

- (2026-11-06, noche) **v0.16.0 terminada en código (sin probar en móvil):** pestaña Tienda (`ui/tienda/` TiendaController + TiendaScreen + TiendaNav, puente `TasaloAndroid.print()`), icono Cupcake, Blog como overlay abierto desde Tasas (botón junto a la campana + flecha «Volver a Tasas» en `BlogScreen`), `TiendaNavTest`, CHANGELOG y `VERSION_NAME=0.16.0`. El lado taso-inv (Imprimir por el puente y sin Google en el WebView) ya estaba en taso-inv `15bb150`. No se compiló en local: mirar el CI (`gh run list -L 3`) y corregir lo que falle.
- **Para probar en el móvil (checklist del plan §7.3):** abrir la pestaña, entrar con clave, entrar con Telegram, subir foto como admin, imprimir una ficha, cerrar y reabrir (sesión), rotar, laptop/VPS caído (pantalla de error), «atrás», Blog desde Tasas y su flecha.
- Pendiente v0.17.0: Google nativo (necesita el ID de cliente OAuth Android que debe crear Ernesto) y tema sincronizado app↔web (ver plan §10).


---

# SESIÓN 2026-11-07 — v0.17.0 EN CURSO: Google por el navegador + revisión UI/UX (una sola APK)
Decisión de Ernesto (7 nov): NO se hace el Google «nativo» (Credential Manager) que decía el plan; en su lugar **el botón «Continuar con Google» sale en la app y manda al navegador**, y todo va junto con la revisión UI/UX en **una sola APK (v0.17.0)**. Pidió dejar todo anotado por si se acaban los créditos. Después (versiones aparte, ya aprobadas en el plan `tasalo\docs\plans\2026-11-07-android-ux-premium-notificaciones-privacidad.md`): notificaciones del bot en el teléfono y descargo de privacidad.
Reglas de este repo que se mantienen: SIN dependencias nuevas (por eso el navegador se abre con un `Intent` ACTION_VIEW, no con androidx.browser) y SIN colores nuevos (Quiet Glass v3). Sin SDK local: se sube a `main` y se mira el CI.

## Diseño del flujo Google (PKCE, sin secreto de cliente; reutiliza el client ID y la verificación del id_token de taso-inv)
1. La web de taso-inv (en el WebView, UA `TasaloAndroid/…`) genera un *verificador* aleatorio, lo guarda en `localStorage` (caduca a los 10 min) y calcula el *desafío* = base64url(SHA-256(verificador)). Llama a `window.TasaloAndroid.googleLogin("https://camiersus.duckdns.org/google-app.html?c=<desafio>")`.
2. La app valida que la llamada viene de una página de taso-inv y que la URL es https, de `camiersus.duckdns.org` y de `/google-app.html`; entonces abre el **navegador del sistema** (`Intent.ACTION_VIEW`).
3. `google-app.html` (taso-inv) usa Google Identity Services en un navegador normal; el `id_token` va por POST a `/api/auth/google/app` junto al desafío; el servidor lo verifica igual que el login normal y guarda la identidad con un **código de un solo uso (2 min)** ligado al desafío. La página enlaza a `tasalo://login?codigo=<codigo>` (con botón «Volver a la app» por si el navegador no abre solo el enlace).
4. `MainActivity` (singleTop, intent-filter `tasalo://login`) recibe el código y lo pasa a la pestaña Tienda, que lo entrega al WebView con `window.tasaloGoogleCodigo(codigo)`.
5. La web canjea `POST /api/auth/google/app/canjear {codigo, verificador}`: el servidor comprueba SHA-256(verificador) == desafío (comparación en tiempo constante), quema el código y crea la sesión (cookie HttpOnly **en el WebView**). Quien intercepte el enlace profundo no puede canjearlo sin el verificador.
- Fuera de alcance: «Vincular Google» desde Mi cuenta dentro de la app (sigue oculto en el WebView).

## Checklist v0.17.0 (marcar al terminar cada paso)
- [x] HECHO (taso-inv 0.101.0.0, commit 9912fae, desplegado por git pull en el VPS; FALTA reiniciar taso-inv con sudo) taso-inv backend: `app/google_app.py` (códigos en memoria, TTL, un solo uso, tope) + rutas `/api/auth/google/app` y `/app/canjear` + pruebas.
- [x] HECHO (mismo commit; 20/20 JS y pytest completo en verde; NO probado en navegador/móvil real todavía) taso-inv web: `web/google-app.html` + `web/js/google-app.js`, `lib/google_app.js` (puro, con pruebas `node --test`), `login.js` (botón en la app), `lib/app.js`.
- [x] HECHO (sin compilar en local; lo valida el CI) taso-android: puente `googleLogin` (`TiendaBridge` → `TiendaController.openGoogleLogin`, solo si la página es de taso-inv y la URL es exactamente `/google-app.html`), `AndroidManifest` (intent-filter `tasalo://login`), `MainActivity.handleIntent` → `MainViewModel.googleCode` → `TiendaController.deliverGoogleCode` (espera a que cargue la página; si la web aún no pintó el login deja el código en `window.__tasaloGoogleCodigo`), pruebas de `TiendaNav` (`isGoogleBrowserUrl`, `googleCodeFromLink`, `isGoogleCode`).
- DECISIÓN 7 nov (tarde): Ernesto pidió **desplegar por secciones** (consolidar → desplegar → seguir). Por eso la 0.17.0 sale SOLO con Google por el navegador; la revisión UI/UX pasa a **0.18.0** (auditoría primero; solo cambios dentro de Quiet Glass v3).
- [x] `VERSION_NAME=0.17.0` + `CHANGELOG.md` (texto para usuarios).
- [x] taso-inv ya está desplegado y el servicio reiniciado en el VPS (el 7 nov `POST /api/auth/google/app/canjear` responde con el mensaje propio de la app, no 404/405). `ssh pan` no tiene `sudo` sin clave.
- [x] CI de GitHub en verde y Release `v0.17.0` publicado como Latest (commit `8fb4617`, 7 nov). PENDIENTE solo la prueba en un móvil real: botón de Google → navegador → volver a la app con la sesión iniciada.
- Nota: en el árbol quedaban 4 líneas sin commitear en `HomeScreen.kt` (parámetro `onOpenBlog` y argumento `onBlog` DUPLICADOS: no compilaban). Se revirtieron con `git checkout`; copia del parche en `%TEMP%\homescreen-sin-commitear.patch`.

# SESION 2026-11-07 (noche) - v0.17.1 PUBLICADA (PR #6 fusionado): descargo de privacidad de taso-inv (Parte 4 del plan)
Plan: `tasalo\docs\plans\2026-11-07-android-ux-premium-notificaciones-privacidad.md`. Rama **`feat/privacidad-taso-inv`** (NO está en `main`): el texto legal lo revisa Ernesto antes de publicar. El CI corre en el PR sin publicar Release; al fusionar en `main` sale la 0.17.1.
- [x] Auditoría de solo lectura de taso-inv v0.101.0.0 (migraciones 0001-0020, `app/auth.py`, `cuentas.py`, `google_login.py`, `telegram_login.py`, `respaldo.py`, `web/js/views/cuenta.js`, bloque `log` de Caddy en el VPS). Lo que hay en `docs/PRIVACIDAD.md` es solo lo confirmado ahí.
- [x] `docs/PRIVACIDAD.md` (texto completo), `domain/PrivacyNotice.kt` (resumen corto, mismo contenido) y categoría **Privacidad** en Ajustes (`SettingsCategories.PRIVACY`, `NavIcons.Privacy`, `PrivacyNoticeContent` en `SettingsScreen.kt`) con botón «Ver el texto completo». Pruebas: `PrivacyNoticeTest` y un caso nuevo en `SettingsSummaryTest`. `VERSION_NAME=0.17.1` + CHANGELOG. Sin compilar en local (no hay SDK): lo valida el CI.
- Regla: si cambia lo que guarda taso-inv, actualizar `docs/PRIVACIDAD.md` y `PrivacyNotice.kt` a la vez.

## Pendientes de decisión de Ernesto antes de fusionar (hallazgos de la auditoría)
1. **Sesiones vencidas nunca se borran** en taso-inv: `sesiones` (con IP y navegador) solo se borra al cerrar sesión, cambiar la clave o eliminar la cuenta. El texto dice «mientras tengas la cuenta», que es verdad hoy, pero lo razonable es purgar las vencidas (p. ej. en `auth.iniciar`). Es un cambio pequeño en taso-inv (otro repo + despliegue): requiere visto bueno.
2. **Canal de contacto**: el texto dice «pídelo al negocio por el mismo medio con el que haces tus pedidos». Si hay un correo o @usuario concreto, ponerlo.
3. **Personal** no puede descargar sus datos ni darse de baja solo (lo hace un admin). El texto lo dice así.
4. El texto no cita leyes (decisión del plan). Revisarlo con alguien que sepa si el negocio crece.
5. Dentro de la app el vínculo con Google (Mi cuenta) sigue oculto en el WebView; el texto lo aclara solo en `docs/PRIVACIDAD.md`.

## Siguiente (orden del plan)
Parte 3: notificaciones del bot en el teléfono. Opción A (local desde `RefreshWorker`, sin Firebase) salvo que Ernesto pida tiempo real con FCM. Luego revisión UI/UX 0.18.0 por versiones pequeñas.


# SESION 2026-11-07 (madrugada) - v0.18.0 PUBLICADA (PR #7 fusionado): avisos de alertas del bot en el telefono (Parte 3 del plan)
Rama **`feat/notificaciones-bot`**, PR **#7** (apilado sobre el #6 de privacidad: fusionar primero #6, luego #7). **CI verde** (build 2m14s). No se publica Release hasta fusionar en `main`. NO probado en un movil real.
- Opcion A elegida por Ernesto: notificacion **local** desde `RefreshWorker` (sin Firebase). El worker ya descarga los mensajes (`RefreshResult.messages`); despues del refresco llama a `notifyNewAlerts` (en `RefreshWorker.kt`), que solo actua si `settings.alertNotifications` esta activo.
- `domain/AlertNotices.kt` (puro, probado en `AlertNoticesTest`): decide que alertas avisar (id mayor que la ultima avisada y que la ultima vista en la app), un aviso por tanda (una alerta = su titulo + adelanto sin Markdown; varias = «N alertas nuevas» + titulo de la mas reciente).
- `work/AlertNotifier.kt`: canal `alertas_bot`, comprueba permiso `POST_NOTIFICATIONS` (Android 13+) y que las notificaciones no esten bloqueadas; el id avisado SOLO se guarda si de verdad se mostro. El tap abre `MainActivity` con `EXTRA_OPEN_ALERTS` → `MainViewModel.openAlerts` → abre la pantalla de Notificaciones.
- Ajustes: dentro de la categoria **Tasas**, seccion «Avisos de alertas del bot» (`AlertNotifySection` en `SettingsScreen.kt`): interruptor APAGADO por defecto; al activar pide el permiso en Android 13+; avisos si esta denegado/bloqueado, boton «Sonido y tipo de aviso» (pantalla del canal) y aviso si la actualizacion en segundo plano esta en Manual (sin ella no llegan avisos). Al activar se fija una linea base (`setAlertNotifications(on, baseline)`): solo avisa de lo posterior.
- Claves nuevas del DataStore: `alert_notifications`, `last_notified_message` (no se tocaron las existentes). `VERSION_NAME=0.18.0`, CHANGELOG y README (permisos) actualizados.
- Para probar en el movil: activar el interruptor (Android 13+: aceptar el permiso), mandar `/msapp` desde el bot, esperar el refresco (o forzarlo), ver la notificacion, tocarla (debe abrir Notificaciones), comprobar que no se repite, y con la app abierta/alerta ya vista no debe avisar.
- Limites honestos: el aviso llega con el intervalo del refresco (15 min minimo en WorkManager), no en tiempo real; si Ernesto quiere tiempo real hace falta FCM (Firebase), cambio mayor.
- Parte legal (PR #6): se ajusto el texto para el personal segun el Codigo de Trabajo (commit 404276c). Siguen pendientes de decision de Ernesto: purgar sesiones vencidas en taso-inv, canal de contacto concreto para pedir borrado.
- Siguiente: revision UI/UX 0.18.x por versiones pequenas (auditoria primero, solo dentro de Quiet Glass v3).


# SESION 2026-11-08 - v0.18.1: boton «Pedir borrado de mis datos» (privacidad)
- PR #6 (v0.17.1) y #7 (v0.18.0) ya fusionados en `main`; Releases v0.17.1 y v0.18.0 publicadas; ramas viejas borradas (solo queda `main`). La calculadora v0.7.0 ya estaba en `main` con otro hash.
- Ernesto decidio el contacto para pedir borrado de datos: **tasaloteam@gmail.com**. Rama `feat/privacidad-contacto-borrado`.
- `PrivacyNotice` (domain, puro): `CONTACT_EMAIL`, `DELETION_SUBJECT`, `DELETION_BODY` (mensaje para completar y editar). El texto de «Tus opciones» ya no dice «por el mismo medio de tus pedidos»: cita el correo. `UPDATED` = 8 de noviembre de 2026 (igual en `docs/PRIVACIDAD.md`, que tambien cita el correo).
- `SettingsScreen.kt` (`PrivacyNoticeContent`): boton «Pedir borrado de mis datos» → `ACTION_SENDTO mailto:` con destinatario, asunto y cuerpo; el usuario edita antes de enviar. Sin app de correo: copia el texto al portapapeles y avisa. No hace falta permiso ni cambio de manifest (mismo patron que `ReportSender`).
- Pendiente de decision de Ernesto: purgar sesiones vencidas en taso-inv (guardan IP y navegador hasta eliminar la cuenta; el texto dice «mientras tengas la cuenta»). Cambio pequeno en taso-inv + despliegue en el VPS (`ssh pan`).
- Sin probar en movil: abrir el boton y ver que Gmail/otra app trae asunto y cuerpo; probar sin app de correo.

- 2026-11-08: v0.18.0 y v0.18.1 ya estan publicadas (la v0.18.0 ya NO esta en revision). v0.19.0: avisos de alertas activos por defecto (AlertPrompt.kt; en Android 13+ pregunta una vez, en 12 o menos quedan activos sin preguntar; quien ya eligio a mano conserva su eleccion; el worker no avisa de lo viejo la primera vez). CI verde y Release publicada. Sin probar en movil real. Siguiente: auditoria UI/UX (solo dentro de Quiet Glass v3), en versiones pequenas.


# SESION 2026-11-08 (tarde) - Auditoria UI/UX hecha; v0.19.1 EN CURSO
Ernesto pidio la revision UI/UX «profesional» en versiones pequenas y dejar todo anotado por si se acaban los creditos.
- **La auditoria completa esta en `docs/AUDITORIA_UI_UX.md`** (20 hallazgos A1-A20 con archivo, lineas aprox., arreglo y casilla de estado; orden de entrega 0.19.1 -> 0.20.0 -> 0.20.1 -> 0.21.0). Es solo de codigo: lo marcado «verificar» hay que probarlo en un movil real (no hay SDK ni emulador).
- Reglas que se mantienen: dentro de Quiet Glass v3 (radios 22/28 y turquesa son marca), SIN dependencias ni colores nuevos, cambios minimos, `ESCALADO_DE_FUENTE.md` (se cambia la distribucion, nunca se recorta), `ContrastTest` en verde. No hay SDK local: se sube a `main` y se mira `gh run list -L 3`.
- Correccion hecha durante el trabajo: el campo de texto del Blog esta dentro de un `AlertDialog`, que maneja el teclado solo; **no** necesita `imePadding` (A4 es solo Ajustes).
- Herramientas: graphify NO tiene grafo de este repo (no existe `graphify-out/graph.json`); la consola de PowerShell 5.1 muestra mal los acentos de archivos UTF-8 (no es corrupcion; los archivos estan bien: usar `read_file` de Desktop Commander o `Get-Content -Encoding UTF8`). Los archivos usan fin de linea LF.
- **Plan de la 0.19.1:** A1 Calculadora sin recorte, A2 TalkBack con superpuestas, A3 tarjetas de Notificaciones, A4 teclado en Ajustes. Marcar cada una en `docs/AUDITORIA_UI_UX.md` al terminarla y subir `VERSION_NAME` + CHANGELOG (texto para usuarios).
- Estado de la 0.19.1: (se actualiza abajo, linea a linea, segun avance)


# SESION 2026-11-08 (noche) - otro agente retoma: analisis de avisos de pedidos + 0.19.1
- **Estado real al llegar:** `main` limpio (solo `.claude/` sin trackear), HEAD `923022f`. La 0.19.1 NO tenia codigo todavia (A1-A4 sin tocar). Graphify sigue sin grafo de este repo.
- **Analisis hecho (sin codigo): avisos de estado de pedidos de taso-inv en el telefono.** Plan: `tasalo\docs\plans\2026-11-08-android-avisos-pedidos-taso-inv.md`. Conclusion: taso-inv YA tiene `GET /api/avisos` (tipos `pedido_estado` para el cliente y `pedido_nuevo` para el personal) y la app YA tiene la sesion (cookie `taso_sesion` en el WebView) y el motor de avisos locales (`RefreshWorker`). Recomendado: opcion A = el worker lee la cookie con `CookieManager`, consulta `/api/avisos` y notifica; sin cambios en taso-inv, sin Firebase; retraso 15-30 min. **Falta el «continua» de Ernesto**; no escribas codigo de esto antes. Decisiones por defecto en §6 del plan.
- Aviso de seguridad a decirle: el sondeo refresca `ultimo_uso`, asi que la sesion no caduca a los 10 dias de inactividad mientras el aviso este activo (sigue el tope de 30 dias y cerrar sesion la corta).
- Orden acordado: 0.19.1 (A1-A4 de `docs/AUDITORIA_UI_UX.md`) -> avisos de pedidos (0.19.2 si lo aprueba) -> 0.20.0 -> 0.20.1 -> 0.21.0.
- Estado de la 0.19.1 (se actualiza linea a linea):
- 2026-11-08 (noche): A1-A4 escritos, **sin compilar en local (no hay SDK)**: A1 `CalculatorScreen.kt` (resultado y filas de la factura en `FlowRow`, sin `maxLines`; ojo: el resultado ya no lleva `Spacer` y la fila de factura usa pesos 2:1), A2 `MainActivity.kt` (`clearAndSetSemantics` en el `HorizontalPager` si hay Notificaciones/Blog/Detalle abiertos), A3 `NotificationsScreen.kt` (`Modifier.expandable` + `ExpandChevron`, privados; el `ExpandableCard` comun sigue para 0.20.0), A4 `SettingsScreen.kt` (`imePadding` + sin hueco de la capsula con teclado, igual que Efectivo). `VERSION_NAME=0.19.1` + CHANGELOG + casillas A1-A4 marcadas en `docs/AUDITORIA_UI_UX.md`. Siguiente: mirar `gh run list -L 3`; si falla, leer el log (`gh run view <id> --log-failed`) y corregir. Probar en movil: Calculadora con 1.000+ USD y letra al 200 %, TalkBack con Notificaciones abiertas, teclado en Ajustes → Diagnostico y avanzado → URL base.
- 2026-11-08 (noche): **v0.19.1 CI verde (run 42513880622, commit 5bb255a)**; Release segun `gh release list -L 1`. Pendiente: probar en movil. Siguiente: avisos de pedidos (plan nuevo, espera el continua de Ernesto) o 0.20.0 (A9, A10, A17: base comun) segun lo que diga.

- 2026-11-08 (noche, otro agente, ponytail): **v0.20.0 escrita y subida, sin compilar en local**: A10 (iconos Refresh/Bell/Close/SwapVertical/Copy/Share/Edit/Add en NavIcons; Calculadora, Blog y Tasas ya no usan Icons.Filled), A17 (RateCard con RateTitle/RateValue comunes; marcadores unificados), A19 (imports sin uso de MainActivity), A12 (selector de moneda min 48 dp y filas de factura con role Button), A9 reducido: padding de tarjeta unificado a 16 dp (RateCard, Notificaciones, Blog) y CurrencyPicker con shapes.small. DECISION ponytail: NO se creo un objeto Spacing/Radius ni se migraron los ~37 valores (codigo sin uso = peso muerto) ni el ExpandableCard comun (Notificaciones y Ajustes ya comparten el patron en privado); retomar solo si se vuelve a repetir. Siguiente: mirar gh run list -L 3 (si falla, gh run view <id> --log-failed) y luego 0.20.1 = A6, A7, A8, A5 (movimiento). Pendiente de Ernesto: el continua para los avisos de pedidos (plan 2026-11-08-android-avisos-pedidos-taso-inv.md).
- 2026-11-08 (noche): **v0.20.0 CI verde (run 42516563451, commit f592cbd)**. Pendiente: probar en movil (iconos nuevos en Tasas/Calculadora/Blog, lista vs cuadricula de Tasas, selector de moneda). Siguiente: 0.20.1 (A6, A7, A8, A5).
- 2026-11-08 (noche): **v0.20.1 EN CURSO** (Ernesto dijo continuar y dejar todo anotado). Plan: A6 un solo LocalReduceMotion (CollapsingHeader, isla, parallax del paginador, grafico 650 ms, slide del Detalle), A7 mismo fundido de 200 ms para Notificaciones/Blog/Detalle, A8 enableOnBackInvokedCallback + revisar los 4 BackHandler, A5 isla con selectableGroup, etiqueta en la pestana activa y foco visible. Si ves esto sin linea de 'hecho' debajo, mira git log y git status: puede haber trabajo a medias.
- 2026-11-08 (noche): **v0.20.1 escrita y subida, sin compilar en local**: A6 LocalReduceMotion (ui/theme/ReduceMotion.kt, se provee en TasaloTheme; lo usan CollapsingHeader, isla, parallax del paginador, salto entre pestanas, grafico y entrada del Detalle), A7 Blog y Notificaciones con AnimatedVisibility fundido 200 ms (entrada y salida) como el Detalle, A8 enableOnBackInvokedCallback=true en <application> (los 4 BackHandler de MainActivity + el de TiendaScreen siguen valiendo; PROBAR en Android 13+ con el gesto de atras en Notificaciones, Blog con un post abierto, Detalle y Tienda con historial), A5 isla con selectableGroup, etiqueta (10 sp) solo en la pestana activa y fontScale <= 1.15, icono sin contentDescription cuando la etiqueta se ve, y foco visible con borde. Siguiente: mirar gh run list -L 3; luego 0.21.0 = A16, A18, A15, A14, A13, A11 (orden en AUDITORIA_UI_UX.md).
