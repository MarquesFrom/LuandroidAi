package ai.luandroid.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Suppress("UseKtx")
class SecretStore(context: Context) {
    private val preferences = context.getSharedPreferences("engine_secret", Context.MODE_PRIVATE)

    fun save(value: String, account: String = DEFAULT_ACCOUNT) {
        validateAccount(account)
        if (value.isEmpty()) {
            preferences.edit().remove(keyFor(account)).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val stored = cipher.iv + encrypted
        check(preferences.edit().putString(keyFor(account), Base64.encodeToString(stored, Base64.NO_WRAP)).commit()) {
            "Não foi possível salvar a chave de API criptografada."
        }
    }

    fun load(account: String = DEFAULT_ACCOUNT): String {
        validateAccount(account)
        val encoded = preferences.getString(keyFor(account), null) ?: return ""
        return try {
            val stored = Base64.decode(encoded, Base64.NO_WRAP)
            require(stored.size > IV_LENGTH) { "Os dados salvos da chave de API estão incompletos." }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, stored.copyOfRange(0, IV_LENGTH)),
            )
            String(cipher.doFinal(stored.copyOfRange(IV_LENGTH, stored.size)), Charsets.UTF_8)
        } catch (error: Exception) {
            throw IllegalStateException(
                "Não foi possível descriptografar a chave de API salva. Informe-a novamente nas configurações do motor.",
                error,
            )
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyFor(account: String): String =
        if (account == DEFAULT_ACCOUNT) LEGACY_KEY else "encrypted_api_key_$account"

    private fun validateAccount(account: String) {
        require(account.matches(Regex("[a-z][a-z0-9_]{0,31}"))) {
            "A chave de armazenamento solicitada não é válida."
        }
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "luandroid-engine-api-key"
        const val DEFAULT_ACCOUNT = "engine"
        const val LEGACY_KEY = "encrypted_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
