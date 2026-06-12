"""Centralized configuration loaded from environment / .env file."""

from __future__ import annotations

import os
from enum import Enum


class TradingMode(str, Enum):
    PAPER = "paper"
    LIVE = "live"


class Settings:
    """Simple settings class that reads from environment variables."""

    def __init__(self):
        self.mode = TradingMode(os.getenv("TRADEBOT_MODE", "paper"))
        self.log_level = os.getenv("LOG_LEVEL", "INFO")

        # Revolut
        self.revolut_access_token = os.getenv("REVOLUT_ACCESS_TOKEN", "")
        self.revolut_refresh_token = os.getenv("REVOLUT_REFRESH_TOKEN", "")
        self.revolut_device_id = os.getenv("REVOLUT_DEVICE_ID", "")
        self.revolut_sandbox = os.getenv("REVOLUT_SANDBOX", "true").lower() == "true"

        # Binance
        self.binance_api_key = os.getenv("BINANCE_API_KEY", "")
        self.binance_secret = os.getenv("BINANCE_SECRET", "")

        # Bitvavo
        self.bitvavo_api_key = os.getenv("BITVAVO_API_KEY", "")
        self.bitvavo_secret = os.getenv("BITVAVO_SECRET", "")

        # DEGIRO
        self.degiro_username = os.getenv("DEGIRO_USERNAME", "")
        self.degiro_password = os.getenv("DEGIRO_PASSWORD", "")

        # Alpaca
        self.alpaca_api_key = os.getenv("ALPACA_API_KEY", "")
        self.alpaca_secret = os.getenv("ALPACA_SECRET", "")

        # Risk
        self.max_position_size_pct = float(os.getenv("MAX_POSITION_SIZE_PCT", "5"))
        self.stop_loss_pct = float(os.getenv("STOP_LOSS_PCT", "3"))
        self.take_profit_pct = float(os.getenv("TAKE_PROFIT_PCT", "6"))


def load_settings() -> Settings:
    return Settings()
