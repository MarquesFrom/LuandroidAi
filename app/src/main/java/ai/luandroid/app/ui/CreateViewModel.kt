package ai.luandroid.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.luandroid.app.data.CreateEngine
import ai.luandroid.app.data.CreateMode
import ai.luandroid.app.data.EngineKind
import ai.luandroid.app.data.EngineUrlPolicy
import ai.luandroid.app.data.EnginePreferences
import ai.luandroid.app.data.MediaAsset
import ai.luandroid.app.data.MediaLibrary
import ai.luandroid.app.data.ProviderConfig
import ai.luandroid.app.data.SecretStore
import ai.luandroid.app.data.OpenAiEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File

enum class MainPage { CREATE, GALLERY, ENGINE }

data class CreateUiState(
    val page: MainPage = MainPage.CREATE,
    val config: ProviderConfig = ProviderConfig(),
    val apiKey: String = "",
    val prompt: String = "",
    val mode: CreateMode = CreateMode.IMAGE,
    val inputImage: String? = null,
    val assets: List<MediaAsset> = emptyList(),
    val models: List<String> = emptyList(),
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
)

class CreateViewModel(application: Application) : AndroidViewModel(application) {
    private val configStore = EnginePreferences(application)
    private val secretStore = SecretStore(application)
    private val library = MediaLibrary(application)
    private val engine = CreateEngine()

    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()
    private var generationJob: Job? = null

    init {
        try {
            _state.value = _state.value.copy(
                config = configStore.load(),
                apiKey = secretStore.load(),
                assets = library.load(),
            )
        } catch (error: Exception) {
            report(error)
        }
    }

    fun setPage(page: MainPage) {
        _state.value = _state.value.copy(page = page, error = null, status = null)
        if (page == MainPage.GALLERY) refreshGallery()
    }

    fun setEngine(engine: EngineKind) = updateConfig { it.copy(engine = engine) }
    fun setName(value: String) = updateConfig { it.copy(name = value) }
    fun setBaseUrl(value: String) = updateConfig { it.copy(baseUrl = value) }
    fun setModel(value: String) = updateConfig { it.copy(model = value) }
    fun setWorkflow(value: String) = updateConfig { it.copy(workflowJson = value) }
    fun setApiKey(value: String) { _state.value = _state.value.copy(apiKey = value, error = null) }
    fun setPrompt(value: String) { _state.value = _state.value.copy(prompt = value, error = null) }
    fun setMode(value: CreateMode) { _state.value = _state.value.copy(mode = value, error = null) }

    fun saveEngine() {
        if (_state.value.busy) return
        viewModelScope.launch {
            try {
                val current = _state.value
                validateEngine(current.config)
                withContext(Dispatchers.IO) {
                    secretStore.save(current.apiKey)
                    configStore.save(current.config)
                }
                _state.value = _state.value.copy(status = "Motor salvo com segurança.", error = null)
            } catch (error: Exception) {
                report(error)
            }
        }
    }

