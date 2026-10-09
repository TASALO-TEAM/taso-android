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
- [x] v0.11.0 compilada en GitHub (commit ce84ac3): lint, tests y APK verdes, Release v0.11.0 publicada. Pendiente del usuario: desplegar taso-api (daily/summary) para que se vean las curvas; hasta entonces los widgets salen sin curva. Pendiente de comprobar en un móvil real: aspecto de los widgets.

## Cosas que NO se hicieron y se dijeron al usuario
- La skill `.claude/skills/ui-ux-pro-max` existe pero no se leyó.
- No se recuerda el último rango/días del detalle (haría falta `SettingsStore`).
- El indicador de cambio de las tarjetas compara con la **lectura anterior** (15 min), no con «ayer».
