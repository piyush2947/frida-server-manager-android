package com.prapps.fridaserverinstaller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prapps.fridaserverinstaller.ui.theme.FridaServerInstallerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FridaServerInstallerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FridaInstallerScreen(
                        modifier = Modifier.padding(innerPadding),
                        context = this@MainActivity
                    )
                }
            }
        }
    }
}

@Composable
fun FridaInstallerScreen(
    modifier: Modifier = Modifier,
    context: ComponentActivity,
    viewModel: FridaInstallerViewModel = viewModel { FridaInstallerViewModel(context) }
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            val path = getRealPathFromUri(context, it)
            if (path != null) {
                viewModel.installFromManualFile(path)
            }
        }
    }
    
    if (uiState.showRedownloadDialog) {
        RedownloadDialog(
            onConfirm = { viewModel.forceRedownload() },
            onDismiss = { viewModel.dismissRedownloadDialog() },
            onSelectFile = { 
                viewModel.dismissRedownloadDialog()
                filePickerLauncher.launch("*/*")
            },
            serverInfo = uiState.serverInfo
        )
    }
    
    if (uiState.showInstallTypeDialog) {
        InstallTypeDialog(
            onDownload = { viewModel.downloadAndInstall() },
            onSelectFile = { 
                viewModel.dismissInstallTypeDialog()
                filePickerLauncher.launch("*/*")
            },
            onDismiss = { viewModel.dismissInstallTypeDialog() }
        )
    }
    
    if (uiState.showVersionSelectionDialog) {
        VersionSelectionDialog(
            isLoading = uiState.isLoadingReleases,
            releases = uiState.availableReleases,
            onVersionSelected = { release ->
                if (uiState.isServerInstalled) {
                    viewModel.forceRedownloadFromVersion(release)
                } else {
                    viewModel.installFromSelectedVersion(release)
                }
            },
            onDismiss = { viewModel.dismissVersionSelectionDialog() }
        )
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Frida Server Manager",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        Text(
            text = "This app will install and run the Frida server on your rooted Android device",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Current server version display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.isServerInstalled) Color(0xFFE8F5E8) else Color(0xFFFFF3E0)
            ),
            border = BorderStroke(
                1.dp, 
                if (uiState.isServerInstalled) Color(0xFF4CAF50) else Color(0xFFFF9800)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (uiState.isServerInstalled) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (uiState.isServerInstalled) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Current Installation:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = uiState.currentServerType,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (uiState.isServerInstalled) Color(0xFF2E7D32) else Color(0xFFE65100)
                    )
                }
            }
        }
        
        when (uiState.status) {
            InstallStatus.IDLE -> {
                Button(
                    onClick = { viewModel.startInstallation() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Install Frida Server")
                }
            }
            InstallStatus.INSTALLING -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.downloadProgress > 0) {
                        LinearProgressIndicator(
                            progress = uiState.downloadProgress / 100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Download: ${uiState.downloadProgress}% (${formatBytes(uiState.downloadedBytes)}/${formatBytes(uiState.totalBytes)})",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                    
                    Text(
                        text = uiState.currentMessage.ifEmpty { "Installing..." },
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
            InstallStatus.SUCCESS -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Success",
                    tint = Color.Green,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Installation Complete!",
                    color = Color.Green,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.startServer() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Start Server")
                    }
                    Button(
                        onClick = { viewModel.resetInstallation() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Install Again")
                    }
                }
            }
            InstallStatus.SERVER_STARTING -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Starting Server...",
                    fontWeight = FontWeight.Medium
                )
            }
            InstallStatus.SERVER_RUNNING -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Server Running",
                    tint = Color.Green,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Server Running!",
                    color = Color.Green,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.stopServer() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Stop Server")
                    }
                    Button(
                        onClick = { viewModel.resetInstallation() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Install Again")
                    }
                }
            }
            InstallStatus.SERVER_STOPPED -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Server Stopped",
                    tint = Color.Gray,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Server Stopped",
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.startServer() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Start Server")
                    }
                    Button(
                        onClick = { viewModel.resetInstallation() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Install Again")
                    }
                }
            }
            InstallStatus.ERROR -> {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = "Error",
                    tint = Color.Red,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Error",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Button(
                    onClick = { viewModel.resetInstallation() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Try Again")
                }
            }
        }
        
        // Current Status Display
        if (uiState.currentMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            uiState.currentMessage.startsWith("✅") -> Color(0xFF2E7D32) // Medium green
                            uiState.currentMessage.startsWith("❌") -> Color(0xFFD32F2F) // Medium red
                            else -> Color(0xFF1976D2) // Medium blue
                        }
                    )
                ) {
                    Text(
                        text = uiState.currentMessage,
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color.White // Always white text for visibility
                    )
                }
        }

        if (uiState.messages.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text(
                        text = "Progress Log:",
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(
                            min = 100.dp,
                            max = 500.dp
                        ) // Dynamic height that grows with content
                    ) {
                        items(uiState.messages) { message ->
                            Text(
                                text = message,
                                fontSize = 9.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 1.dp, vertical = 0.dp),
                                color = when {
                                    message.startsWith("✅") -> Color.Green
                                    message.startsWith("❌") -> Color.Red
                                    message.startsWith("📤 [STDOUT]") || message.startsWith("🔴 [STDERR]") -> Color.Green
                                    message.startsWith("🔴") -> Color.Red
                                    message.startsWith("🌐") || message.startsWith("📥") || message.startsWith("📦") -> Color.Cyan
                                    message.startsWith("🔐") || message.startsWith("📱") -> Color.Yellow
                                    message.startsWith("🚀") || message.startsWith("🛑") -> Color.Magenta
                                    else -> Color.White
                                },
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstallTypeDialog(
    onDownload: () -> Unit,
    onSelectFile: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Installation Method") },
        text = { 
            Text("How would you like to install the Frida server?\n\n• Download: Automatically download latest version from GitHub\n• Select File: Choose your own Frida server binary")
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDownload) {
                    Text("Download")
                }
                TextButton(onClick = onSelectFile) {
                    Text("Select File")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RedownloadDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onSelectFile: () -> Unit,
    serverInfo: String?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server Already Installed") },
        text = { 
            Text("Frida server is already installed${if (serverInfo != null) ": $serverInfo" else ""}.\n\nChoose how to reinstall:\n• Download: Get latest from GitHub\n• Select File: Use your own binary")
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onConfirm) {
                    Text("Download")
                }
                TextButton(onClick = onSelectFile) {
                    Text("Select File")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun VersionSelectionDialog(
    isLoading: Boolean,
    releases: List<FridaInstaller.FridaRelease>,
    onVersionSelected: (FridaInstaller.FridaRelease) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Frida Version") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Loading available versions...")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(releases) { release ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onVersionSelected(release) }
                                    .border(
                                        1.dp, 
                                        if (release.prerelease) Color(0xFFFF9800) else Color.Gray, 
                                        RoundedCornerShape(4.dp)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (release.prerelease) Color(0xFFFFF3E0) else Color(0xFFF5F5F5)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = release.getDisplayName(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (release.name != release.tagName) {
                                        Text(
                                            text = release.name,
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Text(
                                        text = "Published: ${formatPublishDate(release.publishedAt)}",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1fKB", kb)
    val mb = kb / 1024.0
    return String.format("%.1fMB", mb)
}

fun formatPublishDate(publishedAt: String): String {
    return try {
        // Parse ISO 8601 format: 2023-12-15T10:30:00Z
        val date = publishedAt.substring(0, 10) // Extract YYYY-MM-DD
        date
    } catch (e: Exception) {
        publishedAt.take(10) // Fallback to first 10 characters
    }
}

fun getRealPathFromUri(context: ComponentActivity, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val tempFile = java.io.File(context.cacheDir, "temp_frida_server")
        inputStream?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        tempFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

@Preview(showBackground = true)
@Composable
fun FridaInstallerScreenPreview() {
    FridaServerInstallerTheme {
        // Preview implementation would go here
    }
}