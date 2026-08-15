# P2000 Live

Android-app die live P2000-meldingen (ambulance, brandweer, politie, traumaheli, KNRM) toont.

## Functies

- **Live meldingen** — haalt elke 30 seconden de landelijke P2000-feed op (bron: alarmeringen.nl) en ondersteunt pull-to-refresh.
- **Per type hulpverlening** — elke melding wordt geclassificeerd (Ambulance, Brandweer, Politie, Traumaheli, KNRM/Water, Overig) met eigen kleur; via de chips bovenin schakel je types aan/uit.
- **Kaartweergave** — OpenStreetMap (osmdroid, geen API-key nodig) met gekleurde markers per type. Adressen worden gegeocodeerd via de gratis PDOK Locatieserver (postcode → straat → plaats, met cache).
- **Locatiefilter** — vrije tekstfilter op plaats, straat, postcode of regio.
- **Straalfilter** — toon alleen meldingen binnen X km (1–100) van je huidige locatie (vraagt locatietoestemming).

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
