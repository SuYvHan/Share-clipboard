package com.clipboardsync.app.ui.main

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import com.clipboardsync.app.ui.components.ClipboardItemCard
import com.clipboardsync.app.ui.components.PermissionDialog
import com.clipboardsync.app.ui.components.PermissionDeniedDialog
import com.clipboardsync.app.ui.settings.SettingsScreen
import com.clipboardsync.app.ui.theme.ClipboardSyncTheme
import com.clipboardsync.app.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var clipboardManager: ClipboardManager

    private var showPermissionDialog by mutableStateOf(false)
    private var showPermissionDeniedDialog by mutableStateOf(false)

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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
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
                            onFileUpload = { filePickerLauncher.launch("*/*") }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }

                // 权限对话框
                if (showPermissionDialog) {
                    PermissionDialog(
                        permissions = PermissionUtils.getDeniedPermissions(this@MainActivity),
                        onRequestPermissions = {
                            showPermissionDialog = false
                            val deniedPermissions = PermissionUtils.getDeniedPermissions(this@MainActivity)
                            permissionLauncher.launch(deniedPermissions.toTypedArray())
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
            }
        }
        
        // 检查权限并启动服务
        checkPermissionsAndStartService()
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onCopyToClipboard: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onRequestPermissions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onImageUpload: () -> Unit,
    onFileUpload: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredItems by viewModel.getFilteredItems().collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    
    var showFilterMenu by remember { mutableStateOf(false) }
    
    // 显示消息
    LaunchedEffect(uiState.message, uiState.error) {
        // 这里可以显示 SnackBar 或 Toast
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("剪切板同步") },
                actions = {
                    // 连接状态指示器
                    when (connectionState) {
                        is com.clipboardsync.app.network.websocket.WebSocketClient.ConnectionState.Connected -> {
                            Icon(
                                Icons.Default.CloudDone,
                                contentDescription = "已连接",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        is com.clipboardsync.app.network.websocket.WebSocketClient.ConnectionState.Disconnected -> {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = "未连接",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        is com.clipboardsync.app.network.websocket.WebSocketClient.ConnectionState.Reconnecting -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        else -> {
                            Icon(
                                Icons.Default.Cloud,
                                contentDescription = "连接中",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        }
                    }
                    
                    // 同步按钮
                    IconButton(onClick = { viewModel.syncWithServer() }) {
                        Icon(Icons.Default.Sync, contentDescription = "同步")
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
                onFileUpload = onFileUpload
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
                items(filteredItems) { item ->
                    ClipboardItemCard(
                        item = item,
                        onCopy = onCopyToClipboard,
                        onDelete = { viewModel.deleteItem(it) },
                        onSaveImage = { viewModel.saveImageToGallery(it) }
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
    onFileUpload: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
