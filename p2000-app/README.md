# P2000 Live

Android-app die live P2000-meldingen (ambulance, brandweer, politie, traumaheli, KNRM) toont.

## Functies

- **Live meldingen** — haalt elke 30 seconden de landelijke P2000-feed op (bron: alarmeringen.nl) en ondersteunt pull-to-refresh.
- **Per type hulpverlening** — elke melding wordt geclassificeerd (Ambulance, Brandweer, Politie, Traumaheli, KNRM/Water, Overig) met eigen kleur; via de chips bovenin schakel je types aan/uit.
- **Kaartweergave** — OpenStreetMap (osmdroid, geen API-key nodig) met gekleurde markers per type. Adressen worden gegeocodeerd via de gratis PDOK Locatieserver (postcode → straat → plaats, met cache).
- **Locatiefilter** — vrije tekstfilter op plaats, straat, postcode of regio.
- **Straalfilter** — toon alleen meldingen binnen X km (1–100) van je huidige locatie (vraagt locatietoestemming).
- **Foldables & tablets** — zodra het venster minstens 600dp breed is (opengeklapte Galaxy Z Fold 8 / Pixel Fold, tablets, of een telefoon in landscape) schakelt de app naar een two-pane weergave: meldingenlijst links, kaart rechts. Tik op een melding om de kaart erheen te laten springen; tikken op een marker opent hetzelfde detailscherm als vanuit de lijst. Bij open-/dichtklappen of split-screen wisselt de layout automatisch mee.
- **Alle beeldverhoudingen** — geen vaste orientatie en `resizeableActivity`, dus geen letterboxing: het langgerekte coverscherm (21:9+) en het bijna vierkante binnenscherm van de Galaxy Z Fold 8 worden volledig gebruikt, inclusief doorlopen langs de camera-cutout (`shortEdges`).

- **Historie (24 uur)** — meldingen worden op schijf bewaard en overleven een herstart. Met het historiefilter kies je hoe ver je terugkijkt: 15/30 min, 1/3/6/12/24 uur. Optioneel houdt een stille achtergrondservice de historie ook bij als de app dicht is (schakelaar in het filterpaneel).
- **Gegroepeerde incidenten** — meldingen van verschillende diensten op dezelfde locatie binnen 15 minuten (bijv. ambulance + traumaheli + politie bij een reanimatie) worden gebundeld tot één kaart. De kop toont alle betrokken diensten en daaronder staat, ingesprongen, per dienst een eigen regel met tijdstip, prioriteit en tekst; in het detailscherm staat bij elke dienst de originele pagertekst.
- **Politieberichten (Burgernet-achtig)** — naast P2000 haalt de app de publieke RSS-feeds van rss.politie.nl op: getuigenoproepen/opsporingsberichten, vermiste personen en politienieuws, als eigen type met groene marker. Burgernet zelf heeft geen publieke API (hun endpoint vereist authenticatie vanuit de eigen app), dit is de publiek beschikbare tegenhanger.
- **Verrijking via data.politie.nl** — bij het openen van een melding worden achteraf de maandcijfers van de betreffende gemeente opgehaald (CBS-tabel 47013NED, "Geregistreerde misdrijven en aangiften; soort misdrijf, gemeente"), inclusief het misdrijftype dat bij de aard van de melding past. De gemeente komt uit dezelfde PDOK-geocodering die ook de kaartpositie levert.
- **Detailinformatie per melding** — tik op een melding voor een detailscherm met de aard van de melding (woningbrand, reanimatie, verkeersongeval, schietincident, GRIP-opschaling, …), prioriteit met uitleg (A1/A2/P1/…), directe-inzet-ambulance (DIA), opgeroepen eenheden, rit-/bonnummer, volledig adres en regio, en de originele pagertekst. De aard wordt lokaal uit de ruwe pagertekst herkend met een patronenbibliotheek (`AardExtractor.kt`); ambulancemeldingen bevatten om privacyredenen geen medische details en dat meldt de app dan ook eerlijk.

## Installatie

Kant-en-klare APK: [`apk/p2000-live.apk`](apk/p2000-live.apk)

1. Download de APK naar je telefoon.
2. Sta "installeren uit onbekende bronnen" toe wanneer Android daarom vraagt.
3. Open de app — meldingen verschijnen direct; voor het straalfilter vraagt de app om locatietoestemming.

Vereist Android 8.0 (API 26) of hoger.

## Zelf bouwen

```bash
cd p2000-app
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Er is ook een GitHub Actions-workflow (`.github/workflows/p2000-android.yml`) die bij elke push naar `p2000-app/` automatisch een APK bouwt en als artifact uploadt.

## Architectuur

- `data/FeedParser.kt` — parseert de RSS-feed: type-classificatie, prio (A1/A2/P1/…), plaats/straat/postcode-extractie uit titel, omschrijving en link-pad.
- `data/Geocoder.kt` — PDOK Locatieserver-geocoder met in-memory cache.
- `data/MeldingRepository.kt` — pollt de feed, merget op GUID, bewaart de laatste 300 meldingen.
- `ui/MainViewModel.kt` — pollloop + filterstatus (types, tekstfilter, straal + eigen locatie).
- `ui/MainActivity.kt` — lijst (RecyclerView), kaart (osmdroid) en filter-bottomsheet.

## Kanttekeningen

- De feed bevat geen coördinaten; de kaartpositie is gegeocodeerd op basis van postcode/straat/plaats en dus bij benadering. Meldingen zonder herleidbaar adres verschijnen alleen in de lijst.
- De debug-APK is ondertekend met een debug-sleutel; prima voor eigen gebruik, niet voor de Play Store.
