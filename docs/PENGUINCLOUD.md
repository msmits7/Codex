# Projectlogboek — Codex

- **Projectnaam in PenguinCloud:** `Projecten/Codex/`
- **Laatst bijgewerkt:** 2026-10-17
- **Uitvoerende agent:** Claude Code (cloudsessie via claude.ai/code)

Dit bestand is de betrouwbare route om voortgang vast te leggen: het staat in
de repository en wordt later naar de PenguinCloud-vault gesynchroniseerd. Als
de PenguinCloud-MCP beschikbaar is, wordt dezelfde voortgang ook rechtstreeks
in `Projecten/Codex/` geschreven (zie `CLAUDE.md`).

## Doel en aanleiding

De repository `msmits7/Codex` bundelt projecten die met AI-agents (Claude Code,
eerder ook Codex) zijn ontwikkeld. Aanleiding voor dit logboek (2026-10-17): al
het werk met Claude Code, lokaal en online, moet worden vastgelegd in Mikes
Second Brain *PenguinCloud*.

## Status en afbakening

De repository bevat op dit moment twee losstaande projecten:

| Onderdeel | Map | Wat de repo laat zien | Status |
| --- | --- | --- | --- |
| P2000 Live (Android-app) | `p2000-app/` | Kotlin-app met live P2000-meldingen, kaart, filters, historie; 12 unittestbestanden; GitHub Actions-workflow die een debug-APK bouwt; een APK in `p2000-app/apk/` | Actief ontwikkeld (laatste commits 2026-08-18 en later). Of de CI groen is, is in deze sessie **niet gecontroleerd**. |
| TradeBot (Flask-webapp) | `tradebot/`, `render.yaml`, `Procfile`, `requirements.txt`, `docs/RENDER_DEPLOY.md` | Flask-dashboard (`tradebot/web/app.py`), config en modellen; Render-deploymentconfig met `TRADEBOT_MODE=paper` | Laatste commit 2026-06-12. Of deze dienst op Render draait, is **onbekend**; er is geen bewijs van productiegebruik. |

Buiten de afbakening van deze sessie: functionele wijzigingen aan de app of de
tradebot. Deze sessie richt alleen de vastlegging naar PenguinCloud in.

## Repository en omgevingen

- **GitHub:** `msmits7/Codex`.
- **Branches (2026-10-17):** `claude/build-trading-bot-vBkrZ` (standaardbranch
  op GitHub, TradeBot), `claude/android-p2000-app-lhf6rz` (P2000 Live, bevat ook
  de TradeBot-geschiedenis), `ccr-562b4802-km70t1` (deze inrichting).
- **CI:** `.github/workflows/p2000-android.yml` — bouwt de APK en draait
  `testDebugUnitTest` bij pushes naar `p2000-app/**`.
- **Hosting TradeBot:** Render (`render.yaml`, regio Frankfurt, plan free).
  API-sleutels horen in het Render-dashboard, niet in de repo.
- **Claude Code-omgevingen:** lokale CLI (niet getest in deze sessie) en
  cloudsessies via claude.ai/code (getest, zie hieronder).

## Technische opzet en koppelingen

- **`docs/PENGUINCLOUD.md`** (dit bestand) — projectlogboek in de repo.
- **`CLAUDE.md`** — sectie "PenguinCloud" met de werkafspraken voor Claude Code.
- **`.mcp.json`** — remote HTTP-MCP-server `penguincloud`
  (`https://mcp.penguincloud.nl/mcp`), zonder token of headers. Authenticatie
  verloopt via OAuth in de client.
- **`.claude/settings.json`** — registreert een Stop-hook (een script dat Claude
  Code uitvoert op het moment dat Claude wil stoppen met antwoorden).
- **`.claude/hooks/penguincloud-stop.sh`** — de hook zelf (bash + git; jq
  optioneel met fallback). Werking:
  1. Is `stop_hook_active` in de invoer `true` (Claude is al eens door deze hook
     tegengehouden), dan mag Claude altijd stoppen — dit voorkomt een eindeloze
     lus.
  2. Anders verzamelt het script alle gewijzigde bestanden: commits van deze
     branch ten opzichte van de basisbranch, plus gewijzigde, gestagede en
     nieuwe bestanden in de werkboom.
  3. Zijn er wijzigingen buiten `docs/PENGUINCLOUD.md` en is dat bestand zelf
     niet gewijzigd, dan geeft het
     `{"decision":"block","reason":"Werk eerst docs/PENGUINCLOUD.md bij met de wijzigingen van deze sessie."}`
     terug; Claude moet dan eerst het logboek bijwerken.
  4. Basisbranch: `PENGUINCLOUD_BASE_BRANCH` (omgevingsvariabele), anders
     `origin/HEAD`, anders `origin/main`/`origin/master`/`main`/`master`. Wordt
     er geen basisbranch gevonden, dan kijkt het script alleen naar de werkboom.
