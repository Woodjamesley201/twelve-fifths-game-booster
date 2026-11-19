package com.twelvefifths.booster.optimize

import kotlinx.coroutines.flow.Flow

/**
 * Starts and restores a gaming session. Implementations must never terminate
 * essential system processes or change critical OS settings.
 *
 * Allowed actions: [android.app.ActivityManager.killBackgroundProcesses] for
 * *user-selected, non-system* packages (a no-op for other apps on Android 14+);
 * Do Not Disturb when the user has granted policy access; opening battery-saver
 * or game-mode Settings screens; launching the game. Everything changed must be
 * restored in [endSession].
 *
 * Owner: Script Nathan.
 */
interface Optimizer {
    val activeSession: Flow<OptimizationSession?>

    suspend fun startSession(
        game: GameEntry,
        profile: OptimizationProfile,
    ): Result<OptimizationSession>

    suspend fun endSession(): Result<OptimizationSession> = endSession(SessionEndReason.USER)

    suspend fun endSession(reason: SessionEndReason): Result<OptimizationSession>
}
