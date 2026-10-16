package ai.luandroid.app.data

import org.json.JSONObject

object ImageGenerationParameters {
    private const val MAX_NEGATIVE_PROMPT_LENGTH = 4_000

    fun parse(json: String): JSONObject {
        require(json.isNotBlank()) { "O JSON de parâmetros está vazio." }
        val root = JSONObject(json)
        val values = root.optJSONObject("parameters") ?: root
        val result = JSONObject()

        if (values.has("width") || values.has("height")) {
            require(values.has("width") && values.has("height")) {
                "O JSON precisa informar width e height juntos."
            }
            val width = values.opt("width") as? Number
                ?: throw IllegalArgumentException("width precisa ser um número inteiro.")
            val height = values.opt("height") as? Number
                ?: throw IllegalArgumentException("height precisa ser um número inteiro.")
            require(width.toDouble() % 1.0 == 0.0 && height.toDouble() % 1.0 == 0.0) {
                "width e height precisam ser números inteiros."
            }
            val widthValue = width.toInt()
            val heightValue = height.toInt()
            require(
                widthValue in 64..8192 && heightValue in 64..8192 &&
                    widthValue % 8 == 0 && heightValue % 8 == 0,
            ) {
                "As dimensões no JSON devem ser múltiplos de 8 entre 64 e 8192."
            }
            result.put("width", widthValue).put("height", heightValue)
        }

        if (values.has("seed")) {
            val seedValue = values.opt("seed") as? Number
                ?: throw IllegalArgumentException("O seed precisa ser um número inteiro.")
            require(seedValue.toDouble() >= 0.0 && seedValue.toDouble() % 1.0 == 0.0) {
                "O seed precisa ser um número inteiro não negativo."
            }
            val seed = seedValue.toLong()
            result.put("seed", seed)
        }
        if (values.has("steps")) {
            val stepsValue = values.opt("steps") as? Number
                ?: throw IllegalArgumentException("steps precisa ser um número inteiro.")
            require(stepsValue.toDouble() % 1.0 == 0.0) { "steps precisa ser um número inteiro." }
            val steps = stepsValue.toInt()
            require(steps in 1..150) { "O número de steps precisa estar entre 1 e 150." }
            result.put("steps", steps)
        }
        if (values.has("cfg") || values.has("cfg_scale")) {
            val cfg = (values.opt(if (values.has("cfg")) "cfg" else "cfg_scale") as? Number)
                ?.toDouble() ?: Double.NaN
            require(cfg.isFinite() && cfg in 0.0..30.0) { "O valor cfg precisa estar entre 0 e 30." }
            result.put("cfg", cfg)
        }
        if (values.has("denoise")) {
            val denoise = (values.opt("denoise") as? Number)?.toDouble() ?: Double.NaN
            require(denoise.isFinite() && denoise in 0.0..1.0) {
                "O valor denoise precisa estar entre 0 e 1."
            }
            result.put("denoise", denoise)
        }
        listOf("sampler_name", "scheduler").forEach { key ->
            if (values.has(key)) {
                val value = values.optString(key)
                require(value.length in 1..100 && value.matches(Regex("[A-Za-z0-9_-]+"))) {
                    "Valor inválido para '$key' nos parâmetros da imagem."
                }
                result.put(key, value)
            }
        }
        if (values.has("negative_prompt")) {
            val negativePrompt = values.optString("negative_prompt")
            require(negativePrompt.length <= MAX_NEGATIVE_PROMPT_LENGTH) {
                "O negative_prompt ultrapassa o limite de 4.000 caracteres."
            }
            result.put("negative_prompt", negativePrompt)
        }
        require(result.length() > 0) {
            "O JSON não contém parâmetros de imagem compatíveis."
        }
        return result
    }

}
