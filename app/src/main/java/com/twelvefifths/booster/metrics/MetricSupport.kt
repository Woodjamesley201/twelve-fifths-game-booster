package com.twelvefifths.booster.metrics

/**
 * Whether a metric can be shown. The UI must never invent a number
 * for [UNSUPPORTED] or [NEEDS_PERMISSION] — show the support state instead.
 *
 * Owner: Script Nathan.
 */
enum class MetricSupport {
    /** A real measurement is available (the value may still be null for this sample). */
    SUPPORTED,

    /** The metric is readable after the user grants a specific permission. */
    NEEDS_PERMISSION,

    /** Android does not expose this metric to an unprivileged app. */
    UNSUPPORTED,
}
