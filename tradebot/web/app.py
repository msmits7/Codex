"""Flask web interface for TradeBot."""

from __future__ import annotations

import os
import random
from datetime import datetime, timezone
from flask import Flask, render_template, request, redirect, url_for, jsonify, flash

from tradebot.config import load_settings

app = Flask(__name__, template_folder="templates")
app.secret_key = os.getenv("SECRET_KEY", "tradebot-secret-change-me")

# Global state
_bot_running = False
_trade_history: list[dict] = []

PLATFORM_DISPLAY = {
    "binance": "Binance",
    "bitvavo": "Bitvavo",
    "degiro": "DEGIRO",
    "alpaca": "Alpaca",
    "revolut_stocks": "Revolut Stocks",
    "revolut_etf": "Revolut ETFs",
    "revolut_bonds": "Revolut Bonds",
    "revolut_crypto": "Revolut Crypto",
    "revolut_cfd": "Revolut CFDs",
}


def _get_demo_portfolio():
    """Generate demo portfolio data."""
    return {
        "total_value": 12450.00,
        "total_pnl": 450.00,
        "platforms": [
            {"name": "revolut_stocks", "display_name": "Revolut Stocks", "connected": True, "configured": True, "total_value_usd": 5200.00, "error": ""},
            {"name": "revolut_crypto", "display_name": "Revolut Crypto", "connected": True, "configured": True, "total_value_usd": 3800.00, "error": ""},
            {"name": "bitvavo", "display_name": "Bitvavo", "connected": False, "configured": False, "total_value_usd": 0, "error": ""},
            {"name": "degiro", "display_name": "DEGIRO", "connected": False, "configured": False, "total_value_usd": 0, "error": ""},
        ],
        "top_positions": [
            {"symbol": "AAPL", "platform": "revolut_stocks", "value": 2100.00, "pnl": 150.00, "pnl_pct": 7.7},
            {"symbol": "BTC", "platform": "revolut_crypto", "value": 1800.00, "pnl": 200.00, "pnl_pct": 12.5},
            {"symbol": "ASML", "platform": "revolut_stocks", "value": 1500.00, "pnl": 75.00, "pnl_pct": 5.3},
            {"symbol": "ETH", "platform": "revolut_crypto", "value": 1200.00, "pnl": -50.00, "pnl_pct": -4.0},
            {"symbol": "SPY", "platform": "revolut_etf", "value": 900.00, "pnl": 25.00, "pnl_pct": 2.9},
        ],
    }


@app.route("/")
def dashboard():
    settings = load_settings()
    portfolio = _get_demo_portfolio()

    return render_template(
        "dashboard.html",
        mode=settings.mode.value,
        bot_running=_bot_running,
        trade_count=len(_trade_history),
        recent_trades=_trade_history[-10:],
        ai_enabled=False,
        total_value=portfolio["total_value"],
        total_pnl=portfolio["total_pnl"],
        platforms=portfolio["platforms"],
        top_positions=portfolio["top_positions"],
        yield_stats={"today": 45.00, "week": 120.00, "month": 450.00, "all_time": 450.00},
        stats={"win_rate": 65.0, "wins": 13, "losses": 7, "avg_win": 85.00, "avg_loss": -42.00, "best_trade": 200.00, "worst_trade": -75.00},
        connected_count=2,
        total_platforms=len(PLATFORM_DISPLAY),
        open_positions=5,
    )


@app.route("/trades")
def trades():
    return render_template("trades.html", trades=_trade_history)


@app.route("/platforms")
def platforms():
    settings = load_settings()
    platform_info = {
        "revolut": {
            "display": "Revolut",
            "description": "Stocks, ETFs, Bonds, Crypto, CFDs",
            "configured": bool(settings.revolut_access_token),
            "fields": [
                {"label": "Access Token", "env_key": "REVOLUT_ACCESS_TOKEN", "type": "password", "placeholder": "", "current": ""},
            ],
        },
        "bitvavo": {
            "display": "Bitvavo",
            "description": "Dutch crypto exchange (EUR)",
            "configured": bool(settings.bitvavo_api_key),
            "fields": [
                {"label": "API Key", "env_key": "BITVAVO_API_KEY", "type": "text", "placeholder": "", "current": ""},
                {"label": "Secret", "env_key": "BITVAVO_SECRET", "type": "password", "placeholder": "", "current": ""},
            ],
        },
        "degiro": {
            "display": "DEGIRO",
            "description": "Dutch & EU stocks",
            "configured": bool(settings.degiro_username),
            "fields": [
                {"label": "Username", "env_key": "DEGIRO_USERNAME", "type": "text", "placeholder": "", "current": ""},
                {"label": "Password", "env_key": "DEGIRO_PASSWORD", "type": "password", "placeholder": "", "current": ""},
            ],
        },
    }
    return render_template("platforms.html", platform_info=platform_info)


@app.route("/settings")
def settings_page():
    settings = load_settings()
    return render_template(
        "settings.html",
        settings=settings,
        exchanges_configured={
            "revolut": bool(settings.revolut_access_token),
            "bitvavo": bool(settings.bitvavo_api_key),
            "degiro": bool(settings.degiro_username),
            "binance": bool(settings.binance_api_key),
            "alpaca": bool(settings.alpaca_api_key),
        },
    )


@app.route("/ai")
def ai_page():
    return render_template("ai.html", stats={"episodes": 0, "epsilon": 0.15}, training_log=[], training_enabled=False)


@app.route("/test-phase")
def test_phase():
    return render_template("test_phase.html", test_running=False, results=None, test_trades=[])


@app.route("/bot/start", methods=["POST"])
def bot_start():
    global _bot_running
    _bot_running = True
    flash("Bot gestart in demo mode", "success")
    return redirect(url_for("dashboard"))


@app.route("/bot/stop", methods=["POST"])
def bot_stop():
    global _bot_running
    _bot_running = False
    flash("Bot gestopt", "info")
    return redirect(url_for("dashboard"))


@app.route("/api/status")
def api_status():
    return jsonify({
        "bot_running": _bot_running,
        "trade_count": len(_trade_history),
        "mode": "paper",
        "version": "1.0.0",
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
