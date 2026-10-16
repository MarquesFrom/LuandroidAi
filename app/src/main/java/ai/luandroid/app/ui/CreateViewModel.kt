package ai.luandroid.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.luandroid.app.data.CreateEngine
import ai.luandroid.app.data.ChatAssistant
import ai.luandroid.app.data.ChatAction
import ai.luandroid.app.data.ChatTurn
import ai.luandroid.app.data.ChatFile
import ai.luandroid.app.data.CreateMode
import ai.luandroid.app.data.EngineUrlPolicy
import ai.luandroid.app.data.EnginePreferences
import ai.luandroid.app.data.MediaAsset
import ai.luandroid.app.data.MediaLibrary
import ai.luandroid.app.data.ProviderConfig
import ai.luandroid.app.data.SUPPORTED_CHAT_MODELS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File

enum class MainPage { CREATE, CHAT, GALLERY, ENGINE }

data class ChatMessage(
    val role: String,
    val content: String,
    val attachments: List<ChatFile> = emptyList(),
)

data class CreateUiState(
    val page: MainPage = MainPage.CREATE,
    val config: ProviderConfig = ProviderConfig(),
    val prompt: String = "",
    val mode: CreateMode = CreateMode.IMAGE,
    val inputImages: List<String?> = listOf(null, null),
    val assets: List<MediaAsset> = emptyList(),
    val busy: Boolean = false,
    val chatBusy: Boolean = false,
    val chatDraft: String = "",
    val chatAttachment: ChatFile? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val pendingChatAction: ChatAction? = null,
    val status: String? = null,
    val error: String? = null,
)

class CreateViewModel(application: Application) : AndroidViewModel(application) {
    private val configStore = EnginePreferences(application)
    private val library = MediaLibrary(application)
    private val engine = CreateEngine()
    private val chatAssistant = ChatAssistant()

    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()
    private var generationJob: Job? = null

    init {
        try {
            _state.value = _state.value.copy(
                config = configStore.load(),
                assets = library.load(),
            )
        } catch (error: Exception) {
            report(error)
        }
    }

    fun selectModel(model: String) {
        require(model in SUPPORTED_CHAT_MODELS) { "Escolha Qwen3 ou Gemma 2 2B." }
        updateConfig { it.copy(chatModel = model) }
    }

    fun setPage(page: MainPage) {
        _state.value = _state.value.copy(page = page, error = null, status = null)
        if (page == MainPage.GALLERY) refreshGallery()
    }

    fun setWorkflow(value: String): Boolean = updateConfig { it.copy(workflowJson = value) }
    fun setPrompt(value: String) { _state.value = _state.value.copy(prompt = value, error = null) }
    fun setMode(value: CreateMode) { _state.value = _state.value.copy(mode = value, error = null) }
    fun setChatDraft(value: String) { _state.value = _state.value.copy(chatDraft = value, error = null) }
    fun removeChatAttachment() { _state.value = _state.value.copy(chatAttachment = null) }

    fun importChatMedia(uri: Uri) {
        viewModelScope.launch {
            try {
                val media = withContext(Dispatchers.IO) { library.importChatMedia(uri) }
                _state.value = _state.value.copy(
                    chatAttachment = ChatFile(
                        media.file.absolutePath,
                        media.mimeType,
                        media.analysisImages.map(File::getAbsolutePath),
                    ),
                    status = if (media.mimeType.startsWith("video/")) {
                        "Vídeo anexado. Até três quadros serão enviados ao modelo visual para análise."
                    } else {
                        "Imagem anexada ao chat."
                    },
                    error = null,
                )
            } catch (error: Exception) {
                report(error)
            }
        }
    }

    fun attachGalleryAsset(asset: MediaAsset) {
        viewModelScope.launch {
            try {
                val file = File(asset.path)
                val analysisImages = withContext(Dispatchers.IO) {
                    library.analysisImages(file, asset.mimeType)
                }
                _state.value = _state.value.copy(
                    chatAttachment = ChatFile(
                        file.absolutePath,
                        asset.mimeType,
                        analysisImages.map(File::getAbsolutePath),
                    ),
                    status = if (asset.mimeType.startsWith("video/")) {
                        "Vídeo da galeria anexado; até três quadros serão analisados."
                    } else {
                        "Imagem da galeria anexada ao chat."
                    },
                    error = null,
                )
            } catch (error: Exception) {
                report(error)
            }
        }
    }

