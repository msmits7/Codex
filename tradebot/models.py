"""Domain models shared across the application."""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from dataclasses import dataclass, field


class AssetType(str, Enum):
    CRYPTO = "crypto"
    STOCK = "stock"
    EU_STOCK = "eu_stock"
    COMMODITY = "commodity"
    ETF = "etf"
    BOND = "bond"
    CFD = "cfd"


class Side(str, Enum):
    BUY = "buy"
    SELL = "sell"


class OrderStatus(str, Enum):
    PENDING = "pending"
    FILLED = "filled"
    CANCELLED = "cancelled"
    REJECTED = "rejected"


class SignalType(str, Enum):
    BUY = "buy"
    SELL = "sell"
    HOLD = "hold"


@dataclass
class OHLCV:
    timestamp: datetime
    open: float
    high: float
    low: float
    close: float
    volume: float


@dataclass
class Signal:
    symbol: str
    signal_type: SignalType
    strategy: str
    strength: float
    timestamp: datetime = field(default_factory=datetime.utcnow)
    metadata: dict = field(default_factory=dict)


@dataclass
class Order:
    symbol: str
    side: Side
    quantity: float
    asset_type: AssetType
    order_id: str = ""
    status: OrderStatus = OrderStatus.PENDING
    price: float = 0.0
    filled_price: float = 0.0
    timestamp: datetime = field(default_factory=datetime.utcnow)


@dataclass
class Position:
    symbol: str
    asset_type: AssetType
    quantity: float
    entry_price: float
    current_price: float = 0.0
    unrealized_pnl: float = 0.0
    timestamp: datetime = field(default_factory=datetime.utcnow)

    def update_price(self, price: float) -> None:
        self.current_price = price
        self.unrealized_pnl = (price - self.entry_price) * self.quantity
