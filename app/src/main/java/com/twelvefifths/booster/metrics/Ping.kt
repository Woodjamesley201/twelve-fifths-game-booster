package com.twelvefifths.booster.metrics

import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Proxy
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Host and port for the TCP-connect latency probe. Apps cannot send ICMP, so
 * "ping" here is the time to complete a TCP handshake (SYN → SYN/ACK). DNS
 * resolution is done first and is not included in the measured time.
 *
 * Owner: Script Nathan.
 */
data class PingTarget(val host: String, val port: Int) {
    val label: String
        get() = if (host.contains(':')) "[$host]:$port" else "$host:$port"

    fun isValid(): Boolean = isValidHost(host) && port in 1..65_535

    companion object {
        val DEFAULT = PingTarget("8.8.8.8", 443)

        /**
         * Parses `host`, `host:port`, or `[ipv6]:port`. A missing port defaults
         * to 443. Returns null for anything invalid.
         */
        fun parse(input: String?, defaultPort: Int = 443): PingTarget? {
            val text = input?.trim().orEmpty()
            if (text.isEmpty() || text.any { it.isWhitespace() }) return null
            val (host, port) = when {
                text.startsWith("[") -> {
                    val close = text.indexOf(']')
                    if (close <= 1) return null
                    val h = text.substring(1, close)
                    val rest = text.substring(close + 1)
                    val p = when {
                        rest.isEmpty() -> defaultPort
                        rest.startsWith(":") -> rest.drop(1).toIntOrNull() ?: return null
                        else -> return null
                    }
                    h to p
                }
                text.count { it == ':' } == 1 -> {
                    val h = text.substringBefore(':')
                    val p = text.substringAfter(':').toIntOrNull() ?: return null
                    h to p
                }
                text.count { it == ':' } > 1 -> text to defaultPort // bare IPv6
                else -> text to defaultPort
            }
            val target = PingTarget(host, port)
            return if (target.isValid()) target else null
        }

        private val HOSTNAME = Regex("""^[A-Za-z0-9]([A-Za-z0-9-]{0,62})(\.[A-Za-z0-9]([A-Za-z0-9-]{0,62}))*\.?$""")

        private fun isValidHost(host: String): Boolean {
            if (host.isEmpty() || host.length > 253) return false
            if (host.contains(':')) return host.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }
            return HOSTNAME.matches(host)
        }
    }
}

enum class PingStatus {
    /** A handshake completed; [MetricsSnapshot.pingMs] holds the time. */
    OK,
    TIMEOUT,
    /** Refused, no route, or another socket error. */
    UNREACHABLE,
    UNKNOWN_HOST,
    NO_NETWORK,
    INVALID_TARGET,
    /** No probe has completed yet. */
    NOT_MEASURED,
}

data class PingResult(val status: PingStatus, val millis: Long? = null, val detail: String? = null) {
    companion object {
        val NOT_MEASURED = PingResult(PingStatus.NOT_MEASURED)
    }
}

fun interface HostResolver {
    @Throws(IOException::class)
    fun resolve(host: String): InetAddress
}

fun interface TcpConnector {
    /** Opens and closes a TCP connection, throwing on failure. */
    @Throws(IOException::class)
    fun connect(address: InetSocketAddress, timeoutMs: Int)
}

/**
 * Timed TCP connect. Pure apart from the injected resolver / connector / clock,
 * so result handling is JVM-testable.
 */
class TcpPingProber(
    private val resolver: HostResolver = HostResolver { InetAddress.getByName(it) },
    private val connector: TcpConnector = TcpConnector { address, timeout ->
        Socket(Proxy.NO_PROXY).use { socket ->
            socket.tcpNoDelay = true
            socket.connect(address, timeout)
        }
    },
    private val nanoClock: () -> Long = System::nanoTime,
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
) {

    fun probe(target: PingTarget, networkAvailable: Boolean = true): PingResult {
        if (!target.isValid()) return PingResult(PingStatus.INVALID_TARGET, detail = target.label)
        if (!networkAvailable) return PingResult(PingStatus.NO_NETWORK)
        val address = try {
            resolver.resolve(target.host)
        } catch (e: UnknownHostException) {
            return PingResult(PingStatus.UNKNOWN_HOST, detail = e.message)
        } catch (e: Throwable) {
            return PingResult(PingStatus.UNKNOWN_HOST, detail = e.message ?: e.javaClass.simpleName)
        }
        val started = nanoClock()
        return try {
            connector.connect(InetSocketAddress(address, target.port), timeoutMs)
            val elapsedNs = (nanoClock() - started).coerceAtLeast(0L)
            // Round up so a sub-millisecond LAN handshake is reported as 1 ms, not 0.
            val ms = ((elapsedNs + 999_999L) / 1_000_000L).coerceAtLeast(1L)
            PingResult(PingStatus.OK, millis = ms)
        } catch (e: SocketTimeoutException) {
            PingResult(PingStatus.TIMEOUT, detail = e.message)
        } catch (e: ConnectException) {
            PingResult(PingStatus.UNREACHABLE, detail = e.message ?: "refused")
        } catch (e: NoRouteToHostException) {
            PingResult(PingStatus.UNREACHABLE, detail = e.message ?: "no route")
        } catch (e: Throwable) {
            // IOException, SecurityException (no INTERNET), etc.
            PingResult(PingStatus.UNREACHABLE, detail = e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 1_500
    }
}
