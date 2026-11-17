"""Protocol constants. These are consensus rules: they are compiled in and
must never be read from a YAML file, or two nodes could disagree."""

PROTOCOL_VERSION = 1
NETWORK_ID = "nearchain-dev-1"
NETWORK_NAME = "NearChain Dev Network"
ADDRESS_PREFIX = "CDC"

MAX_SUPPLY = 21_000_000_000        # 21 billion CDC, in whole units
BLOCK_REWARD = 1_000               # CDC per block (1,000 per minute at 60 s blocks)
TARGET_BLOCK_TIME = 60             # seconds
LWMA_WINDOW = 60                   # intervals used by difficulty adjustment
MAX_SOLVETIME = 6 * TARGET_BLOCK_TIME

MIN_DIFFICULTY = 1
MAX_DIFFICULTY = 1 << 48
GENESIS_DIFFICULTY = 1 << 16
GENESIS_TIMESTAMP = 1_700_000_000

MIN_FEE = 1                        # smallest allowed transaction fee, in CDC
MAX_MESSAGE_BYTES = 16 * 1024 * 1024
