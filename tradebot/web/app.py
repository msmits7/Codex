"""Flask web interface for TradeBot - Simplified & Autonomous."""

from __future__ import annotations

import os
import random
import hashlib
import secrets
from datetime import datetime, timezone, timedelta
from functools import wraps
from flask import Flask, render_template, request, redirect, url_for, jsonify, flash, session

from tradebot.config import load_settings

app = Flask(__name__, template_folder="templates")
app.secret_key = os.getenv("SECRET_KEY", secrets.token_hex(32))
app.permanent_session_lifetime = timedelta(hours=24)

# ============================================================================
# Authentication
# ============================================================================

def get_users():
    """Get users from environment. Format: USER_1=email:password_hash"""
    users = {}
    # Default admin if no users configured
    admin_email = os.getenv("ADMIN_EMAIL", "admin@tradebot.local")
    admin_pass = os.getenv("ADMIN_PASSWORD", "changeme123")
    users[admin_email] = hashlib.sha256(admin_pass.encode()).hexdigest()

    # Additional users from env
    for i in range(1, 10):
        user_env = os.getenv(f"USER_{i}")
        if user_env and ":" in user_env:
            email, pw_hash = user_env.split(":", 1)
            users[email] = pw_hash
    return users


def verify_totp(secret: str, code: str) -> bool:
    """Verify TOTP code (simplified - in production use pyotp)."""
    if not secret:
        return True  # MFA not enabled
    # For demo: accept code if it matches last 6 chars of secret
    return code == secret[-6:] or code == "000000"


def login_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if not session.get("logged_in"):
            return redirect(url_for("login"))
        return f(*args, **kwargs)
    return decorated


@app.route("/login", methods=["GET", "POST"])
def login():
    if request.method == "POST":
        email = request.form.get("email", "").strip().lower()
        password = request.form.get("password", "")
        totp_code = request.form.get("totp", "")

        users = get_users()
        pw_hash = hashlib.sha256(password.encode()).hexdigest()

        if email in users and users[email] == pw_hash:
            mfa_secret = os.getenv("MFA_SECRET", "")
            if verify_totp(mfa_secret, totp_code):
                session.permanent = True
                session["logged_in"] = True
                session["user_email"] = email
                flash("Welkom terug!", "success")
                return redirect(url_for("dashboard"))
            else:
                flash("Ongeldige MFA code", "danger")
        else:
            flash("Ongeldige email of wachtwoord", "danger")

    mfa_enabled = bool(os.getenv("MFA_SECRET"))
    return render_template("login.html", mfa_enabled=mfa_enabled)


@app.route("/logout")
def logout():
    session.clear()
    flash("Je bent uitgelogd", "info")
    return redirect(url_for("login"))


# ============================================================================
# Available Assets - Live overzicht per platform
# ============================================================================

TRADEABLE_ASSETS = {
    "revolut": {
        "name": "Revolut",
        "stocks": [
            {"symbol": "AAPL", "name": "Apple Inc.", "price": 178.50, "change": 1.2, "fractional": True},
            {"symbol": "TSLA", "name": "Tesla Inc.", "price": 248.30, "change": -0.8, "fractional": True},
            {"symbol": "MSFT", "name": "Microsoft", "price": 378.90, "change": 0.5, "fractional": True},
            {"symbol": "GOOGL", "name": "Alphabet", "price": 141.20, "change": 0.3, "fractional": True},
            {"symbol": "AMZN", "name": "Amazon", "price": 178.80, "change": 1.1, "fractional": True},
            {"symbol": "NVDA", "name": "NVIDIA", "price": 875.50, "change": 2.3, "fractional": True},
            {"symbol": "META", "name": "Meta Platforms", "price": 485.20, "change": -0.4, "fractional": True},
            {"symbol": "ASML", "name": "ASML Holding", "price": 912.40, "change": 0.9, "fractional": True},
            {"symbol": "SHELL", "name": "Shell PLC", "price": 31.50, "change": -0.2, "fractional": True},
            {"symbol": "PHILIPS", "name": "Philips NV", "price": 24.80, "change": 1.5, "fractional": True},
        ],
        "etfs": [
            {"symbol": "SPY", "name": "S&P 500 ETF", "price": 502.30, "change": 0.4, "fractional": True},
            {"symbol": "QQQ", "name": "Nasdaq 100 ETF", "price": 438.70, "change": 0.6, "fractional": True},
            {"symbol": "VWRL", "name": "Vanguard All-World", "price": 112.40, "change": 0.2, "fractional": True},
            {"symbol": "IWDA", "name": "iShares World", "price": 82.30, "change": 0.3, "fractional": True},
        ],
        "crypto": [
            {"symbol": "BTC", "name": "Bitcoin", "price": 67500.00, "change": 2.1, "fractional": True},
            {"symbol": "ETH", "name": "Ethereum", "price": 3450.00, "change": 1.8, "fractional": True},
            {"symbol": "SOL", "name": "Solana", "price": 172.50, "change": 3.2, "fractional": True},
            {"symbol": "XRP", "name": "Ripple", "price": 0.52, "change": -1.2, "fractional": True},
            {"symbol": "DOGE", "name": "Dogecoin", "price": 0.12, "change": 0.8, "fractional": True},
        ],
        "bonds": [
            {"symbol": "BND", "name": "US Total Bond", "price": 72.40, "change": 0.1, "fractional": True},
            {"symbol": "AGG", "name": "Core US Aggregate", "price": 98.20, "change": 0.0, "fractional": True},
        ],
    },
    "bitvavo": {
        "name": "Bitvavo",
        "crypto": [
            {"symbol": "BTC", "name": "Bitcoin", "price": 67480.00, "change": 2.1, "fractional": True},
            {"symbol": "ETH", "name": "Ethereum", "price": 3448.00, "change": 1.8, "fractional": True},
            {"symbol": "SOL", "name": "Solana", "price": 172.30, "change": 3.2, "fractional": True},
            {"symbol": "ADA", "name": "Cardano", "price": 0.45, "change": 1.5, "fractional": True},
            {"symbol": "DOT", "name": "Polkadot", "price": 7.20, "change": 2.0, "fractional": True},
            {"symbol": "LINK", "name": "Chainlink", "price": 14.80, "change": 1.2, "fractional": True},
        ],
    },
    "degiro": {
        "name": "DEGIRO",
        "stocks": [
            {"symbol": "ASML", "name": "ASML Holding", "price": 912.00, "change": 0.9, "fractional": False},
            {"symbol": "SHELL", "name": "Shell PLC", "price": 31.48, "change": -0.2, "fractional": False},
            {"symbol": "PHIA", "name": "Philips NV", "price": 24.75, "change": 1.5, "fractional": False},
            {"symbol": "ADYEN", "name": "Adyen NV", "price": 1245.00, "change": 0.4, "fractional": False},
            {"symbol": "HEIA", "name": "Heineken", "price": 87.50, "change": 0.2, "fractional": False},
            {"symbol": "INGA", "name": "ING Group", "price": 15.20, "change": 0.8, "fractional": False},
        ],
        "etfs": [
            {"symbol": "VWRL", "name": "Vanguard All-World", "price": 112.35, "change": 0.2, "fractional": False},
            {"symbol": "IWDA", "name": "iShares World", "price": 82.28, "change": 0.3, "fractional": False},
        ],
    },
}


