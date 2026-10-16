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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.luandroid.app.data.CreateMode
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
    val chatMediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.importChatMedia(it) }
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
                Text(
                    if (state.page == MainPage.CHAT) "LUANDROID AI" else "LU CREATE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.page == MainPage.CHAT) "O que vamos criar hoje?" else "Criação visual no seu ritmo",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
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
                        onPickAttachment = { chatMediaPicker.launch("*/*") },
                        onAttachAsset = viewModel::attachGalleryAsset,
                        onRemoveAttachment = viewModel::removeChatAttachment,
                        onConfirmAction = viewModel::confirmChatAction,
                        onDismissAction = viewModel::dismissChatAction,
                    )
                    MainPage.GALLERY -> GalleryPage(state.assets, onOpen = { openAsset(context, it) })
                    MainPage.ENGINE -> EnginePage(
                        state = state,
                        onSelectModel = viewModel::selectModel,
                        onComfyUrlChange = viewModel::setComfyUrl,
                        onOllamaUrlChange = viewModel::setOllamaUrl,
                        onImportWorkflow = { workflowPicker.launch("application/json") },
                        onWorkflowDraftChange = viewModel::setWorkflowDraft,
                        onSaveWorkflow = viewModel::saveWorkflow,
                        onClearWorkflow = viewModel::clearWorkflow,
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
            ModeChip("Clonar / editar", state.mode == CreateMode.EDIT) { onModeChange(CreateMode.EDIT) }
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
        if (state.mode == CreateMode.EDIT) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Clonagem e edição por referência", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Adicione até duas imagens e descreva o que deve permanecer ou mudar. " +
                            "O workflow ComfyUI precisa ter nós de entrada de imagem e usar os placeholders correspondentes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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

        Card(Modifier.fillMaxWidth().clickable(onClick = onOpenEngine)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Imagem: ComfyUI · Chat: Ollama ${state.config.chatModel}", fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        state.config.baseUrl.isBlank() -> "Configure um endpoint para começar."
                        state.config.workflowJson.isBlank() ->
                            "Falta importar um workflow ComfyUI."
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
    onSelectModel: (String) -> Unit,
    onComfyUrlChange: (String) -> Unit,
    onOllamaUrlChange: (String) -> Unit,
    onImportWorkflow: () -> Unit,
    onWorkflowDraftChange: (String) -> Unit,
    onSaveWorkflow: () -> Unit,
    onClearWorkflow: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Escolha o modelo do chat e o workflow ComfyUI. A seleção fica salva automaticamente.")
        Text("ENDEREÇOS DOS MOTORES", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = state.config.baseUrl,
            onValueChange = onComfyUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Servidor ComfyUI") },
            placeholder = { Text("http://192.168.1.20:8188") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.config.chatBaseUrl,
            onValueChange = onOllamaUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Servidor Ollama") },
            placeholder = { Text("http://192.168.1.20:11434") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            enabled = !state.chatBusy,
        )
        Text(
            "Os endereços padrão funcionam no emulador Android. Em um celular físico, informe o IP " +
                "da máquina que executa ComfyUI e Ollama.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("MODELO DO CHAT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Os modelos precisam estar instalados no Ollama conectado ao app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.config.chatModel == "qwen3:8b",
                    onClick = { onSelectModel("qwen3:8b") },
                    label = { Text("Qwen 3 · texto") },
                )
                FilterChip(
                    selected = state.config.chatModel == "qwen3-vl:8b",
                    onClick = { onSelectModel("qwen3-vl:8b") },
                    label = { Text("Qwen3-VL · visão") },
                )
            }
            FilterChip(
                selected = state.config.chatModel == "gemma2:2b",
                onClick = { onSelectModel("gemma2:2b") },
                label = { Text("Gemma 2 · 2B · texto") },
            )
        }
        Text(
            "Para conversar sobre imagens e quadros de vídeos anexados, instale e selecione qwen3-vl:8b. " +
                "Qwen 3 e Gemma 2 são modelos de texto.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("WORKFLOW DE CRIAÇÃO · COMFYUI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Importe um workflow exportado pelo ComfyUI em formato API ou edite o JSON diretamente. " +
                "O conteúdo fica salvo no dispositivo e pode ser trocado quando quiser.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onImportWorkflow, enabled = !state.busy) {
            Text(if (state.config.workflowJson.isBlank()) "Importar workflow JSON" else "Importar outro JSON")
        }
        OutlinedTextField(
            value = state.workflowDraft,
            onValueChange = onWorkflowDraftChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Workflow ComfyUI · JSON API") },
            placeholder = { Text("Cole ou edite o JSON do workflow aqui…") },
            minLines = 8,
            maxLines = 14,
            enabled = !state.busy,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSaveWorkflow,
                enabled = !state.busy && state.workflowDraft.isNotBlank(),
            ) { Text("Validar e salvar JSON") }
            if (state.config.workflowJson.isNotBlank()) {
                TextButton(onClick = onClearWorkflow, enabled = !state.busy) { Text("Remover workflow") }
            }
        }
        Text(
            "Placeholders disponíveis nos nós: {{PROMPT}}, {{IMAGE_FILENAME}}, {{IMAGE_SUBFOLDER}}, " +
                "{{IMAGE_TYPE}}, {{IMAGE2_FILENAME}}, {{IMAGE2_SUBFOLDER}} e {{IMAGE2_TYPE}}. " +
                "O workflow deve conter nós de entrada e saída compatíveis com a operação escolhida.",
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
    onPickAttachment: () -> Unit,
    onAttachAsset: (MediaAsset) -> Unit,
    onRemoveAttachment: () -> Unit,
    onConfirmAction: () -> Unit,
    onDismissAction: () -> Unit,
) {
    val showGalleryPicker = remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Chat Ollama ativo com ${state.config.chatModel}. Com Qwen3-VL, você pode anexar imagens " +
                "e vídeos para análise; use Criar para editar uma imagem ou gerar uma animação.",
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
                        message.attachments.forEach { attachment ->
                            Text(
                                if (attachment.mimeType.startsWith("video/")) "Vídeo: ${File(attachment.path).name}"
                                else "Imagem: ${File(attachment.path).name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
        state.chatAttachment?.let { attachment ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (attachment.mimeType.startsWith("video/")) "Vídeo: ${File(attachment.path).name}"
                        else "Imagem: ${File(attachment.path).name}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onRemoveAttachment, enabled = !state.chatBusy) { Text("Remover") }
                }
                attachment.analysisImagePaths.firstOrNull()?.let { path -> InputImagePreview(path) }
            }
        }
        OutlinedButton(
            onClick = onPickAttachment,
            enabled = !state.chatBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("＋  Anexar imagem ou vídeo")
        }
        OutlinedButton(
            onClick = { showGalleryPicker.value = true },
            enabled = !state.chatBusy && state.assets.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Anexar uma criação da galeria")
        }
        Button(
            onClick = onSend,
            enabled = !state.chatBusy && (state.chatDraft.isNotBlank() || state.chatAttachment != null),
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
        if (showGalleryPicker.value) {
            AlertDialog(
                onDismissRequest = { showGalleryPicker.value = false },
                title = { Text("Escolha uma criação") },
                text = {
                    if (state.assets.isEmpty()) {
                        Text("Ainda não há arquivos na galeria.")
                    } else {
                        Column(
                            Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            state.assets.asReversed().take(20).forEach { asset ->
                                TextButton(
                                    onClick = {
                                        onAttachAsset(asset)
                                        showGalleryPicker.value = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        (if (asset.mimeType.startsWith("video/")) "Vídeo · " else "Imagem · ") +
                                            File(asset.path).name,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGalleryPicker.value = false }) { Text("Fechar") }
                },
            )
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
