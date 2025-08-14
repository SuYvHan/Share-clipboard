package com.clipboardsync.app.ui.main

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clipboardsync.app.domain.model.ClipboardType
import com.clipboardsync.app.service.clipboard.ClipboardSyncService
import com.clipboardsync.app.ui.components.AdvancedPermissionDialog
import com.clipboardsync.app.ui.components.ClipboardItemCard
import com.clipboardsync.app.ui.components.PermissionDialog
import com.clipboardsync.app.ui.components.PermissionDeniedDialog
import com.clipboardsync.app.ui.settings.SettingsScreen
import com.clipboardsync.app.ui.settings.SmsSettingsScreen
import com.clipboardsync.app.ui.theme.ClipboardSyncTheme
import com.clipboardsync.app.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var sharedPreferences: SharedPreferences

    private var showPermissionDialog by mutableStateOf(false)
    private var showTextUploadDialog by mutableStateOf(false)
    private var showPermissionDeniedDialog by mutableStateOf(false)
    private var showClipboardLimitationDialog by mutableStateOf(false)
    private var showAdvancedPermissionDialog by mutableStateOf(false)
    private var permissionCheckResult by mutableStateOf<PermissionUtils.PermissionCheckResult?>(null)
    private var isPermissionCheckFromSettings by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startClipboardService()
        } else {
            // 显示权限被拒绝对话框
            showPermissionDeniedDialog = true
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.handleImageSelected(it) }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.handleFileSelected(it) }
    }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 电池优化设置返回后，重新检查权限状态
        checkAdvancedPermissions()
    }

    private val autoStartSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 自启动设置返回后，重新检查权限状态
        checkAdvancedPermissions()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

        // 通知服务应用已进入前台
        notifyServiceAppInForeground()
        
        setContent {
            ClipboardSyncTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "main"
                ) {
                    composable("main") {
                        MainScreen(
                            viewModel = viewModel,
                            onCopyToClipboard = { content ->
                                copyToClipboard(content)
                            },
                            onRequestPermissions = {
                                requestPermissions()
                            },
                            onNavigateToSettings = {
                                navController.navigate("settings")
                            },
                            onImageUpload = { imagePickerLauncher.launch("image/*") },
                            onFileUpload = { filePickerLauncher.launch("*/*") },
                            onTextUpload = { showTextUploadDialog = true }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onNavigateToSmsSettings = {
                                navController.navigate("sms_settings")
                            },
                            onShowPermissionCheck = {
                                // 重新显示权限检查对话框
                                val result = PermissionUtils.checkAllPermissions(this@MainActivity)
                                permissionCheckResult = result
                                isPermissionCheckFromSettings = true
                                showAdvancedPermissionDialog = true
                            }
                        )
                    }

                    composable("sms_settings") {
                        SmsSettingsScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }

                // 权限对话框
                if (showPermissionDialog) {
                    PermissionDialog(
                        permissions = PermissionUtils.getPermissionsToRequest(this@MainActivity),
                        onRequestPermissions = {
                            showPermissionDialog = false
                            val permissionsToRequest = PermissionUtils.getPermissionsToRequest(this@MainActivity)
                            if (permissionsToRequest.isNotEmpty()) {
                                permissionLauncher.launch(permissionsToRequest.toTypedArray())
                            } else {
                                // 没有需要申请的权限，直接启动服务
                                startClipboardService()
                            }
                        },
                        onDismiss = {
                            showPermissionDialog = false
                        },
                        onOpenSettings = {
                            showPermissionDialog = false
                            openAppSettings()
                        }
                    )
                }

                // 权限被拒绝对话框
                if (showPermissionDeniedDialog) {
                    PermissionDeniedDialog(
                        onOpenSettings = {
                            showPermissionDeniedDialog = false
                            openAppSettings()
                        },
                        onDismiss = {
                            showPermissionDeniedDialog = false
                        }
                    )
                }

                // 文本上传对话框
                if (showTextUploadDialog) {
                    TextUploadDialog(
                        onDismiss = { showTextUploadDialog = false },
                        onUpload = { text ->
                            viewModel.uploadText(text)
                            showTextUploadDialog = false
                        }
                    )
                }

                // 剪切板限制说明对话框 (Android 12+)
                if (showClipboardLimitationDialog) {
                    ClipboardLimitationDialog(
                        onDismiss = { showClipboardLimitationDialog = false }
                    )
                }

                // 高级权限检查对话框
                if (showAdvancedPermissionDialog && permissionCheckResult != null) {
                    AdvancedPermissionDialog(
                        permissionResult = permissionCheckResult!!,
                        onDismiss = {
                            showAdvancedPermissionDialog = false
                            // 只有在首次启动时才标记为已显示
                            if (!isPermissionCheckFromSettings) {
                                markPermissionCheckShown()
                            }
                            isPermissionCheckFromSettings = false
                        },
                        onRequestBatteryOptimization = {
                            showAdvancedPermissionDialog = false
                            requestBatteryOptimization()
                        },
                        onOpenAutoStartSettings = {
                            showAdvancedPermissionDialog = false
                            openAutoStartSettings()
                        },
                        onOpenBasicPermissions = {
                            showAdvancedPermissionDialog = false
                            requestPermissions()
                        }
                    )
                }
            }
        }
        
        // 检查权限并启动服务
        checkPermissionsAndStartService()

        // 检查高级权限（电池优化、自启动）- 只在首次启动时显示
        checkAdvancedPermissionsIfFirstTime()

        // 如果是Android 12+，显示剪切板限制说明 - 只在首次启动时显示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isFirstTimeShowingClipboardLimitation()) {
            showClipboardLimitationDialog = true
            markClipboardLimitationShown()
        }
    }

    override fun onResume() {
        super.onResume()
        // 应用回到前台时通知服务检查剪切板
        notifyServiceAppInForeground()
        Log.d("MainActivity", "应用回到前台，通知服务检查剪切板")
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "应用进入后台")
    }

    /**
     * 通知服务应用在前台状态
     */
    private fun notifyServiceAppInForeground() {
        try {
            val intent = Intent(this, ClipboardSyncService::class.java).apply {
                action = ClipboardSyncService.ACTION_APP_IN_FOREGROUND
            }
            startService(intent)
        } catch (e: Exception) {
            Log.w("MainActivity", "通知服务前台状态失败: ${e.message}")
        }
    }
    
    private fun checkPermissionsAndStartService() {
        if (PermissionUtils.hasAllPermissions(this)) {
            startClipboardService()
        } else {
            showPermissionDialog = true
        }
    }
    
    private fun requestPermissions() {
        checkPermissionsAndStartService()
    }
    
    private fun startClipboardService() {
        ClipboardSyncService.startService(this)
    }
    
    private fun copyToClipboard(content: String) {
        val clip = ClipData.newPlainText("clipboard_sync", content)
        clipboardManager.setPrimaryClip(clip)
        viewModel.copyToClipboard(content)
    }
    
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    /**
     * 检查高级权限（电池优化、自启动）- 只在首次启动时显示
     */
    private fun checkAdvancedPermissionsIfFirstTime() {
        if (isFirstTimeShowingPermissionCheck()) {
            val result = PermissionUtils.checkAllPermissions(this)
            permissionCheckResult = result

            // 如果有需要用户注意的权限问题，显示对话框
            if (result.recommendations.isNotEmpty()) {
                isPermissionCheckFromSettings = false
                showAdvancedPermissionDialog = true
                markPermissionCheckShown()
            } else {
                // 即使没有权限问题，也标记为已显示，避免后续再次检查
                markPermissionCheckShown()
            }

            Log.d("MainActivity", "首次启动权限检查结果: $result")
        } else {
            Log.d("MainActivity", "非首次启动，跳过权限检查对话框")
        }
    }

    /**
     * 检查高级权限（不显示对话框，仅用于手动检查）
     */
    private fun checkAdvancedPermissions() {
        val result = PermissionUtils.checkAllPermissions(this)
        permissionCheckResult = result
        Log.d("MainActivity", "权限检查结果: $result")
    }

    /**
     * 请求忽略电池优化
     */
    private fun requestBatteryOptimization() {
        val intent = PermissionUtils.requestIgnoreBatteryOptimization(this)
        if (intent != null) {
            try {
                batteryOptimizationLauncher.launch(intent)
            } catch (e: Exception) {
                Log.w("MainActivity", "无法打开电池优化设置: ${e.message}")
            }
        }
    }

    /**
     * 打开自启动设置
     */
    private fun openAutoStartSettings() {
        val intent = PermissionUtils.getAutoStartSettingsIntent(this)
        if (intent != null) {
            try {
                autoStartSettingsLauncher.launch(intent)
            } catch (e: Exception) {
                Log.w("MainActivity", "无法打开自启动设置: ${e.message}")
            }
        }
    }

    /**
     * 检查是否是首次显示权限检查对话框
     */
    private fun isFirstTimeShowingPermissionCheck(): Boolean {
        return !sharedPreferences.getBoolean("permission_check_shown", false)
    }

    /**
     * 标记权限检查对话框已显示
     */
    private fun markPermissionCheckShown() {
        sharedPreferences.edit()
            .putBoolean("permission_check_shown", true)
            .apply()
        Log.d("MainActivity", "已标记权限检查对话框为已显示")
    }

    /**
     * 检查是否是首次显示剪切板限制说明
     */
    private fun isFirstTimeShowingClipboardLimitation(): Boolean {
        return !sharedPreferences.getBoolean("clipboard_limitation_shown", false)
    }

    /**
     * 标记剪切板限制说明已显示
     */
    private fun markClipboardLimitationShown() {
        sharedPreferences.edit()
            .putBoolean("clipboard_limitation_shown", true)
            .apply()
        Log.d("MainActivity", "已标记剪切板限制说明为已显示")
    }

    /**
     * 重置首次启动标记（用于测试或重新显示对话框）
     */
    private fun resetFirstTimeFlags() {
        sharedPreferences.edit()
            .putBoolean("permission_check_shown", false)
            .putBoolean("clipboard_limitation_shown", false)
            .apply()
        Log.d("MainActivity", "已重置首次启动标记")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onCopyToClipboard: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onRequestPermissions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onImageUpload: () -> Unit,
    onFileUpload: () -> Unit,
    onTextUpload: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredItems by viewModel.getFilteredItems().collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    var showFilterMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 显示消息
    LaunchedEffect(uiState.message, uiState.error, uiState.lastSyncMessage) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearMessage()
        }
        uiState.lastSyncMessage?.let { syncMessage ->
            snackbarHostState.showSnackbar(syncMessage)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("剪切板同步") },
                actions = {
                    // 连接状态指示器
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when (connectionState) {
                            is com.clipboardsync.app.network.websocket.WebSocketClient.ConnectionState.Connected -> {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = "已连接",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "已连接",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            is com.clipboardsync.app.network.websocket.WebSocketClient.ConnectionState.Disconnected -> {
                                Icon(
                                    Icons.Default.CloudOff,
                                    contentDescription = "未连接",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "未连接",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            is com.clipboardsync.app.network.websocket.WebSocketClient.ConnectionState.Reconnecting -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "重连中",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            else -> {
                                Icon(
                                    Icons.Default.Cloud,
                                    contentDescription = "连接中",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "连接中",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 过滤菜单
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "过滤")
                        }
                        
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("全部") },
                                onClick = {
                                    viewModel.filterItems(null)
                                    showFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("文本") },
                                onClick = {
                                    viewModel.filterItems(ClipboardType.text)
                                    showFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("图片") },
                                onClick = {
                                    viewModel.filterItems(ClipboardType.image)
                                    showFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("文件") },
                                onClick = {
                                    viewModel.filterItems(ClipboardType.file)
                                    showFilterMenu = false
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("清除所有本地记录") },
                                onClick = {
                                    viewModel.clearAllLocalClipboard()
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                }
                            )
                        }
                    }
                    
                    // 同步所有按钮（直接触发同步所有云端剪切板）
                    IconButton(onClick = { viewModel.syncAllClipboardFromServer() }) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "同步所有云端剪切板")
                    }
                    
                    // 设置按钮
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            UploadFab(
                onImageUpload = onImageUpload,
                onFileUpload = onFileUpload,
                onTextUpload = onTextUpload
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 同步状态提示
            uiState.lastSyncMessage?.let { message ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { viewModel.syncAllClipboardFromServer() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "点击同步",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // 搜索栏
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.searchItems(it) },
                label = { Text("搜索剪切板内容") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            
            // 加载指示器
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            // 剪切板项目列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = filteredItems,
                    key = { item -> item.id }  // 添加key提升滚动性能
                ) { item ->
                    val context = LocalContext.current
                    ClipboardItemCard(
                        item = item,
                        onCopy = onCopyToClipboard,
                        onDelete = { viewModel.deleteItem(it) },
                        onSaveImage = { viewModel.saveImageToGallery(context, it) }
                    )
                }
                
                if (filteredItems.isEmpty() && !uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.searchQuery.isNotBlank()) {
                                    "没有找到匹配的内容"
                                } else {
                                    "暂无剪切板内容"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadFab(
    onImageUpload: () -> Unit,
    onFileUpload: () -> Unit,
    onTextUpload: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 文本上传按钮
        if (expanded) {
            FloatingActionButton(
                onClick = {
                    onTextUpload()
                    expanded = false
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Edit, contentDescription = "上传文本")
            }
        }

        // 图片上传按钮
        if (expanded) {
            FloatingActionButton(
                onClick = {
                    onImageUpload()
                    expanded = false
                },
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(Icons.Default.Image, contentDescription = "上传图片")
            }
        }

        // 文件上传按钮
        if (expanded) {
            FloatingActionButton(
                onClick = {
                    onFileUpload()
                    expanded = false
                },
                containerColor = MaterialTheme.colorScheme.tertiary
            ) {
                Icon(Icons.Default.Description, contentDescription = "上传文件")
            }
        }

        // 主按钮
        FloatingActionButton(
            onClick = { expanded = !expanded }
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = if (expanded) "关闭" else "上传"
            )
        }
    }
}

@Composable
fun TextUploadDialog(
    onDismiss: () -> Unit,
    onUpload: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "上传文本",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "输入要上传到剪切板服务器的文本内容：",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("文本内容") },
                    placeholder = { Text("请输入文本...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 200.dp),
                    maxLines = 8,
                    singleLine = false
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "字符数: ${text.length}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onUpload(text.trim())
                    }
                },
                enabled = text.isNotBlank()
            ) {
                Text("上传")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun ClipboardLimitationDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "剪切板同步说明",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Android 12+ 系统限制",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "为了保护用户隐私，Android 12及以上版本限制了后台应用访问剪切板。",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "📋 剪切板同步工作原理：",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                Text(
                    text = "• 当应用在前台时：可以正常监听和同步剪切板\n" +
                          "• 当应用在后台时：无法访问剪切板内容\n" +
                          "• 重新打开应用时：会自动检查并上传剪切板最新内容\n" +
                          "• 如果后台复制失败，前台会自动重新上传",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "💡 使用建议：",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )

                Text(
                    text = "• 复制内容后，短暂打开应用确保同步\n" +
                          "• 使用应用内的上传功能手动同步内容\n" +
                          "• 服务会在后台保持运行，等待应用回到前台",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我知道了")
            }
        }
    )
}
