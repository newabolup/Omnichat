package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.model.ApiConfig
import com.example.model.Attachment
import com.example.model.ModelInfo
import com.example.ui.components.AttachmentChip
import com.example.ui.components.DebugPanelDialog
import com.example.ui.components.MarkdownRenderer
import com.example.ui.components.ModelSelectorDropdown
import com.example.ui.components.SearchDialog
import com.example.ui.components.SidebarDrawer
import com.example.ui.viewmodel.ChatUiState
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(
    viewModel: ChatViewModel,
    uiState: ChatUiState,
    config: ApiConfig,
    activeConversations: List<ConversationEntity>,
    archivedConversations: List<ConversationEntity>,
    messages: List<MessageEntity>,
    onOpenSettings: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    var showSearchDialog by remember { mutableStateOf(false) }
    var showDebugDialog by remember { mutableStateOf(false) }

    // File Picker for Multimodal Attachments
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val contentResolver = context.contentResolver
                    var fileName = "file_${System.currentTimeMillis()}"
                    var fileSize = 0L

                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                            if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                        }
                    }

                    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                    val isImage = mimeType.startsWith("image/")

                    val inputStream: InputStream? = contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: byteArrayOf()
                    inputStream?.close()

                    val contentStr = if (isImage) {
                        Base64.encodeToString(bytes, Base64.NO_WRAP)
                    } else {
                        // Check if text readable
                        try {
                            String(bytes, Charsets.UTF_8)
                        } catch (_: Exception) {
                            Base64.encodeToString(bytes, Base64.NO_WRAP)
                        }
                    }

                    val attachment = Attachment(
                        id = UUID.randomUUID().toString(),
                        name = fileName,
                        sizeBytes = if (fileSize > 0) fileSize else bytes.size.toLong(),
                        mimeType = mimeType,
                        content = contentStr,
                        isImage = isImage
                    )
                    viewModel.addAttachment(attachment)
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load attachment: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Auto scroll down when messages change or streaming
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length, uiState.isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val isScrolledUp by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total == 0) false
            else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible < total - 2
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(300.dp)
            ) {
                SidebarDrawer(
                    conversations = activeConversations,
                    archivedConversations = archivedConversations,
                    activeConversationId = uiState.activeConversationId,
                    onSelectConversation = { id ->
                        viewModel.selectConversation(id)
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        viewModel.startNewChat()
                        scope.launch { drawerState.close() }
                    },
                    onSearchClick = {
                        showSearchDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onRenameConversation = { id, title -> viewModel.renameConversation(id, title) },
                    onPinConversation = { id, isPinned -> viewModel.pinConversation(id, isPinned) },
                    onArchiveConversation = { id, isArchived -> viewModel.archiveConversation(id, isArchived) },
                    onDeleteConversation = { id -> viewModel.deleteConversation(id) },
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    onOpenDebugLogs = {
                        scope.launch { drawerState.close() }
                        showDebugDialog = true
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        ModelSelectorDropdown(
                            selectedModel = uiState.selectedModel,
                            models = uiState.models,
                            isLoading = uiState.isLoadingModels,
                            onModelSelected = { viewModel.selectModel(it) },
                            onRefresh = { viewModel.refreshModels() }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.startNewChat() }) {
                            Icon(Icons.Default.Add, contentDescription = "New chat")
                        }
                        IconButton(onClick = { showSearchDialog = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Chat Messages or Empty Starter Screen
                    if (messages.isEmpty()) {
                        EmptyStarterScreen(
                            onSelectPrompt = { prompt ->
                                viewModel.onInputTextChanged(prompt)
                                viewModel.sendMessage()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                if (msg.role == "user") {
                                    UserMessageCard(
                                        message = msg,
                                        showTimestamp = config.showTimestamps,
                                        onEdit = { viewModel.startEditUserMessage(msg) },
                                        onDelete = { viewModel.deleteMessage(msg.id) }
                                    )
                                } else {
                                    AssistantMessageCard(
                                        message = msg,
                                        showTimestamp = config.showTimestamps,
                                        debugMode = config.debugMode,
                                        isGenerating = uiState.isGenerating && msg.status == "streaming",
                                        onRegenerate = { viewModel.regenerateResponse(msg.id) },
                                        onDelete = { viewModel.deleteMessage(msg.id) }
                                    )
                                }
                            }

                            // Extra bottom spacing for composer
                            item { Spacer(modifier = Modifier.height(10.dp)) }
                        }
                    }

                    // Bottom Composer Area
                    MessageComposer(
                        inputText = uiState.inputText,
                        attachments = uiState.attachments,
                        isGenerating = uiState.isGenerating,
                        editingMessage = uiState.editingMessage,
                        enterToSend = config.enterToSend,
                        onTextChanged = { viewModel.onInputTextChanged(it) },
                        onSendMessage = {
                            if (uiState.editingMessage != null) {
                                viewModel.submitEditedMessage()
                            } else {
                                viewModel.sendMessage()
                            }
                        },
                        onStopGeneration = { viewModel.stopGeneration() },
                        onCancelEdit = { viewModel.cancelEditUserMessage() },
                        onPickAttachment = { filePickerLauncher.launch("*/*") },
                        onRemoveAttachment = { viewModel.removeAttachment(it) }
                    )
                }

                // Floating Scroll-to-Bottom FAB
                AnimatedVisibility(
                    visible = isScrolledUp,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 80.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                if (messages.isNotEmpty()) {
                                    listState.animateScrollToItem(messages.size - 1)
                                }
                            }
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll to bottom")
                    }
                }
            }
        }
    }

    if (showSearchDialog) {
        SearchDialog(
            onDismiss = { showSearchDialog = false },
            onSearch = { query -> viewModel.searchConversations(query) },
            onSelectConversation = { id ->
                viewModel.selectConversation(id)
                showSearchDialog = false
            }
        )
    }

    if (showDebugDialog) {
        val debugLogs by viewModel.debugLogs.collectAsStateWithLifecycle(emptyList())
        DebugPanelDialog(
            config = config,
            logs = debugLogs,
            onClearLogs = { viewModel.clearDebugLogs() },
            onDismiss = { showDebugDialog = false }
        )
    }
}

