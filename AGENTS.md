# AGENTS.md

## Project Shape
- Android app module is `:app`; embedded Ktor/API library is `:server`. Keep `:server` independent from `:app`; `:app` adapts its repository through `RainNoteRepositoryService`.
- Product wording is "卡片集", but internal model/API names remain `Note`/`notes` for compatibility (`/api/notes`, DB `notes`, Kotlin `Note*`). Do not rename these internals casually.
- `MainActivity` starts both `HttpLogServer` on `48621` and `RainNoteServerManager`/Ktor on `48622`.

## Build And Verification
- Preferred focused Android check: `./gradlew.bat :app:processDebugResources :app:compileDebugKotlin --stacktrace`.
- Full check: `./gradlew.bat build --stacktrace`.
- Gradle `preBuild` automatically runs `h5-ui` `npm run build` and copies `h5-ui/dist` into `app/src/main/assets`; do not manually edit copied web assets.
- H5-only check: run `npm run build` from `h5-ui`.
- If Gradle fails before project configuration with `FileLockContentionHandler` / `Address already in use`, run `./gradlew.bat --stop` and retry.
- Expected non-blocking warnings: `@vueuse/core` Rolldown `INVALID_ANNOTATION`; Android deprecated permission / Wi-Fi P2P API warnings.

## H5 Clients
- H5 source lives in `h5-ui`: mobile uses Vue 3 + Vant 4 under `/web-mobile/`; PC uses Vue 3 + Element Plus under `/web-pc/`.
- `vite.config.js` selects `base` and input by mode. `npm run build` builds both mobile and PC; `scripts/fix-pc-index.mjs` renames the PC entry after build.
- Current UI direction is original library components: do not reintroduce global custom CSS patches (`theme.css`, `pc.css`) unless explicitly requested.
- App WebView loads mobile H5 through local Ktor: `/web-mobile/?embedded=1&token=...#/notes`.

## API And Access
- Static `/web-mobile` and `/web-pc` are public; `/api/*` requires `X-RainNote-Token` when the app has an access token.
- Ktor static asset fallback is intentional: missing real asset files return 404; only route-like paths fall back to `index.html` to avoid JS/CSS MIME errors.

## Content Data Contract
- `NoteBlock.content` is a string with type-specific protocol:
  - `plain_text`: sanitized HTML, usually paragraphs like `<p>text</p>`.
  - `rich_text`: sanitized HTML; allowed tags are maintained in `h5-ui/src/utils/blockContent.js`.
  - `code_block`: JSON string `{"language":"plain","code":"..."}`.
- Use `h5-ui/src/utils/blockContent.js` for H5 conversions, sanitization, and old-data migration. Do not duplicate ad-hoc parsing in components.
- Sync/export/import preserve `type` and raw `content`; importing backups overwrites same-title card sets and creates new cards/blocks with local IDs.

## Layout / Native Notes
- Mobile phone layout uses `BottomNavigationView`; tablet/wide layouts use drawer/sidebar variants. Check resource qualifiers before assuming one XML applies everywhere.
- Sync page lists are inside `NestedScrollView`; RecyclerViews should keep nested scrolling enabled so discovered devices and pending card sets can scroll within fixed-height panels.

## Generated / Ignored Files
- `h5-ui/node_modules/`, `h5-ui/dist/`, `app/src/main/assets/web-mobile/`, and `app/src/main/assets/web-pc/` are ignored build outputs.
- `package-lock.json` exists under `h5-ui`; use npm, not another package manager, unless the repo changes.
