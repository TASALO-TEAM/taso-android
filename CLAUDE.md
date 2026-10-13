# TASO-ANDROID: notas para Claude

Lee antes de tocar nada: `docs/HANDOFF_AGENTE.md` (estado y forma de trabajo) y `docs/ESCALADO_DE_FUENTE.md` (la app respeta la letra grande del sistema: se cambia la distribución, nunca se recorta ni se reduce el texto).

## Skill `ui-ux-pro-max` (`.claude/skills/ui-ux-pro-max`)
- **Cuándo usarla:** al crear o rediseñar pantallas y componentes (diseño, colores, tipografía, espaciado, movimiento) y para **validar** el diseño final: contraste, objetivos táctiles de 48 dp o más, foco y semántica, estados vacío/error, claro y oscuro, letra grande.
- **Cuándo no:** lógica pura, datos, red, CI, documentación.
- **Límite:** valida y completa, pero **no reemplaza la marca**. El diseño es Quiet Glass v3 (`ui/theme/Theme.kt`: `LocalGlass`, Space Grotesk, `TasaloMono`, `TasaloShapes`); no se usan sus paletas, fuentes ni patrones de landing. Sin colores nuevos ni dependencias nuevas.

## Reglas del proyecto
- Cambios mínimos y dirigidos; no refactorizar lo que esté fuera de alcance. No romper nada de lo que funciona en el ecosistema TASALO.
- Sin Android SDK local: revisar imports, nombres y nulabilidad con mucho cuidado; se sube a `main` y se comprueba el CI con `gh run list -L 3`.
- Cada versión nueva: `VERSION_NAME` en `gradle.properties` y sección en `CHANGELOG.md` escrita para los usuarios.
- No commitear `.claude/` ni `uv.lock`.
