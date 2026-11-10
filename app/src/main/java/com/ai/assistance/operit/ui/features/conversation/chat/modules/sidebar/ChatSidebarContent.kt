package com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.character.ActivePrompt
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.conversation.chat.modules.characters.CharacterSelectorContent
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.CharacterSelectorTarget
import com.ai.assistance.operit.ui.components.menu.WorkbenchDropdownMenu
import com.ai.assistance.operit.ui.features.conversation.chat.modules.history.folders.FolderChatHistoryPanel
import com.ai.assistance.operit.ui.main.components.NavigationDrawerAppearance
import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec
import com.ai.assistance.operit.ui.main.screens.GestureStateHolder
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import com.ai.assistance.operit.ui.theme.script.themeScriptContentColor
import kotlinx.coroutines.flow.flowOf

/** 角色和历史优先，插件使用可折叠的大按钮区域，底部保持固定。 */
@Composable
fun ChatSidebarContent(
    modifier: Modifier = Modifier,
    brandName: String,
    appearance: NavigationDrawerAppearance,
    pluginEntries: List<NavigationEntrySpec>,
    selectedRouteId: String,
    onNavigationEntryClick: (NavigationEntrySpec) -> Unit,
    onOpenCharacterSettings: () -> Unit,
    onCloseSidebar: () -> Unit,
    onRunChatAction: (() -> Unit) -> Unit,
    onCreateChat: (() -> Unit) -> Unit,
    onSelectCharacter: (CharacterSelectorTarget) -> Unit,
    bottomContent: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = LocalMainChatViewModel.current
    val state = LocalMainChatSidebarState.current
    val currentChatId by viewModel.currentChatId.collectAsState()
    val deletionProgress by viewModel.chatListMotion.deletions.collectAsState()
    val batchProgress by viewModel.chatBatchProgress.collectAsState()
    val streamingIds by viewModel.activeStreamingChatIds.collectAsState()
    val searchQuery by viewModel.chatHistorySearchQuery.collectAsState()
    val userPreferences = remember(context) { UserPreferencesManager.getInstance(context) }
    val cardId = state.activeCharacterCard?.id
    val groupId = state.activeCharacterGroup?.id
    val cardAvatar by remember(cardId) {
        if (cardId == null) flowOf(null) else userPreferences.getAiAvatarForCharacterCardFlow(cardId)
    }.collectAsState(initial = null)
    val groupAvatar by remember(groupId) {
        if (groupId == null) flowOf(null) else userPreferences.getAiAvatarForCharacterGroupFlow(groupId)
    }.collectAsState(initial = null)
    val memberCardId = state.activeCharacterGroup?.members?.minByOrNull { it.orderIndex }?.characterCardId
    val memberAvatar by remember(memberCardId) {
        if (memberCardId == null) flowOf(null) else userPreferences.getAiAvatarForCharacterCardFlow(memberCardId)
    }.collectAsState(initial = null)
    val avatar = when (state.activePrompt) {
        is ActivePrompt.CharacterCard -> cardAvatar
        is ActivePrompt.CharacterGroup -> groupAvatar ?: memberAvatar
    }
    val characterName = when (state.activePrompt) {
        is ActivePrompt.CharacterCard -> state.activeCharacterCard?.name.orEmpty()
        is ActivePrompt.CharacterGroup -> state.activeCharacterGroup?.name.orEmpty()
    }
    val textColor = themeScriptContentColor("navigation", getTextColorForBackground(appearance.containerColor))
    var showCharacterSelector by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { GestureStateHolder.isChatScreenGestureConsumed = false }
    }
    ChatSidebarBody(
        modifier = modifier,
        brandName = brandName,
        appearance = appearance,
        pluginEntries = pluginEntries,
        selectedRouteId = selectedRouteId,
        characterName = characterName,
        avatar = avatar,
        onNavigationEntryClick = onNavigationEntryClick,
        onOpenCharacterSettings = onOpenCharacterSettings,
        onCloseSidebar = onCloseSidebar,
        bottomContent = bottomContent,
        onOpenCharacterSelector = { showCharacterSelector = true },
        characterSelectorExpanded = showCharacterSelector,
        onDismissCharacterSelector = { showCharacterSelector = false },
        characterSelectorContent = {
            CharacterSelectorContent(
                onDismiss = { showCharacterSelector = false },
                onSelectCharacter = onSelectCharacter,
                onOpenCharacterSettings = {
                    showCharacterSelector = false
                    onOpenCharacterSettings()
                },
            )
        },
        historyContent = {
            FolderChatHistoryPanel(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onNewChat = { cardName, group, folder -> onCreateChat {
                    viewModel.showChatPage()
                    viewModel.createNewChat(cardName, group, folder, inheritGroupFromCurrent = false)
                } },
                onSelectChat = { id -> onRunChatAction { viewModel.showChatPage(); viewModel.switchChat(id) } },
                onDeleteChat = viewModel::deleteChatHistory,
                onUpdateChatTitle = viewModel::updateChatTitle,
                onSummarizeChatTitle = viewModel::summarizeChatTitle,
                onUpdateChatBinding = viewModel::updateChatCharacterBinding,
                chatHistories = state.displayedChatHistories,
                currentId = currentChatId,
                activeStreamingChatIds = streamingIds,
                lazyListState = null,
                onBack = null,
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::onChatHistorySearchQueryChange,
                historyDisplayMode = state.historyDisplayMode,
                onDisplayModeChange = state.onDisplayModeChange,
                autoSwitchCharacterCard = state.autoSwitchCharacterCard,
                onAutoSwitchCharacterCardChange = state.onAutoSwitchCharacterCardChange,
                autoSwitchChatOnCharacterSelect = state.autoSwitchChatOnCharacterSelect,
                onAutoSwitchChatOnCharacterSelectChange = state.onAutoSwitchChatOnCharacterSelectChange,
                onQuickScrollInteractionChange = { GestureStateHolder.isChatScreenGestureConsumed = it },
                activePrompt = state.activePrompt,
                embeddedInSidebar = true,
                deletionProgress = deletionProgress,
                batchProgress = batchProgress,
                onRunBatch = viewModel::runChatBatch,
                contentColor = textColor,
                selectedContainerColor = appearance.selectedContainerColor,
                selectedContentColor = getTextColorForBackground(appearance.selectedContainerColor),
            )
        },
    )
}

