package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ConversationEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SidebarDrawer(
    conversations: List<ConversationEntity>,
    archivedConversations: List<ConversationEntity>,
    activeConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onSearchClick: () -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onPinConversation: (String, Boolean) -> Unit,
    onArchiveConversation: (String, Boolean) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebugLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showArchived by remember { mutableStateOf(false) }
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    val displayedList = if (showArchived) archivedConversations else conversations

    // Grouping by date
    val now = Calendar.getInstance()
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - 86400000L
    val sevenDaysAgo = todayStart - (6 * 86400000L)

    val pinnedGroup = displayedList.filter { it.isPinned }
    val unpinned = displayedList.filter { !it.isPinned }

    val todayGroup = unpinned.filter { it.updatedAt >= todayStart }
    val yesterdayGroup = unpinned.filter { it.updatedAt in yesterdayStart until todayStart }
    val prev7DaysGroup = unpinned.filter { it.updatedAt in sevenDaysAgo until yesterdayStart }
    val olderGroup = unpinned.filter { it.updatedAt < sevenDaysAgo }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        // App Brand & New Chat Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "OmniChat",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp)
                )
            }

            IconButton(onClick = onSearchClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search chats",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // "+ New Chat" button
        OutlinedButton(
            onClick = onNewChat,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "New chat", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // History Groups
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (pinnedGroup.isNotEmpty()) {
                item { GroupHeader("Pinned") }
                items(pinnedGroup, key = { "pin_${it.id}" }) { conv ->
                    ConversationRow(
                        conversation = conv,
                        isSelected = conv.id == activeConversationId,
                        onSelect = { onSelectConversation(conv.id) },
                        onRename = { renameTargetId = conv.id; renameText = conv.title },
                        onTogglePin = { onPinConversation(conv.id, !conv.isPinned) },
                        onToggleArchive = { onArchiveConversation(conv.id, !conv.isArchived) },
                        onDelete = { deleteTargetId = conv.id }
                    )
                }
            }

            if (todayGroup.isNotEmpty()) {
                item { GroupHeader("Today") }
                items(todayGroup, key = { it.id }) { conv ->
                    ConversationRow(
                        conversation = conv,
                        isSelected = conv.id == activeConversationId,
                        onSelect = { onSelectConversation(conv.id) },
                        onRename = { renameTargetId = conv.id; renameText = conv.title },
                        onTogglePin = { onPinConversation(conv.id, !conv.isPinned) },
                        onToggleArchive = { onArchiveConversation(conv.id, !conv.isArchived) },
                        onDelete = { deleteTargetId = conv.id }
                    )
                }
            }

            if (yesterdayGroup.isNotEmpty()) {
                item { GroupHeader("Yesterday") }
                items(yesterdayGroup, key = { it.id }) { conv ->
                    ConversationRow(
                        conversation = conv,
                        isSelected = conv.id == activeConversationId,
                        onSelect = { onSelectConversation(conv.id) },
                        onRename = { renameTargetId = conv.id; renameText = conv.title },
                        onTogglePin = { onPinConversation(conv.id, !conv.isPinned) },
                        onToggleArchive = { onArchiveConversation(conv.id, !conv.isArchived) },
                        onDelete = { deleteTargetId = conv.id }
                    )
                }
            }

            if (prev7DaysGroup.isNotEmpty()) {
                item { GroupHeader("Previous 7 Days") }
                items(prev7DaysGroup, key = { it.id }) { conv ->
                    ConversationRow(
                        conversation = conv,
                        isSelected = conv.id == activeConversationId,
                        onSelect = { onSelectConversation(conv.id) },
                        onRename = { renameTargetId = conv.id; renameText = conv.title },
                        onTogglePin = { onPinConversation(conv.id, !conv.isPinned) },
                        onToggleArchive = { onArchiveConversation(conv.id, !conv.isArchived) },
                        onDelete = { deleteTargetId = conv.id }
                    )
                }
            }

            if (olderGroup.isNotEmpty()) {
                item { GroupHeader("Older") }
                items(olderGroup, key = { it.id }) { conv ->
                    ConversationRow(
                        conversation = conv,
                        isSelected = conv.id == activeConversationId,
                        onSelect = { onSelectConversation(conv.id) },
                        onRename = { renameTargetId = conv.id; renameText = conv.title },
                        onTogglePin = { onPinConversation(conv.id, !conv.isPinned) },
                        onToggleArchive = { onArchiveConversation(conv.id, !conv.isArchived) },
                        onDelete = { deleteTargetId = conv.id }
                    )
                }
            }

            if (displayedList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (showArchived) "No archived chats" else "No conversations yet",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(8.dp))

        // Bottom footer actions: Archive toggle, Settings, Debug
        if (archivedConversations.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showArchived = !showArchived }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (showArchived) Icons.AutoMirrored.Filled.Chat else Icons.Default.Archive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (showArchived) "Active Chats" else "Archived Chats (${archivedConversations.size})",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Settings", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenDebugLogs)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "API Debug Logs", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
        }
    }

    // Rename Dialog
    if (renameTargetId != null) {
        AlertDialog(
            onDismissRequest = { renameTargetId = null },
            title = { Text("Rename Chat") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = renameTargetId ?: return@Button
                        if (renameText.isNotBlank()) {
                            onRenameConversation(id, renameText.trim())
                        }
                        renameTargetId = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetId = null }) { Text("Cancel") }
            }
        )
    }

    // Delete Confirmation Dialog
    if (deleteTargetId != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("Delete Chat") },
            text = { Text("Are you sure you want to delete this conversation? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTargetId?.let { onDeleteConversation(it) }
                        deleteTargetId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        ),
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
fun ConversationRow(
    conversation: ConversationEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent
            )
            .clickable(onClick = onSelect)
            .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (conversation.isPinned) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = "Pinned",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Text(
            text = conversation.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.5.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = { menuExpanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text(if (conversation.isPinned) "Unpin" else "Pin") },
                    leadingIcon = { Icon(if (conversation.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = { menuExpanded = false; onTogglePin() }
                )
                DropdownMenuItem(
                    text = { Text(if (conversation.isArchived) "Unarchive" else "Archive") },
                    leadingIcon = { Icon(if (conversation.isArchived) Icons.Default.Unarchive else Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = { menuExpanded = false; onToggleArchive() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}