- **PenguinCloud-MCP-tools in de cloudsessie:** `brain_read_revision`,
  `brain_update`, `brain_create`, `vault_read`, `vault_list`,
  `vault_batch_read`, `vault_search` e.a. Deze komen in de cloudsessie uit de
  PenguinCloud-connector van het claude.ai-account (niet uit `.mcp.json`).

## Besluiten (met onderbouwing)

1. **Logboek in de repo als primaire route** (2026-10-17). De MCP-koppeling is
   per client afzonderlijk geautoriseerd en niet overal gegarandeerd; een
   Markdown-bestand in de repo werkt altijd en is via git te herleiden.
2. **Stop-hook in `.claude/settings.json` in de root.** Claude Code-websessies
   starten in de root van de repository; een `.claude/`-map in een submap wordt
   niet gevonden.
3. **Geen geheimen in `.mcp.json`.** De PenguinCloud-server gebruikt OAuth, dus
   er is geen token in de configuratie nodig. Conform `Werkwijze.md`: geen
   tokens, wachtwoorden of sleutels in repo of vault.
4. **Hook laat stoppen bij `stop_hook_active=true`.** Zonder deze uitzondering
   kan Claude in een lus raken als het logboek om een of andere reden niet bij
   te werken is.
5. **"Codewijziging" = elk gewijzigd bestand behalve het logboek zelf.** Eenvoudig
   en voorspelbaar; ook documentatie- en configwijzigingen horen in het logboek.
6. **Eén vaultproject `Projecten/Codex/` voor de hele repo** (op verzoek van
   Mike). Aanname: P2000 Live en TradeBot worden daarbinnen als onderdelen
   bijgehouden, niet als aparte projecten. Zie open vragen.

## Wijzigingslog

| Datum | Wat | Commit / PR |
| --- | --- | --- |
| 2026-10-17 | PenguinCloud-vastlegging ingericht: `docs/PENGUINCLOUD.md`, sectie in `CLAUDE.md`, `.mcp.json`, Stop-hook en hookscript. Projectnotitie aangemaakt in `Projecten/Codex/Projectlogboek.md` via de MCP. | `38356cd`, PR #1 (open, base `claude/android-p2000-app-lhf6rz`) |

Eerdere geschiedenis (samengevat uit `git log`, niet uit PenguinCloud):