@Composable
fun EmptyStarterScreen(
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val starterPrompts = listOf(
        "⚡ Write a Python script for high-performance data scraping",
        "💡 Explain Quantum Computing and Superposition in plain English",
        "📐 Derive the Quadratic Formula step-by-step with LaTeX",
        "📝 Draft a polite email declining an invitation with alternatives"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "What's on your mind today?",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Ask questions, generate and debug code, attach files, or explore ideas with your configured model.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            starterPrompts.forEach { prompt ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .clickable { onSelectPrompt(prompt.drop(3).trim()) }
                        .padding(14.dp)
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun UserMessageCard(
    message: MessageEntity,
    showTimestamp: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val attachments = remember(message.attachmentsJson) { parseAttachments(message.attachmentsJson) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                attachments.forEach { att ->
                    AttachmentChip(attachment = att)
                }
            }
        }

        if (message.content.isNotBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        lineHeight = 22.sp,
                        fontSize = 14.5.sp
                    )
                )
            }
        }

        // Action Toolbar
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (showTimestamp) {
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.createdAt))
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(message.content))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(22.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit message", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
fun AssistantMessageCard(
    message: MessageEntity,
    showTimestamp: Boolean,
    debugMode: Boolean,
    isGenerating: Boolean,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // Header with Avatar & Model name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message.model?.ifEmpty { "OmniChat" } ?: "OmniChat",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )

            if (isGenerating) {
                Spacer(modifier = Modifier.width(6.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Markdown Body
        if (message.status == "error") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message.errorMessage ?: message.content.ifEmpty { "Generation error" },
                    fontSize = 13.sp,
                    color = Color(0xFFEF4444)
                )
            }
        } else {
            MarkdownRenderer(
                markdown = message.content.ifEmpty { if (isGenerating) "Thinking..." else "" }
            )
        }

        // Debug info row
        if (debugMode && (message.latencyMs != null || message.totalTokens != null)) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                message.latencyMs?.let { ms ->
                    Text(
                        text = "${ms}ms",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                message.totalTokens?.let { tokens ->
                    Text(
                        text = "$tokens tokens",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Action Toolbar
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(message.content))
                    isCopied = true
                    Toast.makeText(context, "Response copied", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        delay(2000)
                        isCopied = false
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy response",
                    tint = if (isCopied) Color(0xFF10A37F) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }

            IconButton(onClick = onRegenerate, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Regenerate response",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }

            if (showTimestamp) {
                Spacer(modifier = Modifier.weight(1f))
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.createdAt))
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
fun MessageComposer(
    inputText: String,
    attachments: List<Attachment>,
    isGenerating: Boolean,
    editingMessage: MessageEntity?,
    enterToSend: Boolean,
    onTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    onCancelEdit: () -> Unit,
    onPickAttachment: () -> Unit,
    onRemoveAttachment: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Editing indicator banner
        if (editingMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Editing message",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                )
                IconButton(onClick = onCancelEdit, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel edit", modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Attachments preview row
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                attachments.forEach { att ->
                    AttachmentChip(
                        attachment = att,
                        onRemove = { onRemoveAttachment(att.id) }
                    )
                }
            }
        }

        // Input pill row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attachment Button
            IconButton(
                onClick = onPickAttachment,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach file",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expanding Text Field
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChanged,
                placeholder = { Text("Message OmniChat...", fontSize = 14.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                maxLines = 5,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )

            // Send or Stop Button
            if (isGenerating) {
                IconButton(
                    onClick = onStopGeneration,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop generating",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                val canSend = inputText.isNotBlank() || attachments.isNotEmpty()
                IconButton(
                    onClick = onSendMessage,
                    enabled = canSend,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Text(
            text = "OmniChat can make mistakes. Verify important info.",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            ),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp, bottom = 2.dp)
        )
    }
}

private fun parseAttachments(jsonStr: String?): List<Attachment> {
    if (jsonStr.isNullOrBlank()) return emptyList()
    val list = mutableListOf<Attachment>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                Attachment(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", "file"),
                    sizeBytes = obj.optLong("sizeBytes", 0L),
                    mimeType = obj.optString("mimeType", "text/plain"),
                    content = obj.optString("content", ""),
                    isImage = obj.optBoolean("isImage", false)
                )
            )
        }
    } catch (_: Exception) {}
    return list
}
