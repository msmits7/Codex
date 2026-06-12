# Deployment op Render

## Stap 1: Render Account

1. Ga naar [render.com](https://render.com) en maak een account aan
2. Verbind je GitHub account

## Stap 2: Nieuwe Web Service

1. Klik op **"New +"** → **"Web Service"**
2. Selecteer je repository: `msmits7/Codex`
3. Selecteer branch: `claude/build-trading-bot-vBkrZ`

## Stap 3: Configuratie

Vul de volgende instellingen in:

| Setting | Waarde |
|---------|--------|
| **Name** | `tradebot` |
| **Region** | `Frankfurt (EU)` (of dichtstbijzijnde) |
| **Branch** | `claude/build-trading-bot-vBkrZ` |
| **Runtime** | `Python 3` |
| **Build Command** | `pip install -e .` |
| **Start Command** | `python -m tradebot.web.app` |

## Stap 4: Environment Variables

Ga naar **Environment** en voeg je API keys toe:

```
# Trading Mode
TRADEBOT_MODE=paper

# Revolut
REVOLUT_ACCESS_TOKEN=xxx
REVOLUT_REFRESH_TOKEN=xxx
REVOLUT_DEVICE_ID=xxx
REVOLUT_SANDBOX=true

# Bitvavo (Dutch crypto)
BITVAVO_API_KEY=xxx
BITVAVO_SECRET=xxx

# DEGIRO (Dutch/EU stocks)
DEGIRO_USERNAME=xxx
DEGIRO_PASSWORD=xxx

# Andere exchanges naar wens...
```

## Stap 5: Deploy

Klik op **"Create Web Service"** — Render bouwt en start automatisch.

Je dashboard is beschikbaar op: `https://tradebot-xxxx.onrender.com`

---

## Alternatief: render.yaml (Infrastructure as Code)

Voor automatische deployment, voeg dit bestand toe aan je repo.

---

## Optie 1: Blueprint (aanbevolen)

1. Ga naar [render.com/new/blueprint](https://render.com/new/blueprint)
2. Selecteer je repository
3. Render detecteert automatisch `render.yaml` en configureert alles

## Optie 2: Handmatige Setup

Volg de stappen hierboven (Stap 1-5).

---

## Troubleshooting

**Build faalt?**
- Check of `requirements.txt` aanwezig is
- Verifieer Python versie (3.10+)

**App start niet?**
- Check logs in Render dashboard
- Verifieer environment variables

**Poort problemen?**
- Render zet automatisch `$PORT` - gebruik `gunicorn --bind 0.0.0.0:$PORT`