    fun importImage(uri: Uri, slot: Int) {
        viewModelScope.launch {
            try {
                require(slot in 0 until MAX_REFERENCE_IMAGES) { "Posição de imagem inválida." }
                require(slot == 0 || _state.value.inputImages[0] != null) {
                    "Adicione a primeira imagem antes da segunda referência."
                }
                val file = withContext(Dispatchers.IO) { library.importImage(uri) }
                val images = _state.value.inputImages.toMutableList()
                while (images.size <= slot) images.add(null)
                images[slot] = file.absolutePath
                _state.value = _state.value.copy(
                    inputImages = images,
                    status = "Imagem de referência pronta.",
                    error = null,
                )
            } catch (error: Exception) {
                report(error)
            }
        }
    }

    fun removeImage(slot: Int) {
        _state.value = _state.value.copy(
            inputImages = _state.value.inputImages.toMutableList().also { images ->
                if (slot == 0 && images.getOrNull(1) != null) {
                    images[0] = images[1]
                    images[1] = null
                } else if (slot in images.indices) {
                    images[slot] = null
                }
            },
            status = null,
            error = null,
        )
    }

    fun importWorkflow(content: String) {
        try {
            require(content.isNotBlank()) { "O arquivo de workflow está vazio." }
            require(content.length <= MAX_WORKFLOW_CHARS) { "O workflow ultrapassa o limite de 2 MB." }
            val parsed = org.json.JSONObject(content)
            require(parsed.length() > 0) { "O workflow não contém nós." }
            if (setWorkflow(parsed.toString(2))) {
                _state.value = _state.value.copy(status = "Workflow importado e salvo.", error = null)
            }
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
                val inputs = current.inputImages.filterNotNull().map(::File)
                require(inputs.all(File::isFile)) { "Uma imagem de referência não está mais disponível." }
                if (current.mode == CreateMode.EDIT || current.mode == CreateMode.IMAGE_TO_VIDEO) {
                    require(inputs.isNotEmpty()) {
                        "Escolha ao menos uma imagem de referência primeiro."
                    }
                }
                require(inputs.size <= MAX_REFERENCE_IMAGES) { "Use no máximo duas imagens de referência." }
                _state.value = current.copy(busy = true, status = "Enviando para o motor…", error = null)
                val result = withContext(Dispatchers.IO) {
                    engine.create(
                        config = current.config,
                        mode = current.mode,
                        prompt = current.prompt,
                        inputImages = inputs,
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

    fun sendChatMessage() {
        val current = _state.value
        val message = current.chatDraft.trim().ifBlank {
            if (current.chatAttachment != null) "Analise o arquivo anexado." else ""
        }
        if (current.chatBusy || message.isBlank()) return
        val history = current.chatMessages
        val attachments = listOfNotNull(current.chatAttachment)
        _state.value = current.copy(
            chatDraft = "",
            chatAttachment = null,
            chatBusy = true,
            chatMessages = history + ChatMessage("user", message, attachments),
            pendingChatAction = null,
            error = null,
            status = null,
        )
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    chatAssistant.reply(
                        config = current.config,
                        history = history.map { ChatTurn(it.role, it.content, it.attachments) },
                        userMessage = message,
                        attachments = attachments,
                    )
                }
                _state.value = _state.value.copy(
                    chatMessages = _state.value.chatMessages + ChatMessage("assistant", result.response),
                    pendingChatAction = result.pendingAction,
                    status = if (result.pendingAction != null) "Ação pronta; confirme antes de enviar ao motor." else null,
                    error = null,
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                report(error)
            } finally {
                _state.value = _state.value.copy(chatBusy = false)
            }
        }
    }

    fun confirmChatAction() {
        val action = _state.value.pendingChatAction ?: return
        _state.value = _state.value.copy(
            pendingChatAction = null,
            page = MainPage.CREATE,
            prompt = action.prompt,
            mode = action.mode,
            inputImages = List(MAX_REFERENCE_IMAGES) { index ->
                action.referenceImages.getOrNull(index)
            },
            status = "Ação do chat confirmada. Revise e toque em Criar para enviar.",
            error = null,
        )
    }

    fun dismissChatAction() {
        _state.value = _state.value.copy(pendingChatAction = null, status = null)
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
        EngineUrlPolicy.parse(config.baseUrl, "ComfyUI")
        EngineUrlPolicy.parse(config.chatBaseUrl, "Ollama")
        require(config.chatModel in SUPPORTED_CHAT_MODELS) { "Escolha Qwen3 ou Gemma 2 2B para o chat." }
    }

    private fun updateConfig(transform: (ProviderConfig) -> ProviderConfig): Boolean {
        val config = transform(_state.value.config)
        try {
            configStore.save(config)
            _state.value = _state.value.copy(config = config, error = null, status = null)
            return true
        } catch (error: Exception) {
            report(error)
            return false
        }
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
        const val MAX_REFERENCE_IMAGES = 2
    }
}
