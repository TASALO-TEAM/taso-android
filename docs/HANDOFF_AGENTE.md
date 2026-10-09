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
- Navegación: `MainActivity` con un `HorizontalPager` de 5 secciones (Tasas · Combustible · Calculadora · Blog · Ajustes) + `GlassIslandBar`. Las pantallas
  que van «encima» (Notificaciones y el **detalle histórico**) son overlays dentro de `TasaloRoot`, con `BackHandler`.
- Tema: `ui/theme/Theme.kt` («Quiet Glass» v3: **turquesa `#3DD6C0` sobre azul noche `#0E1621`**). `ContrastTest` exige WCAG (4,5:1 texto, 3:1 UI): al tocar colores,
  recalcular. Sube/baja: `ChangeColors` + ajuste `invertColors` (por defecto **sube rojo, baja verde**). Widgets: `widget/WidgetUi.kt` (`WidgetPalette`).
- Datos: `TasaloRepository` (tasas, combustible, año, mensajes; caché en DataStore) y **`HistoryRepository`** (historial, caché en `filesDir/history/*.csv`).
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
URL: https://claude.ai/artifact/Ba4ZDY9C3rqD9As8L7jWKx (tipo Design: **no cambiar a otro tipo**). Tableros: `Main` (detalle), `Tasas`, `Combustible`, `Calculadora`, `Ajustes`, `Corto` (historial corto QvaPay).
Se genera con scripts Python (`/tmp/gen.py`, `/tmp/gen2.py` del sandbox, pueden no existir): HTML estático por tablero (`*.dc.html`) + `canvas.json`, publicado con la herramienta `Artifact` (action publish, `url` del artefacto, `file_path` + `files`).
Estilo: fondo `#0E1621`, tarjetas `#16212F`, acento `#3DD6C0`, coral `#FF8F7E`, texto `#E8EEF5`/`#9FB0C3`, Manrope, esquinas 20 px, barra inferior tipo cápsula.

## Estado
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
