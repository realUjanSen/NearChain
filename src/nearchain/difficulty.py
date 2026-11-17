"""LWMA difficulty adjustment. Depends on recent timestamps only, never on height,
so the chain does not get harder just by getting longer."""

from dataclasses import dataclass

from .consensus import LWMA_WINDOW, MAX_DIFFICULTY, MAX_SOLVETIME, MIN_DIFFICULTY, TARGET_BLOCK_TIME


@dataclass(frozen=True)
class Sample:
    timestamp: int
    difficulty: int


def next_difficulty(samples):
    """`samples` is oldest first; the last entry is the tip."""
    if not samples:
        return MIN_DIFFICULTY
    if len(samples) == 1:
        return samples[0].difficulty

    n = min(len(samples) - 1, LWMA_WINDOW)
    window = samples[-(n + 1):]
    weighted_solvetime = 0
    difficulty_sum = 0
    for i in range(1, n + 1):
        solve = window[i].timestamp - window[i - 1].timestamp
        solve = min(max(solve, 1), MAX_SOLVETIME)
        weighted_solvetime += i * solve          # newest interval has the largest weight
        difficulty_sum += window[i].difficulty

    average = difficulty_sum // n
    k = n * (n + 1) // 2 * TARGET_BLOCK_TIME     # value when every solve time is on target
    nxt = average * k // weighted_solvetime
    return max(MIN_DIFFICULTY, min(MAX_DIFFICULTY, nxt))
