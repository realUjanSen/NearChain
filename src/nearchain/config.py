"""Load and validate YAML settings.

Local settings only: ports, folders, mining preferences. Consensus parameters are
rejected outright, so a config file can never change the rules of the network."""

import copy
import os

import yaml

from .consensus import (BLOCK_REWARD, GENESIS_DIFFICULTY, LWMA_WINDOW, MAX_SUPPLY,
                        MIN_FEE, NETWORK_ID, PROTOCOL_VERSION, TARGET_BLOCK_TIME)


class ConfigError(ValueError):
    pass


CONSENSUS_KEYS = {
    "max_supply", "block_reward", "block_interval", "target_block_time", "lwma_window",
    "network_id", "protocol_version", "genesis", "difficulty_bounds", "min_fee",
    "emission", "consensus", "genesis_difficulty",
}

# (kind, low/choices, high, default)
SHARED_SCHEMA = {
    "node": {
        "listen_port": ("int", 0, 65535, 8333),
        "listen_host": ("str", None, None, "0.0.0.0"),
        "data_dir": ("str", None, None, "data"),
        "peers": ("list", None, None, []),
    },
    "storage": {
        "chain_file": ("str", None, None, "chain.json"),
        "wallet_file": ("str", None, None, "wallet.json"),
    },
}

PLATFORM_SCHEMA = {
    "mining": {
        "default_mode": ("choice", ["foreground", "background"], None, "foreground"),
        "batch_size": ("int", 100, 10_000_000, 5000),
        "background_sleep_ms": ("int", 0, 5000, 50),
    },
    "ui": {
        "refresh_ms": ("int", 100, 10_000, 500),
    },
}


def _read(path):
    if not path or not os.path.exists(path):
        return {}
    with open(path, encoding="utf-8") as f:
        data = yaml.safe_load(f) or {}
    if not isinstance(data, dict):
        raise ConfigError(f"{path}: the top level must be a mapping")
    return data


def _check_value(where, spec, value):
    kind, low, high, _default = spec
    if kind == "int":
        if isinstance(value, bool) or not isinstance(value, int):
            raise ConfigError(f"{where} must be a whole number")
        if not low <= value <= high:
            raise ConfigError(f"{where} must be between {low} and {high}")
    elif kind == "str":
        if not isinstance(value, str) or not value:
            raise ConfigError(f"{where} must be non-empty text")
    elif kind == "list":
        if not isinstance(value, list) or not all(isinstance(v, str) for v in value):
            raise ConfigError(f"{where} must be a list of text entries")
    elif kind == "choice":
        if value not in low:
            raise ConfigError(f"{where} must be one of: {', '.join(low)}")
    return value


def _apply(data, path, schema):
    result = {}
    for section, values in data.items():
        if section in CONSENSUS_KEYS:
            raise ConfigError(f"{path}: '{section}' is a consensus parameter and cannot be set locally")
        if section not in schema:
            raise ConfigError(f"{path}: unknown section '{section}'")
        if not isinstance(values, dict):
            raise ConfigError(f"{path}: section '{section}' must be a mapping")
        result[section] = {}
        for key, value in values.items():
            if key in CONSENSUS_KEYS:
                raise ConfigError(f"{path}: '{key}' is a consensus parameter and cannot be set locally")
            if key not in schema[section]:
                raise ConfigError(f"{path}: unknown setting '{section}.{key}'")
            result[section][key] = _check_value(f"{path}: {section}.{key}", schema[section][key], value)
    return result


def _defaults(schema):
    return {s: {k: copy.deepcopy(spec[3]) for k, spec in keys.items()} for s, keys in schema.items()}


def load_config(shared_path, platform_path=None):
    """Return a merged settings dictionary with defaults filled in."""
    config = _defaults({**SHARED_SCHEMA, **PLATFORM_SCHEMA})
    shared = _apply(_read(shared_path), shared_path, SHARED_SCHEMA)
    platform = _apply(_read(platform_path), platform_path or "platform config", PLATFORM_SCHEMA)
    for section in list(shared) + list(platform):
        source = shared if section in shared else platform
        config[section].update(source[section])
    return config


def protocol_summary():
    """Consensus values, for display only. These are not configurable."""
    return {
        "network_id": NETWORK_ID,
        "protocol_version": PROTOCOL_VERSION,
        "max_supply": MAX_SUPPLY,
        "block_reward": BLOCK_REWARD,
        "target_block_time": TARGET_BLOCK_TIME,
        "lwma_window": LWMA_WINDOW,
        "min_fee": MIN_FEE,
        "genesis_difficulty": GENESIS_DIFFICULTY,
    }
