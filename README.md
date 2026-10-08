# TASO-ANDROID

App Android nativa de **TASALO**: tasas de **El Toque**, **QvaPay**, **BCC** y **CADECA**, precios de combustible, frase del día y progreso del año, con **widgets** de pantalla de inicio.

Consume únicamente la [`taso-api`](https://github.com/TASALO-TEAM/taso-api) ya desplegada. **No hace scraping, no tiene analytics ni trackers, no usa API keys.**

> Las tasas son referenciales. TASALO no es una aplicación oficial.

## Características

- **Tasas**: selector El Toque | QvaPay | BCC | CADECA, tarjetas con cambio (▲ ▼ —) y %, CADECA y QvaPay con compra y venta.
- **Combustible**: B-94, B-90, B-83, Petróleo y Gas licuado.
- **Año y frase del día**: barra de progreso, días y semanas restantes.
- **Widgets** (Jetpack Glance): Tasas (1–4 monedas), Bloque (fuente completa) y Año y frase.
- Funciona sin red mostrando el último caché, con banner de estado.
- Tema Auto/Oscuro/Claro, colores de subida/bajada invertibles, monedas visibles configurables.

## Stack

Kotlin · Jetpack Compose (Material 3) · Glance · Retrofit + OkHttp · kotlinx-serialization · DataStore · WorkManager (15 min). `minSdk 26`. Un solo módulo `app`, DI manual.

## Permisos

`INTERNET` y `ACCESS_NETWORK_STATE`, más `REQUEST_INSTALL_PACKAGES` y `UPDATE_PACKAGES_WITHOUT_USER_ACTION` para actualizarse a sí misma (el instalador solo acepta un APK del mismo paquete y con la misma firma). Solo HTTPS. `allowBackup=false`.

## Compilar

```bash
./gradlew testDebugUnitTest assembleDebug
```

Requiere JDK 17 y Android SDK (`compileSdk 35`). En GitHub Actions no hace falta instalar nada: cada push a `main` genera el APK debug como artefacto.

## Release firmado

Un tag `vX.Y.Z` dispara el workflow que firma el APK y lo adjunta a la Release. Secretos requeridos: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. El keystore nunca se sube al repo.

## Licencia

MIT — ver [LICENSE](LICENSE).

## Verificar una instalación

Cada Release publica `taso-android-vX.Y.Z.apk.sha256` (huella del APK) y, en sus notas, la **huella SHA-256 del certificado de firma**. La misma huella aparece en *Ajustes → Acerca de* dentro de la app: si coinciden, la app está firmada con la clave oficial de TASALO.