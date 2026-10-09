# Historial de precios (v0.10.0)

Detalle histórico por fuente y moneda, y tendencia de 30 días de fondo en las tarjetas de Tasas.

## Decisiones (Fase 2)

- **Stack real:** Jetpack Compose + Material 3 (BOM 2024.12.01), `minSdk` 26, DI manual, sin librería de navegación.
- **Detalle = pantalla a pantalla completa superpuesta**, igual que Notificaciones: cabe gráfico + lista, el botón atrás
  funciona con un `BackHandler` y no se añade una dependencia de navegación.
- **Gráfico dibujado en `Canvas`** (sin librería): una sola curva con relleno, así que una dependencia de gráficos no aporta
  nada y se evita peso y riesgo de compatibilidad. Tocar o deslizar muestra el punto más cercano; los días sin dato se unen
  con trazo discontinuo para que el hueco se vea.
- **Fondo de la tarjeta:** `SparklineBackground` (trazo suave + relleno muy tenue, con `blur` en Android 12+). Color según la
  tendencia de los 30 días y la convención sube/baja de Ajustes. Es decorativo: el precio y el cambio siguen en texto.
- **Carga y caché:** las tarjetas usan UNA llamada liviana (`summary`, 30 días) cacheada 30 min en el móvil; el historial largo
  (180 días) se pide solo al abrir el detalle, de una sola moneda, y se guarda 30 min. Solo se guardan series ya reducidas a un
  precio por día (archivos CSV en `filesDir/history`).
- **Rango del gráfico:** 7D / 30D / 90D / 6M + fechas (selector de rango limitado a los datos existentes). **Lista:** 7 / 14 / 30 días.
  No se recuerda la última elección (no es trivial con el `SettingsStore` actual); queda como mejora.
- **Movimiento:** entrada con fundido + resorte (el mismo `spring(0.85, 380)` de la barra) y la curva se dibuja de izquierda a derecha.
- **Skill `ui-ux-pro-max`:** existe en `.claude/skills/`, pero no se leyó para esta versión; las decisiones de gráfico,
  accesibilidad y movimiento salen del análisis del propio código (contraste con `ContrastTest`, `contentDescription` en el gráfico y en cada fila).

## Datos: qué ofrece hoy la API

`GET /api/v1/tasas/history?source=&currency=&days=` (1–365) **ya existe**, pero devuelve las lecturas sueltas (una cada 15 min,
~96 por día, orden descendente, sin límite): unos 17 000 registros para 6 meses de una sola moneda. No sirve para el fondo de todas
las tarjetas y es pesado para el detalle. Además no agrupa por día de Cuba ni elige la lectura de las 7:00.

Hoy el detalle usa ese endpoint como plan B y hace la reducción en el móvil (`PriceHistory.daily`). Es correcto pero lento.

## Contrato mínimo propuesto (pendiente de implementar en `taso-api`)

La reducción debería hacerse en el servidor (una vez, con índice por `fetched_at`):

1. **Serie diaria** — `GET /api/v1/tasas/history/daily?source=eltoque&currency=USD&days=180`
   ```json
   {"ok": true, "source": "eltoque", "currency": "USD", "tz": "America/Havana",
    "data": [{"date": "2026-10-07", "rate": 515.0}, {"date": "2026-10-08", "rate": 520.0}]}
   ```
   Un punto por fecha de Cuba: la lectura más cercana a las 7:00 de ese día. Los días sin lectura **no** aparecen (el cliente muestra «sin dato»).
   Para QvaPay, `rate` = promedio de compra y venta.

2. **Resumen para tarjetas** — `GET /api/v1/tasas/history/summary?days=30`
   ```json
   {"ok": true, "tz": "America/Havana", "days": 30,
    "data": {"eltoque": {"USD": [{"date": "2026-09-09", "rate": 505.0}, ...], "EUR": [...]},
             "qvapay": {"CUP": [...]}}}
   ```
   Misma regla diaria, todas las fuentes y monedas en una sola respuesta (~1 000 puntos, ~20 KB).

Mientras `summary` no exista (404), las tarjetas se ven sin fondo y la app no vuelve a pedirlo en esa sesión.
Cuando `daily` exista, el cliente lo usa solo, sin cambios de la app (cae al endpoint actual si responde 404).

## Pendiente / mejoras

- Implementar los dos endpoints en `taso-api` (esperando visto bueno).
- Recordar el último rango y los días de la lista en `SettingsStore`.
- Historial y fondo también en Combustible (la API guarda esos precios en `rate_snapshots` con `source='fuel'`, pero la app aún no los usa).
