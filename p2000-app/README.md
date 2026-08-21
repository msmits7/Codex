# P2000 Live

Android-app die live P2000-meldingen (ambulance, brandweer, politie, traumaheli, KNRM) toont.

## Functies

- **Live meldingen uit twee bronnen** — de RSS-feed van alarmeringen.nl (netjes geparseerde omschrijvingen, maar vrijwel alleen A1-spoed) én de monitorpagina van p2000-online.net, die álle prioriteiten bevat: A2, P1-P3 en besteld vervoer (B1/B2). Dubbele meldingen worden op ritnummer herkend en samengevoegd. Pull-to-refresh wordt ondersteund.
- **Plaatsafkortingen** — pagerteksten korten plaatsen af ("SGRAVH" voor Den Haag). De app vertaalt die via een vaste tabel, checkt anders bij de geocoder of het token zelf een woonplaats is, en laat de plaats leeg als beide falen — liever geen speld dan een speld in de verkeerde stad.
- **Per type hulpverlening** — elke melding wordt geclassificeerd (Ambulance, Brandweer, Politie, Traumaheli, KNRM/Water, Politiebericht, Overig) met eigen kleur en icoon. De chips bovenin werken als selectie: niets aangevinkt toont alles, en zodra je bijvoorbeeld Brandweer aanvinkt zie je alleen brandweermeldingen. Meerdere types tegelijk kan gewoon.
- **Kaartweergave** — OpenStreetMap (osmdroid, geen API-key nodig) met gekleurde markers per type. Adressen worden gegeocodeerd via de gratis PDOK Locatieserver (postcode → straat → plaats, met cache).
- **Locatiefilter** — vrije tekstfilter op plaats, straat, postcode of regio.
- **Straalfilter** — toon alleen meldingen binnen X km (1–100) van je huidige locatie (vraagt locatietoestemming). Werkt in de meldingenlijst, op de kaart én in het RSS-tabblad. Bij meldingen zonder exact adres — politieberichten noemen vaak alleen een plaats — telt de afstand tot de **rand** van die gemeente in plaats van tot het middelpunt, zodat een bericht meedoet zodra de gemeente ook maar deels binnen de straal valt. De gemeentegrens komt als bounding box uit de PDOK Locatieserver.
- **Logboek en diagnose** — knop in het filterpaneel met een rapport van wat de app binnenkrijgt: actieve filters, eigen locatie, aantallen per bron en per type, hoeveel meldingen een exacte locatie/gebied/geen locatie hebben, de laatste 20 meldingen met afstand en of het filter ze toont, plus een logboek van de ophaalrondes. Te kopiëren naar het klembord of te delen.
- **Foldables & tablets** — zodra het venster minstens 600dp breed is (opengeklapte Galaxy Z Fold 8 / Pixel Fold, tablets, of een telefoon in landscape) schakelt de app naar een two-pane weergave: meldingenlijst links, kaart rechts. Tik op een melding om de kaart erheen te laten springen; tikken op een marker opent hetzelfde detailscherm als vanuit de lijst. Bij open-/dichtklappen of split-screen wisselt de layout automatisch mee.
- **Alle beeldverhoudingen** — geen vaste orientatie en `resizeableActivity`, dus geen letterboxing: het langgerekte coverscherm (21:9+) en het bijna vierkante binnenscherm van de Galaxy Z Fold 8 worden volledig gebruikt, inclusief doorlopen langs de camera-cutout (`shortEdges`).

- **Historie (24 uur)** — meldingen worden op schijf bewaard en overleven een herstart. Meldingen worden een week bewaard; met het historiefilter kies je hoe ver je terugkijkt: 15/30 min, 1/3/6/12/24 uur, 48 uur, 72 uur of 1 week. Optioneel houdt een stille achtergrondservice de historie ook bij als de app dicht is (schakelaar in het filterpaneel).
- **Gegroepeerde incidenten** — meldingen van verschillende diensten op dezelfde locatie binnen 15 minuten (bijv. ambulance + traumaheli + politie bij een reanimatie) worden gebundeld tot één kaart. De kop toont alle betrokken diensten en daaronder staat, ingesprongen, per dienst een eigen regel met tijdstip, prioriteit en tekst; in het detailscherm staat bij elke dienst de originele pagertekst.
- **Drie tabbladen** — *In de buurt* (alleen meldingen met een bruikbare locatie, binnen je straal), *Kaart*, en *Alles* (het hele land, inclusief meldingen zonder duidelijke locatie en de politieberichten). Meldingen die alleen op regio te plaatsen zijn vervuilen de buurtlijst dus niet meer, maar zijn wel terug te vinden. Op een opengeklapte foldable staat op het Alles-tabblad de lijst links en het detail van de gekozen melding rechts; op een telefoon blijft dat een bottom sheet.
- **Zoekveld op het Alles-tabblad** — vrij zoeken over alles wat een melding bevat: plaats, straat, postcode, dienst, prioriteit, regio, bron, rit-/bonnummer en de ruwe pagertekst. Meerdere woorden moeten allemaal voorkomen.
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