| Periode | Wat |
| --- | --- |
| 2026-06-12 | TradeBot: Render-configuratie, Flask-app, UX-overhaul (`aa1cf4d`…`9830dbd`). |
| 2026-08-15 – 2026-08-18 en later | P2000 Live: app, foldable-ondersteuning, incidentdetails, historie, politiefeeds, straalfilter, tweede P2000-bron, tabbladen *In de buurt*/*Alles* met eigen filters (`3e78701`…`8a96f75`). |

## Controles en testresultaten

**2026-10-17 — PenguinCloud-MCP in deze cloudsessie (Claude Code, claude.ai/code)**

- `ToolSearch "PenguinCloud brain"` gaf de tools `mcp__PenguinCloud__brain_create`,
  `brain_read_revision`, `brain_update`, `vault_batch_read`, `vault_list` e.a.
- `vault_batch_read` van `Agents/Globale instructies.md`, `Werkwijze.md`,
  `Agents/Claude.md`: **geslaagd** (`found: 3, missing: 0`).
- `vault_list Projecten`: **geslaagd**; `Projecten/Codex/` bestond nog niet.
- Schrijftest: `brain_create` van `Projecten/Codex/Projectlogboek.md`:
  **geslaagd** (`created: true`, 2353 bytes). Teruggelezen met
  `brain_read_revision`: inhoud klopt, SHA-256
  `d24b86f3ade6ae3e16175646340762daffd625a2eb6c3383a951f6c09d5487c6`.
- Niet getest: `.mcp.json` in de lokale Claude Code-CLI (OAuth-login op een
  eigen machine is in deze sessie niet mogelijk).

**2026-10-17 — Stop-hook `penguincloud-stop.sh`**, getest met nagebootste
hook-invoer in een tijdelijke git-repository (basisbranch `main`, werkbranch
`feature`):

| Geval | Invoer / situatie | Verwacht | Resultaat |
| --- | --- | --- | --- |
| 0 | Geen wijzigingen | doorlaten | doorlaten (lege uitvoer, exit 0) ✅ |
| a1 | Codewijziging in werkboom, logboek niet gewijzigd | block | `{"decision":"block",…}` ✅ |
| a2 | Codewijziging gecommit, logboek niet gewijzigd | block | `{"decision":"block",…}` ✅ |
| b1 | Logboek bijgewerkt in werkboom | doorlaten | doorlaten ✅ |
| b2 | Logboek bijgewerkt en gecommit | doorlaten | doorlaten ✅ |
| c | `stop_hook_active: true` (met codewijziging) | doorlaten | doorlaten ✅ |
| c-zonder-jq | `stop_hook_active: true`, jq niet in PATH | doorlaten | doorlaten ✅ |
| a-zonder-jq | `stop_hook_active: false`, jq niet in PATH, codewijziging | block | `{"decision":"block",…}` ✅ |

Ook in deze repository zelf gaf de hook (vóór het bijwerken van dit logboek)
correct `block`. Niet getest: de hook vanuit een echte Claude Code-sessie die
stopt; dat gebeurt pas bij het afsluiten van een volgende sessie.

## Bekende problemen en open vragen

- **Basisbranch niet altijd lokaal bekend.** In deze cloudcontainer bestaat
  `origin/HEAD` lokaal niet en de standaardbranch
  (`claude/build-trading-bot-vBkrZ`) is niet opgehaald. De hook kijkt dan alleen
  naar de werkboom; gecommitte wijzigingen zonder logupdate glippen er dan door.
  Oplossing: `PENGUINCLOUD_BASE_BRANCH` instellen (bijv. via `env` in
  `.claude/settings.json`) of `git remote set-head origin -a` uitvoeren.
  *Open vraag:* welke branch is de echte basis — de huidige GitHub-standaard
  (TradeBot) of de P2000-branch?
- **Hook is grofmazig.** Zodra het logboek één keer op de branch is gewijzigd,
  laat de hook latere codewijzigingen op die branch door. Bewuste vereenvoudiging.
- **TradeBot-build (aanname, niet getest):** `render.yaml` gebruikt
  `pip install -e .`, maar er is geen `setup.py` of `pyproject.toml` in de repo.
  Waarschijnlijk faalt die build; te verifiëren.
- **`docs/RENDER_DEPLOY.md`** bevat placeholders als `xxx` voor sleutels (geen
  echte geheimen) — prima, maar geen echte waarden toevoegen.
- **Gebouwde bestanden in git:** `p2000-app/apk/p2000-live.apk` (~6,8 MB) en
  `p2000-app/.kotlin/errors/*.log` staan in de repo. Mogelijk ongewenst.
- **Open vraag:** één vaultproject voor de hele repo, of aparte projecten voor
  P2000 Live en TradeBot?

## Volgende stappen

1. PR met deze inrichting reviewen en mergen.
2. Basisbranch van de repo vastleggen en zo nodig `PENGUINCLOUD_BASE_BRANCH`
   instellen.
3. Lokale CLI testen: `claude` in de repo starten, `/mcp` → `penguincloud`
   autoriseren via OAuth, een lees- en schrijftest doen en het resultaat hier en
   in `Agents/Claude.md` (platformtabel) vastleggen.
4. Synchronisatie van `docs/PENGUINCLOUD.md` naar `Projecten/Codex/` inrichten
   (nu handmatig of via de MCP).
5. TradeBot-buildprobleem (`pip install -e .`) onderzoeken als dat project nog
   leeft.

## Installatie, beheer en overdracht

- **Nieuwe sessie (lokaal of online):** Claude Code leest `CLAUDE.md` en
  `.claude/settings.json` automatisch vanuit de root van de repo.
- **MCP lokaal:** bij het eerste gebruik van `.mcp.json` vraagt Claude Code om
  de projectserver goed te keuren; daarna `/mcp` → `penguincloud` →
  authenticeren (OAuth in de browser). Geen token in bestanden zetten.
- **MCP in de cloud:** komt uit de PenguinCloud-connector van het
  claude.ai-account; `.mcp.json` is daarvoor niet nodig.
- **Hook uitschakelen of aanpassen:** verwijder of wijzig het `Stop`-blok in
  `.claude/settings.json`. Handmatig testen:
  `echo '{"stop_hook_active":false}' | bash .claude/hooks/penguincloud-stop.sh`
  (lege uitvoer = doorlaten).
- **Overdracht:** wie verder werkt, leest eerst dit bestand en werkt het bij
  vóór het afsluiten van de sessie, inclusief onafgerond werk en mislukte
  pogingen.