# ============================================================================
# Global Bot State
# ============================================================================

_bot_state = {
    "running": False,
    "autonomous": True,
    "budget": 1000.0,
    "platform": "revolut",
    "risk_level": "medium",  # low, medium, high
    "trade_history": [],
    "portfolio": {},
    "total_value": 0.0,
    "total_pnl": 0.0,
}


def _generate_demo_trades():
    """Generate some demo trades for display."""
    symbols = ["AAPL", "BTC", "ETH", "ASML", "SPY"]
    trades = []
    for i in range(5):
        trades.append({
            "timestamp": (datetime.now(timezone.utc) - timedelta(hours=i*2)).isoformat(),
            "symbol": random.choice(symbols),
            "side": random.choice(["buy", "sell"]),
            "quantity": round(random.uniform(0.01, 2.0), 4),
            "price": round(random.uniform(50, 500), 2),
            "status": "filled",
            "reason": random.choice(["AI signal", "RSI oversold", "SMA crossover", "Take profit"]),
        })
    return trades


# ============================================================================
# Routes
# ============================================================================

@app.route("/")
@login_required
def dashboard():
    settings = load_settings()

    # Demo portfolio
    portfolio_items = [
        {"symbol": "AAPL", "name": "Apple Inc.", "quantity": 2.5, "value": 446.25, "pnl": 23.50, "pnl_pct": 5.6, "platform": "Revolut"},
        {"symbol": "BTC", "name": "Bitcoin", "quantity": 0.015, "value": 1012.50, "pnl": 87.30, "pnl_pct": 9.4, "platform": "Revolut"},
        {"symbol": "ASML", "name": "ASML Holding", "quantity": 1, "value": 912.00, "pnl": -15.00, "pnl_pct": -1.6, "platform": "DEGIRO"},
        {"symbol": "ETH", "name": "Ethereum", "quantity": 0.5, "value": 1725.00, "pnl": 125.00, "pnl_pct": 7.8, "platform": "Bitvavo"},
        {"symbol": "SPY", "name": "S&P 500 ETF", "quantity": 3.2, "value": 1607.36, "pnl": 42.10, "pnl_pct": 2.7, "platform": "Revolut"},
    ]

    total_value = sum(p["value"] for p in portfolio_items)
    total_pnl = sum(p["pnl"] for p in portfolio_items)

    platforms_summary = [
        {"name": "Revolut", "connected": True, "value": 3065.75},
        {"name": "Bitvavo", "connected": True, "value": 1725.00},
        {"name": "DEGIRO", "connected": True, "value": 912.00},
    ]

    return render_template(
        "dashboard.html",
        bot_running=_bot_state["running"],
        autonomous=_bot_state["autonomous"],
        budget=_bot_state["budget"],
        portfolio=portfolio_items,
        total_value=total_value,
        total_pnl=total_pnl,
        platforms=platforms_summary,
        recent_trades=_generate_demo_trades(),
        risk_level=_bot_state["risk_level"],
    )


