package ai.luandroid.app.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object EngineUrlPolicy {
    fun parse(value: String, label: String): HttpUrl {
        val url = value.trim().trimEnd('/').toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Informe uma URL HTTP ou HTTPS válida para $label.")
        require(url.scheme == "https" || url.scheme == "http") {
            "$label precisa usar HTTP ou HTTPS."
        }
        require(url.username.isEmpty() && url.password.isEmpty()) {
            "Não inclua credenciais na URL de $label; use o campo de chave de API."
        }
        require(url.query == null && url.fragment == null) {
            "A URL de $label não pode conter parâmetros nem fragmentos."
        }
        require(isPermitted(url)) {
            "HTTP sem criptografia só é permitido em endereços privados da rede local. Use HTTPS para servidores remotos."
        }
        return url
    }

    fun isPermitted(url: HttpUrl): Boolean =
        url.scheme == "https" || isLocalHost(url.host)

    private fun isLocalHost(host: String): Boolean {
        val normalized = host.trimEnd('.').lowercase()
        if (normalized == "localhost" || normalized.endsWith(".localhost") ||
            normalized.endsWith(".local")
        ) return true

        val ipv4 = normalized.split('.')
        if (ipv4.size == 4 && ipv4.all { it.isNotEmpty() && it.all(Char::isDigit) }) {
            val octets = ipv4.map { it.toIntOrNull()?.takeIf { value -> value in 0..255 } ?: return false }
            return octets[0] == 10 ||
                (octets[0] == 172 && octets[1] in 16..31) ||
                (octets[0] == 192 && octets[1] == 168) ||
                octets[0] == 127 ||
                (octets[0] == 169 && octets[1] == 254)
        }

        if (':' in normalized) {
            val bytes = try {
                java.net.InetAddress.getByName(normalized).address
            } catch (_: java.net.UnknownHostException) {
                return false
            }
            if (bytes.size != 16) return false
            val loopback = bytes.dropLast(1).all { it == 0.toByte() } && bytes.last() == 1.toByte()
            val uniqueLocal = (bytes[0].toInt() and 0xfe) == 0xfc
            val linkLocal = (bytes[0].toInt() and 0xff) == 0xfe &&
                (bytes[1].toInt() and 0xc0) == 0x80
            return loopback || uniqueLocal || linkLocal
        }
        return false
    }
}
