package ai.luandroid.app.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.luandroid.app.data.CreateMode
import ai.luandroid.app.data.EnginePreset
import ai.luandroid.app.data.EngineKind
import ai.luandroid.app.data.MediaAsset
import java.io.File
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFB7A0FF),
                    secondary = Color(0xFFFFB69E),
                    background = Color(0xFF111113),
                    surface = Color(0xFF19191D),
                ),
            ) {
                LuandroidApp()
            }
        }
    }
}

@Composable
private fun LuandroidApp(viewModel: CreateViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val firstImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.importImage(it, 0) }
    }
    val secondImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.importImage(it, 1) }
    }
    val workflowPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_WORKFLOW_BYTES) { "O arquivo de workflow ultrapassa 2 MB." }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: throw IllegalStateException("Não foi possível abrir o workflow selecionado.")
                viewModel.importWorkflow(bytes.toString(Charsets.UTF_8))
            } catch (error: Exception) {
                viewModel.showError(error.message ?: "Não foi possível ler o workflow selecionado.")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("LU CREATE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text("Criação visual no seu ritmo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Gere e edite imagens. Transforme uma imagem em vídeo usando seu motor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.page == MainPage.CREATE,
                    onClick = { viewModel.setPage(MainPage.CREATE) },
                    icon = { Text("✦") },
                    label = { Text("Criar") },
                )
                NavigationBarItem(
                    selected = state.page == MainPage.CHAT,
                    onClick = { viewModel.setPage(MainPage.CHAT) },
                    icon = { Text("✉") },
                    label = { Text("Chat IA") },
                )
                NavigationBarItem(
                    selected = state.page == MainPage.GALLERY,
                    onClick = { viewModel.setPage(MainPage.GALLERY) },
                    icon = { Text("▧") },
                    label = { Text("Galeria") },
                )
                NavigationBarItem(
                    selected = state.page == MainPage.ENGINE,
                    onClick = { viewModel.setPage(MainPage.ENGINE) },
                    icon = { Text("⚙") },
                    label = { Text("Motor") },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.error?.let { MessageCard(it, isError = true, onDismiss = viewModel::clearMessage) }
            state.status?.let { MessageCard(it, isError = false, onDismiss = viewModel::clearMessage) }
            Box(Modifier.weight(1f)) {
                when (state.page) {
                    MainPage.CREATE -> CreatePage(
                        state = state,
                        onPromptChange = viewModel::setPrompt,
                        onModeChange = viewModel::setMode,
                        onPickImage = { slot ->
                            if (slot == 0) firstImagePicker.launch("image/*")
                            else secondImagePicker.launch("image/*")
                        },
                        onRemoveImage = viewModel::removeImage,
                        onCreate = viewModel::create,
                        onCancel = viewModel::cancel,
                        onOpenEngine = { viewModel.setPage(MainPage.ENGINE) },
                        onOpenAsset = { openAsset(context, it) },
                    )
                    MainPage.CHAT -> ChatPage(
                        state = state,
                        onDraftChange = viewModel::setChatDraft,
                        onSend = viewModel::sendChatMessage,
                        onConfirmAction = viewModel::confirmChatAction,
                        onDismissAction = viewModel::dismissChatAction,
                    )
                    MainPage.GALLERY -> GalleryPage(state.assets, onOpen = { openAsset(context, it) })
                    MainPage.ENGINE -> EnginePage(
                        state = state,
                        onApplyPreset = viewModel::applyPreset,
                        onEngineChange = viewModel::setEngine,
                        onNameChange = viewModel::setName,
                        onUrlChange = viewModel::setBaseUrl,
                        onModelChange = viewModel::setModel,
                        onApiKeyChange = viewModel::setApiKey,
                        onChatUrlChange = viewModel::setChatBaseUrl,
                        onChatModelChange = viewModel::setChatModel,
                        onSearchUrlChange = viewModel::setSearchUrl,
                        onChatApiKeyChange = viewModel::setChatApiKey,
                        onImportWorkflow = { workflowPicker.launch("application/json") },
                        onSave = viewModel::saveEngine,
                        onDiscoverModels = viewModel::discoverModels,
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatePage(
    state: CreateUiState,
    onPromptChange: (String) -> Unit,
    onModeChange: (CreateMode) -> Unit,
    onPickImage: (Int) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
    onOpenEngine: () -> Unit,
    onOpenAsset: (MediaAsset) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("Imagem", state.mode == CreateMode.IMAGE) { onModeChange(CreateMode.IMAGE) }
            ModeChip("Editar", state.mode == CreateMode.EDIT) { onModeChange(CreateMode.EDIT) }
            ModeChip("Imagem → vídeo", state.mode == CreateMode.IMAGE_TO_VIDEO) {
                onModeChange(CreateMode.IMAGE_TO_VIDEO)
            }
        }
        OutlinedTextField(
            value = state.prompt,
            onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Descreva sua criação") },
            placeholder = { Text("Ex.: luz de fim de tarde, textura de filme…") },
            minLines = 4,
            maxLines = 8,
        )
        repeat(2) { slot ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { onPickImage(slot) },
                    enabled = !state.busy && (slot == 0 || state.inputImages[0] != null),
                ) {
                    Text(
                        when {
                            state.inputImages.getOrNull(slot) != null -> "Trocar referência ${slot + 1}"
                            slot == 0 -> "Adicionar referência 1"
                            else -> "Adicionar referência 2"
                        },
                    )
                }
                if (state.inputImages.getOrNull(slot) != null) {
                    Text("Imagem ${slot + 1} anexada", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onRemoveImage(slot) }, enabled = !state.busy) { Text("Remover") }
                }
            }
            state.inputImages.getOrNull(slot)?.let {
                InputImagePreview(it)
            }
        }

        val engineName = when (state.config.engine) {
            EngineKind.OPENAI_COMPATIBLE -> state.config.name.ifBlank { "OpenAI-compatible" }
            EngineKind.COMFYUI -> state.config.name.ifBlank { "ComfyUI" }
        }
        Card(Modifier.fillMaxWidth().clickable(onClick = onOpenEngine)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Motor: $engineName", fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        state.config.baseUrl.isBlank() -> "Configure um endpoint para começar."
                        state.config.engine == EngineKind.COMFYUI && state.config.workflowJson.isBlank() ->
                            "Falta importar um workflow ComfyUI."
                        state.mode == CreateMode.IMAGE_TO_VIDEO && state.config.engine != EngineKind.COMFYUI ->
                            "Imagem → vídeo requer ComfyUI e um workflow de vídeo."
                        else -> state.config.baseUrl
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(
            onClick = onCreate,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text("Criando…")
            } else {
                Text(if (state.mode == CreateMode.IMAGE_TO_VIDEO) "Criar vídeo" else "Criar imagem")
            }
        }
        if (state.busy) {
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Parar de esperar")
            }
        }
        Text(
            "A geração acontece no motor configurado. O app não adiciona uma camada própria de moderação; " +
                "o serviço ou modelo escolhido ainda pode aplicar as regras dele.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.assets.firstOrNull()?.let { asset ->
            Text("Última criação", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            MediaCard(asset, onClick = { onOpenAsset(asset) })
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EnginePage(
    state: CreateUiState,
    onApplyPreset: (EnginePreset) -> Unit,
    onEngineChange: (EngineKind) -> Unit,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onChatUrlChange: (String) -> Unit,
    onChatModelChange: (String) -> Unit,
    onSearchUrlChange: (String) -> Unit,
    onChatApiKeyChange: (String) -> Unit,
    onImportWorkflow: () -> Unit,
    onSave: () -> Unit,
    onDiscoverModels: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Escolha onde a criação será processada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Presets de motor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        EnginePreset.entries.forEach { preset ->
            FilterChip(
                selected = state.config.name == preset.providerName && state.config.baseUrl == preset.baseUrl,
                onClick = { onApplyPreset(preset) },
                label = { Text(preset.label) },
            )
        }
        EngineKind.entries.forEach { kind ->
            Row(
                Modifier.fillMaxWidth().clickable { onEngineChange(kind) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.config.engine == kind, onClick = { onEngineChange(kind) })
                Column {
                    Text(if (kind == EngineKind.OPENAI_COMPATIBLE) "OpenAI-compatible" else "ComfyUI")
                    Text(
                        if (kind == EngineKind.OPENAI_COMPATIBLE) "Geração e edição de imagem via API" else "Imagem, edição e workflows de vídeo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.config.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome do motor") },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.config.baseUrl,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Endereço base") },
            placeholder = {
                Text(if (state.config.engine == EngineKind.COMFYUI) "http://192.168.1.20:8188" else "http://192.168.1.20:11434/v1")
            },
            supportingText = {
                Text("No celular, use o IP acessível do servidor; 127.0.0.1 aponta para o próprio celular.")
            },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API key (opcional)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            supportingText = { Text("Armazenada criptografada pelo Android Keystore.") },
        )
        Text("Chat assistente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = state.config.chatBaseUrl,
            onValueChange = onChatUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Endpoint OpenAI-compatible do chat") },
            placeholder = { Text("https://api.openai.com/v1") },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.config.chatModel,
            onValueChange = onChatModelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Modelo de chat") },
            placeholder = { Text("gpt-4.1-mini") },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.chatApiKey,
            onValueChange = onChatApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API key do chat (opcional)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            supportingText = { Text("Se vazio, o chat reutiliza a chave do motor de imagem.") },
        )
        OutlinedTextField(
            value = state.config.searchUrl,
            onValueChange = onSearchUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL base SearXNG para buscas (opcional)") },
            placeholder = { Text("https://search.example.org") },
            singleLine = true,
            supportingText = { Text("O chat pesquisa na web quando essa instância SearXNG está configurada.") },
        )
        if (state.config.engine == EngineKind.OPENAI_COMPATIBLE) {
            OutlinedTextField(
                value = state.config.model,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ID do modelo de imagem") },
                singleLine = true,
            )
            OutlinedButton(onClick = onDiscoverModels, enabled = !state.busy) {
                Text(if (state.busy) "Conectando…" else "Buscar modelos")
            }
            state.models.forEach { model ->
                TextButton(onClick = { onModelChange(model) }) { Text(model) }
            }
        } else {
            Text(
                "Importe um workflow no formato ComfyUI API. Placeholders aceitos: " +
                    "{{PROMPT}}, {{IMAGE_FILENAME}}, {{IMAGE_SUBFOLDER}}, {{IMAGE_TYPE}}, " +
                    "{{IMAGE2_FILENAME}}, {{IMAGE2_SUBFOLDER}} e {{IMAGE2_TYPE}}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onImportWorkflow, enabled = !state.busy) {
                Text(if (state.config.workflowJson.isBlank()) "Importar workflow JSON" else "Trocar workflow JSON")
            }

            if (state.config.workflowJson.isNotBlank()) {
                Text("${state.config.workflowJson.length} caracteres de workflow carregados.")
            }
        }
        Button(onClick = onSave, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Salvar motor")
        }
        Text(
            "HTTP sem TLS serve apenas para redes locais confiáveis. Pela internet, use HTTPS; " +
                "credenciais e prompts em HTTP podem ser lidos por terceiros.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ChatPage(
    state: CreateUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onConfirmAction: () -> Unit,
    onDismissAction: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Converse, pesquise e prepare criações. O envio ao motor sempre precisa de sua confirmação.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.chatMessages.size) { index ->
                val message = state.chatMessages[index]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            if (message.role == "user") "Você" else "Assistente",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(message.content)
                    }
                }
            }
            item {
                state.pendingChatAction?.let { action ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Criação preparada — confirme para revisar", fontWeight = FontWeight.SemiBold)
                            Text("Modo: ${action.mode.name.lowercase().replace('_', ' ')}")
                            Text(action.prompt)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onConfirmAction) { Text("Revisar criação") }
                                OutlinedButton(onClick = onDismissAction) { Text("Descartar") }
                            }
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            value = state.chatDraft,
            onValueChange = onDraftChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mensagem para o assistente") },
            placeholder = { Text("Pesquise, peça ideias ou descreva uma criação…") },
            minLines = 2,
            maxLines = 4,
            enabled = !state.chatBusy,
        )
        Button(
            onClick = onSend,
            enabled = !state.chatBusy && state.chatDraft.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.chatBusy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Pensando…")
            } else {
                Text("Enviar")
            }
        }
    }
}