    fun discoverModels() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, status = "Buscando modelos…", error = null)
            try {
                val current = _state.value
                require(current.config.engine == EngineKind.OPENAI_COMPATIBLE) {
                    "A busca automática de modelos está disponível para endpoints OpenAI-compatible."
                }
                val found = withContext(Dispatchers.IO) {
                    engine.discoverModels(current.config, current.apiKey)
                }
                require(found.isNotEmpty()) { "O endpoint não retornou modelos. Informe o ID manualmente." }
                _state.value = _state.value.copy(
                    models = found,
                    status = "${found.size} modelo(s) encontrado(s).",
                    error = null,
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                report(error)
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    fun importImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { library.importImage(uri) }
                _state.value = _state.value.copy(
                    inputImage = file.absolutePath,
                    status = "Imagem pronta para uso.",
                    error = null,
                )
            } catch (error: Exception) {
                report(error)
            }
        }
    }

    fun removeImage() {
        _state.value = _state.value.copy(inputImage = null, status = null, error = null)
    }

    fun importWorkflow(content: String) {
        try {
            require(content.isNotBlank()) { "O arquivo de workflow está vazio." }
            require(content.length <= MAX_WORKFLOW_CHARS) { "O workflow ultrapassa o limite de 2 MB." }
            val parsed = org.json.JSONObject(content)
            require(parsed.length() > 0) { "O workflow não contém nós." }
            setWorkflow(parsed.toString(2))
            _state.value = _state.value.copy(status = "Workflow importado.", error = null)
        } catch (error: Exception) {
            report(error)
        }
    }

    fun showError(message: String) {
        _state.value = _state.value.copy(error = message, status = null, busy = false)
    }

    fun create() {
        if (_state.value.busy) return
        generationJob = viewModelScope.launch {
            val current = _state.value
            try {
                validateEngine(current.config)
                require(current.prompt.isNotBlank()) { "Escreva uma descrição antes de criar." }
                val input = current.inputImage?.let(::File)
                if (current.mode == CreateMode.EDIT || current.mode == CreateMode.IMAGE_TO_VIDEO) {
                    require(input?.isFile == true) { "Escolha uma imagem de referência primeiro." }
                }
                _state.value = current.copy(busy = true, status = "Enviando para o motor…", error = null)
                val result = withContext(Dispatchers.IO) {
                    engine.create(
                        config = current.config,
                        apiKey = current.apiKey,
                        mode = current.mode,
                        prompt = current.prompt,
                        inputImage = input,
                        save = library::save,
                    )
                }
                _state.value = _state.value.copy(
                    assets = (result + _state.value.assets).distinctBy { it.path }.take(100),
                    status = if (result.size == 1) "Criação salva na galeria." else "${result.size} arquivos salvos na galeria.",
                    error = null,
                )
            } catch (error: Exception) {
                if (error is CancellationException) {
                    _state.value = _state.value.copy(
                        busy = false,
                        status = "Espera cancelada. O servidor pode continuar processando a tarefa.",
                        error = null,
                    )
                    throw error
                }
                report(error)
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    fun cancel() {
        generationJob?.cancel()
        _state.value = _state.value.copy(
            busy = false,
            status = "Espera cancelada. Se o motor já recebeu o pedido, ele ainda pode terminar de processá-lo.",
            error = null,
        )
    }

    fun clearMessage() {
        _state.value = _state.value.copy(status = null, error = null)
    }

    private fun refreshGallery() {
        viewModelScope.launch {
            try {
                val assets = withContext(Dispatchers.IO) { library.load().asReversed() }
                _state.value = _state.value.copy(assets = assets, error = null)
            } catch (error: Exception) {
                report(error)
            }
        }
    }

    private fun validateEngine(config: ProviderConfig) {
        require(config.baseUrl.isNotBlank()) { "Informe o endereço do motor nas configurações." }
        when (config.engine) {
            EngineKind.OPENAI_COMPATIBLE -> {
                OpenAiEndpoint.parseBaseUrl(config.baseUrl)
                require(config.model.isNotBlank()) { "Informe o ID do modelo de imagem." }
                require(config.model.length <= 200) { "O ID do modelo é muito longo." }
            }
            EngineKind.COMFYUI -> {
                EngineUrlPolicy.parse(config.baseUrl, "ComfyUI")
                require(config.workflowJson.isNotBlank()) { "Importe um workflow ComfyUI nas configurações." }
            }
        }
    }

    private fun updateConfig(transform: (ProviderConfig) -> ProviderConfig) {
        _state.value = _state.value.copy(config = transform(_state.value.config), error = null, status = null)
    }

    private fun report(error: Throwable) {
        val message = when (error) {
            is kotlinx.coroutines.CancellationException -> "Operação cancelada."
            else -> error.message?.takeIf(String::isNotBlank) ?: "Ocorreu um erro inesperado."
        }
        _state.value = _state.value.copy(error = message, status = null, busy = false)
    }

    private companion object {
        const val MAX_WORKFLOW_CHARS = 2 * 1024 * 1024
    }
}