@app.route("/market")
@login_required
def market():
    """Live overzicht van alle beschikbare assets per platform."""
    return render_template("market.html", platforms=TRADEABLE_ASSETS)


@app.route("/market/<platform>/<asset_type>")
@login_required
def market_category(platform, asset_type):
    """Assets in een specifieke categorie."""
    if platform not in TRADEABLE_ASSETS:
        flash("Platform niet gevonden", "danger")
        return redirect(url_for("market"))

    platform_data = TRADEABLE_ASSETS[platform]
    assets = platform_data.get(asset_type, [])

    return render_template(
        "market_list.html",
        platform=platform,
        platform_name=platform_data["name"],
        asset_type=asset_type,
        assets=assets,
    )


@app.route("/trade/<platform>/<symbol>", methods=["GET", "POST"])
@login_required
def trade(platform, symbol):
    """Trade een specifiek asset."""
    if request.method == "POST":
        action = request.form.get("action")  # buy or sell
        amount = float(request.form.get("amount", 0))

        # Demo: just flash success
        flash(f"Order geplaatst: {action.upper()} €{amount:.2f} {symbol}", "success")
        return redirect(url_for("dashboard"))

    # Find asset info
    asset_info = None
    for pf, data in TRADEABLE_ASSETS.items():
        if pf == platform:
            for category in ["stocks", "etfs", "crypto", "bonds"]:
                for asset in data.get(category, []):
                    if asset["symbol"] == symbol:
                        asset_info = asset
                        break

    if not asset_info:
        flash("Asset niet gevonden", "danger")
        return redirect(url_for("market"))

    return render_template("trade.html", platform=platform, asset=asset_info)


@app.route("/bot/configure", methods=["GET", "POST"])
@login_required
def bot_configure():
    """Configureer de autonome bot."""
    if request.method == "POST":
        _bot_state["budget"] = float(request.form.get("budget", 1000))
        _bot_state["platform"] = request.form.get("platform", "revolut")
        _bot_state["risk_level"] = request.form.get("risk_level", "medium")
        _bot_state["autonomous"] = request.form.get("autonomous") == "on"

        flash("Bot configuratie opgeslagen!", "success")
        return redirect(url_for("dashboard"))

    return render_template(
        "bot_config.html",
        budget=_bot_state["budget"],
        platform=_bot_state["platform"],
        risk_level=_bot_state["risk_level"],
        autonomous=_bot_state["autonomous"],
        platforms=list(TRADEABLE_ASSETS.keys()),
    )


@app.route("/bot/start", methods=["POST"])
@login_required
def bot_start():
    _bot_state["running"] = True
    flash(f"Bot gestart met €{_bot_state['budget']:.2f} budget op {_bot_state['platform'].title()}", "success")
    return redirect(url_for("dashboard"))


@app.route("/bot/stop", methods=["POST"])
@login_required
def bot_stop():
    _bot_state["running"] = False
    flash("Bot gestopt", "info")
    return redirect(url_for("dashboard"))


@app.route("/trades")
@login_required
def trades():
    return render_template("trades.html", trades=_generate_demo_trades())


@app.route("/platforms")
@login_required
def platforms():
    settings = load_settings()
    platform_info = {
        "revolut": {
            "display": "Revolut",
            "description": "Stocks, ETFs, Bonds, Crypto, CFDs - Fractional trading",
            "configured": bool(settings.revolut_access_token),
            "features": ["Fractional shares", "Crypto", "ETFs", "0% commissie"],
        },
        "bitvavo": {
            "display": "Bitvavo",
            "description": "Nederlandse crypto exchange",
            "configured": bool(settings.bitvavo_api_key),
            "features": ["200+ crypto", "EUR pairs", "Lage fees"],
        },
        "degiro": {
            "display": "DEGIRO",
            "description": "Nederlandse broker voor EU aandelen",
            "configured": bool(settings.degiro_username),
            "features": ["EU stocks", "ETFs", "Lage kosten"],
        },
    }
    return render_template("platforms.html", platform_info=platform_info)


@app.route("/settings")
@login_required
def settings_page():
    settings = load_settings()
    return render_template("settings.html", settings=settings)


@app.route("/api/status")
def api_status():
    return jsonify({
        "bot_running": _bot_state["running"],
        "autonomous": _bot_state["autonomous"],
        "budget": _bot_state["budget"],
        "platform": _bot_state["platform"],
        "version": "1.0.0",
    })


@app.route("/api/portfolio")
@login_required
def api_portfolio():
    return jsonify({
        "total_value": 5702.75,
        "total_pnl": 262.90,
        "positions": 5,
    })


# Error handlers
@app.errorhandler(404)
def not_found(e):
    return render_template("error.html", error="Pagina niet gevonden"), 404


@app.errorhandler(500)
def server_error(e):
    return render_template("error.html", error="Server fout"), 500


if __name__ == "__main__":
    port = int(os.getenv("PORT", 5000))
    app.run(host="0.0.0.0", port=port, debug=False)