/** 侧栏展示共用标题、快捷入口、角色行和插件布局，历史区域由调用方提供。 */
@Composable
internal fun ChatSidebarBody(
    modifier: Modifier = Modifier,
    brandName: String,
    appearance: NavigationDrawerAppearance,
    pluginEntries: List<NavigationEntrySpec>,
    selectedRouteId: String,
    characterName: String,
    avatar: String?,
    onNavigationEntryClick: (NavigationEntrySpec) -> Unit,
    onOpenCharacterSettings: () -> Unit,
    onOpenCharacterSelector: () -> Unit,
    onCloseSidebar: () -> Unit,
    bottomContent: @Composable () -> Unit,
    characterSelectorExpanded: Boolean = false,
    onDismissCharacterSelector: () -> Unit = {},
    characterSelectorContent: @Composable () -> Unit = {},
    historyContent: @Composable ColumnScope.() -> Unit,
) {
    val textColor = themeScriptContentColor("navigation", getTextColorForBackground(appearance.containerColor))
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(brandName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = appearance.titleColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onCloseSidebar, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_content_collapse_sidebar),
                    tint = appearance.itemColor, modifier = Modifier.size(18.dp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onOpenCharacterSelector).padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (avatar.isNullOrBlank()) {
                        Surface(Modifier.size(28.dp), shape = CircleShape, color = appearance.buttonContainerColor) {
                            Icon(Icons.Default.Person, null, tint = textColor, modifier = Modifier.padding(5.dp))
                        }
                    } else {
                        Image(rememberAsyncImagePainter(avatar), contentDescription = null,
                            modifier = Modifier.size(28.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    }
                    Text(characterName, Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                        color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = appearance.itemColor, modifier = Modifier.size(16.dp))
                }
                WorkbenchDropdownMenu(
                    expanded = characterSelectorExpanded,
                    onDismissRequest = onDismissCharacterSelector,
                    minWidth = 200.dp,
                    maxWidth = 240.dp,
                    contentPadding = 4.dp,
                ) { characterSelectorContent() }
            }
            IconButton(onClick = onOpenCharacterSettings, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Settings, stringResource(R.string.sidebar_character_settings),
                    tint = appearance.itemColor, modifier = Modifier.size(18.dp))
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pluginListMaxHeight = maxHeight * 0.3f
            Column(Modifier.fillMaxSize()) {
                historyContent()
                SidebarPluginSection(
                    entries = pluginEntries,
                    selectedRouteId = selectedRouteId,
                    appearance = appearance,
                    maxListHeight = pluginListMaxHeight,
                    onEntryClick = onNavigationEntryClick,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        bottomContent()
    }
}
