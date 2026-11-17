"""Height-based emission. Total issued through height h is min(1000*h, MAX_SUPPLY).
The cap is enforced by the formula itself: once it is reached, every block pays 0."""

from .consensus import BLOCK_REWARD, MAX_SUPPLY

CAP_HEIGHT = MAX_SUPPLY // BLOCK_REWARD  # 21,000,000


def emitted_through(height: int) -> int:
    return min(BLOCK_REWARD * height, MAX_SUPPLY)


def reward_at(height: int) -> int:
    """Subsidy a block at this height may pay. The genesis block pays nothing."""
    if height <= 0:
        return 0
    return emitted_through(height) - emitted_through(height - 1)