@Composable
private fun GalleryPage(assets: List<MediaAsset>, onOpen: (MediaAsset) -> Unit) {
    if (assets.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Suas criações salvas aparecerão aqui.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(assets, key = MediaAsset::path) { asset -> MediaCard(asset, onClick = { onOpen(asset) }) }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun MediaCard(asset: MediaAsset, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            if (asset.mimeType.startsWith("image/")) {
                LocalImage(asset.path, Modifier.fillMaxWidth().aspectRatio(1.2f))
            } else {
                Box(
                    Modifier.fillMaxWidth().height(150.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("▶  Vídeo — toque para abrir")
                }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(asset.prompt, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                Text(
                    File(asset.path).name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InputImagePreview(path: String) {
    Card(Modifier.fillMaxWidth()) {
        LocalImage(path, Modifier.fillMaxWidth().height(180.dp))
    }
}

@Composable
private fun LocalImage(path: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, path) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1400 || bounds.outHeight / sample > 1400) sample *= 2
            BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )?.asImageBitmap()
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = "Imagem salva no dispositivo",
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Text("Prévia indisponível", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun MessageCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    }
}

private fun openAsset(context: android.content.Context, asset: MediaAsset) {
    try {
        val file = File(asset.path)
        check(file.isFile) { "Este arquivo não existe mais." }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, asset.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    } catch (error: Exception) {
        Toast.makeText(
            context,
            error.message ?: "Não foi possível abrir o arquivo.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

private const val MAX_WORKFLOW_BYTES = 2 * 1024 * 1024
