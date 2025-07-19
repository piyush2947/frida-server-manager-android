package com.prapps.fridaserverinstaller

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class InstallStatus {
    IDLE, INSTALLING, SUCCESS, ERROR, SERVER_STARTING, SERVER_RUNNING, SERVER_STOPPED
}

data class InstallUiState(
    val status: InstallStatus = InstallStatus.IDLE,
    val messages: List<String> = emptyList(),
    val currentMessage: String = "",
    val isServerInstalled: Boolean = false,
    val isServerRunning: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val serverInfo: String? = null,
    val currentServerType: String = "Unknown",
    val showRedownloadDialog: Boolean = false,
    val showInstallTypeDialog: Boolean = false,
    val showVersionSelectionDialog: Boolean = false,
    val availableReleases: List<FridaInstaller.FridaRelease> = emptyList(),
    val isLoadingReleases: Boolean = false
)

class FridaInstallerViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(InstallUiState())
    val uiState: StateFlow<InstallUiState> = _uiState.asStateFlow()
    
    private val fridaInstaller = FridaInstaller(context)
    
    init {
        checkExistingInstallation()
    }
    
    private fun checkExistingInstallation() {
        if (fridaInstaller.isServerAlreadyInstalled()) {
            val serverInfo = fridaInstaller.installedServerInfo
            val currentServerType = fridaInstaller.currentServerType
            _uiState.value = _uiState.value.copy(
                isServerInstalled = true,
                serverInfo = serverInfo,
                currentServerType = currentServerType,
                status = InstallStatus.SUCCESS
            )
        } else {
            _uiState.value = _uiState.value.copy(
                currentServerType = "Not installed"
            )
        }
    }
    
    fun startInstallation() {
        if (fridaInstaller.isServerAlreadyInstalled()) {
            _uiState.value = _uiState.value.copy(showRedownloadDialog = true)
            return
        }
        
        _uiState.value = _uiState.value.copy(showInstallTypeDialog = true)
    }
    
    fun startServer() {
        _uiState.value = _uiState.value.copy(status = InstallStatus.SERVER_STARTING)
        
        fridaInstaller.startFridaServer(object : FridaInstaller.InstallCallback {
            override fun onProgress(message: String) {
                val currentMessages = _uiState.value.messages.toMutableList()
                currentMessages.add(message)
                _uiState.value = _uiState.value.copy(messages = currentMessages)
            }
            
            override fun onError(error: String) {
                val currentMessages = _uiState.value.messages.toMutableList()
                currentMessages.add("ERROR: $error")
                _uiState.value = _uiState.value.copy(
                    status = InstallStatus.ERROR,
                    messages = currentMessages
                )
            }
            
            override fun onSuccess(message: String) {
                val currentMessages = _uiState.value.messages.toMutableList()
                currentMessages.add(message)
                _uiState.value = _uiState.value.copy(
                    status = InstallStatus.SERVER_RUNNING,
                    messages = currentMessages,
                    isServerRunning = true
                )
            }
            
            override fun onDownloadProgress(progress: Int, bytesDownloaded: Long, totalBytes: Long) {
                // Not used for server start
            }
        })
    }
    
    fun stopServer() {
        fridaInstaller.stopFridaServer()
        val currentMessages = _uiState.value.messages.toMutableList()
        currentMessages.add("🛑 Frida server stopped")
        _uiState.value = _uiState.value.copy(
            status = InstallStatus.SERVER_STOPPED,
            messages = currentMessages,
            isServerRunning = false,
            currentMessage = "🛑 Frida server stopped"
        )
    }
    
    fun forceRedownload() {
        _uiState.value = _uiState.value.copy(
            showRedownloadDialog = false,
            showVersionSelectionDialog = true,
            isLoadingReleases = true
        )
        loadAvailableReleases()
    }
    
    fun installFromManualFile(filePath: String) {
        _uiState.value = _uiState.value.copy(
            status = InstallStatus.INSTALLING,
            messages = emptyList()
        )
        
        fridaInstaller.installFromManualFile(filePath, createInstallCallback())
    }
    
    fun dismissRedownloadDialog() {
        _uiState.value = _uiState.value.copy(showRedownloadDialog = false)
    }
    
    fun dismissInstallTypeDialog() {
        _uiState.value = _uiState.value.copy(showInstallTypeDialog = false)
    }
    
    fun downloadAndInstall() {
        _uiState.value = _uiState.value.copy(
            showInstallTypeDialog = false,
            showVersionSelectionDialog = true,
            isLoadingReleases = true
        )
        loadAvailableReleases()
    }
    
    fun loadAvailableReleases() {
        fridaInstaller.getAllReleases(object : FridaInstaller.ReleasesCallback {
            override fun onReleasesLoaded(releases: List<FridaInstaller.FridaRelease>) {
                _uiState.value = _uiState.value.copy(
                    availableReleases = releases,
                    isLoadingReleases = false
                )
            }
            
            override fun onError(error: String) {
                _uiState.value = _uiState.value.copy(
                    isLoadingReleases = false,
                    showVersionSelectionDialog = false
                )
                val currentMessages = _uiState.value.messages.toMutableList()
                currentMessages.add("ERROR: $error")
                _uiState.value = _uiState.value.copy(
                    status = InstallStatus.ERROR,
                    messages = currentMessages
                )
            }
        })
    }
    
    fun dismissVersionSelectionDialog() {
        _uiState.value = _uiState.value.copy(showVersionSelectionDialog = false)
    }
    
    fun installFromSelectedVersion(release: FridaInstaller.FridaRelease) {
        _uiState.value = _uiState.value.copy(
            status = InstallStatus.INSTALLING,
            messages = emptyList(),
            downloadProgress = 0,
            downloadedBytes = 0,
            totalBytes = 0,
            showVersionSelectionDialog = false
            // Keep existing currentServerType and other state
        )
        performInstallationFromRelease(release, false)
    }
    
    fun forceRedownloadFromVersion(release: FridaInstaller.FridaRelease) {
        _uiState.value = _uiState.value.copy(
            status = InstallStatus.INSTALLING,
            messages = emptyList(),
            downloadProgress = 0,
            downloadedBytes = 0,
            totalBytes = 0,
            showRedownloadDialog = false,
            showVersionSelectionDialog = false
            // Keep existing currentServerType and other state
        )
        performInstallationFromRelease(release, true)
    }
    
    private fun performInstallation(forceRedownload: Boolean) {
        fridaInstaller.installFridaServer(createInstallCallback(), forceRedownload)
    }
    
    private fun performInstallationFromRelease(release: FridaInstaller.FridaRelease, forceRedownload: Boolean) {
        fridaInstaller.installFridaServerFromRelease(release, createInstallCallback(), forceRedownload)
    }
    
    private fun createInstallCallback() = object : FridaInstaller.InstallCallback {
        override fun onProgress(message: String) {
            val currentMessages = _uiState.value.messages.toMutableList()
            currentMessages.add(message)
            _uiState.value = _uiState.value.copy(
                messages = currentMessages,
                currentMessage = message
            )
        }
        
        override fun onError(error: String) {
            val currentMessages = _uiState.value.messages.toMutableList()
            currentMessages.add("ERROR: $error")
            _uiState.value = _uiState.value.copy(
                status = InstallStatus.ERROR,
                messages = currentMessages,
                currentMessage = error
            )
        }
        
        override fun onSuccess(message: String) {
            val currentMessages = _uiState.value.messages.toMutableList()
            currentMessages.add(message)
            val serverInfo = fridaInstaller.installedServerInfo
            val currentServerType = fridaInstaller.currentServerType
            _uiState.value = _uiState.value.copy(
                status = InstallStatus.SUCCESS,
                messages = currentMessages,
                currentMessage = message,
                isServerInstalled = true,
                serverInfo = serverInfo,
                currentServerType = currentServerType
            )
        }
        
        override fun onDownloadProgress(progress: Int, bytesDownloaded: Long, totalBytes: Long) {
            _uiState.value = _uiState.value.copy(
                downloadProgress = progress,
                downloadedBytes = bytesDownloaded,
                totalBytes = totalBytes
            )
        }
    }
    
    fun resetInstallation() {
        // Reset to initial state but preserve server information
        val newState = InstallUiState()
        _uiState.value = newState
        
        // Update server info but keep status as IDLE to show install button
        if (fridaInstaller.isServerAlreadyInstalled()) {
            val serverInfo = fridaInstaller.installedServerInfo
            val currentServerType = fridaInstaller.currentServerType
            _uiState.value = _uiState.value.copy(
                isServerInstalled = true,
                serverInfo = serverInfo,
                currentServerType = currentServerType
                // Keep status = InstallStatus.IDLE to show install button
            )
        } else {
            _uiState.value = _uiState.value.copy(
                currentServerType = "Not installed"
            )
        }
    }
    
    fun refreshInstallationStatus() {
        checkExistingInstallation()
    }
}