package com.prapps.fridaserverinstaller;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import java.util.ArrayList;

import org.tukaani.xz.XZInputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FridaInstaller {
    private static final String TAG = "FridaInstaller";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/frida/frida/releases/latest";
    private static final String GITHUB_ALL_RELEASES_URL = "https://api.github.com/repos/frida/frida/releases";
    
    public static class FridaRelease {
        public String tagName;
        public String name;
        public String publishedAt;
        public boolean prerelease;
        public JsonArray assets;
        
        public FridaRelease(String tagName, String name, String publishedAt, boolean prerelease, JsonArray assets) {
            this.tagName = tagName;
            this.name = name;
            this.publishedAt = publishedAt;
            this.prerelease = prerelease;
            this.assets = assets;
        }
        
        public String getDisplayName() {
            return tagName + (prerelease ? " (Pre-release)" : "");
        }
    }
    
    private final Context context;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private Process fridaServerProcess;
    private String currentServerType = "Unknown";
    
    public interface InstallCallback {
        void onProgress(String message);
        void onError(String error);
        void onSuccess(String message);
        void onDownloadProgress(int progress, long bytesDownloaded, long totalBytes);
    }
    
    public interface ReleasesCallback {
        void onReleasesLoaded(List<FridaRelease> releases);
        void onError(String error);
    }

    public FridaInstaller(Context context) {
        this.context = context;
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
        loadCurrentServerType();
    }
    
    private void loadCurrentServerType() {
        String serverInfo = getInstalledServerInfo();
        if (serverInfo != null) {
            if (serverInfo.startsWith("Manual Installation")) {
                currentServerType = serverInfo; // Already has "Manual Installation" prefix
            } else {
                currentServerType = "Downloaded: " + serverInfo;
            }
        } else {
            currentServerType = "Unknown";
        }
    }

    public boolean isRooted() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            process.getOutputStream().write("id\n".getBytes());
            process.getOutputStream().write("exit\n".getBytes());
            process.getOutputStream().flush();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.readLine();
            int exitCode = process.waitFor();
            
            Log.d(TAG, "Root check output: " + output);
            Log.d(TAG, "Root check exit code: " + exitCode);
            
            return exitCode == 0 && output != null && output.contains("uid=0");
        } catch (Exception e) {
            Log.e(TAG, "Root check failed", e);
            return false;
        }
    }

    public String getDeviceArchitecture() {
        String abi = Build.CPU_ABI;
        Log.d(TAG, "Device ABI: " + abi);
        
        switch (abi) {
            case "arm64-v8a":
                return "arm64";
            case "armeabi-v7a":
                return "arm";
            case "x86":
                return "x86";
            case "x86_64":
                return "x86_64";
            default:
                return abi;
        }
    }

    private File getFridaDownloadDir() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File fridaDir = new File(downloadsDir, "FridaServerInstaller");
        if (!fridaDir.exists()) {
            fridaDir.mkdirs();
        }
        return fridaDir;
    }
    
    private File getFridaInternalDir() {
        File fridaDir = new File(context.getFilesDir(), "frida");
        if (!fridaDir.exists()) {
            fridaDir.mkdirs();
        }
        return fridaDir;
    }
    
    public boolean isServerAlreadyInstalled() {
        File fridaDir = getFridaInternalDir();
        File serverFile = new File(fridaDir, "frida-server");
        return serverFile.exists() && serverFile.canExecute();
    }
    
    public String getInstalledServerInfo() {
        File fridaDir = getFridaInternalDir();
        File serverFile = new File(fridaDir, "frida-server");
        File infoFile = new File(fridaDir, "server-info.txt");
        
        if (serverFile.exists() && infoFile.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(infoFile)));
                return reader.readLine();
            } catch (Exception e) {
                return "Unknown version";
            }
        }
        return null;
    }
    
    public String getCurrentServerType() {
        return currentServerType;
    }
    
    private void removeExistingInstallation() {
        try {
            // Stop any running server first
            stopFridaServer();
            
            File fridaDir = getFridaInternalDir();
            File serverFile = new File(fridaDir, "frida-server");
            File infoFile = new File(fridaDir, "server-info.txt");
            
            // Remove server binary
            if (serverFile.exists()) {
                serverFile.delete();
            }
            
            // Remove info file
            if (infoFile.exists()) {
                infoFile.delete();
            }
            
            // Update current server type
            currentServerType = "Unknown";
            
            Log.d(TAG, "Existing Frida installation removed");
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove existing installation", e);
        }
    }
    
    public void installFridaServer(InstallCallback callback) {
        installFridaServer(callback, false);
    }
    
    public void installFridaServer(InstallCallback callback, boolean forceRedownload) {
        installFridaServerFromLatest(callback, forceRedownload);
    }
    
    public void installFridaServerFromRelease(FridaRelease release, InstallCallback callback, boolean forceRedownload) {
        new Thread(() -> {
            try {
                callback.onProgress("🛑 Stopping any running Frida server...");
                stopFridaServer();
                
                callback.onProgress("🔐 Checking root permissions...");
                boolean rootStatus = isRooted();
                if (!rootStatus) {
                    callback.onProgress("❌ Root check failed - No root access");
                    callback.onError("Root access is required but not available");
                    return;
                } else {
                    callback.onProgress("✅ Root access confirmed - Device is rooted");
                }

                callback.onProgress("📱 Detecting device architecture...");
                String arch = getDeviceArchitecture();
                callback.onProgress("✅ Device architecture detected: " + arch);

                if (!forceRedownload && isServerAlreadyInstalled()) {
                    String serverInfo = getInstalledServerInfo();
                    callback.onProgress("📋 Found existing server: " + (serverInfo != null ? serverInfo : "Unknown version"));
                    callback.onProgress("🗑️ Removing existing installation to install selected version...");
                    removeExistingInstallation();
                    callback.onProgress("✅ Previous installation removed");
                }

                callback.onProgress("✅ Selected Frida version: " + release.tagName);

                callback.onProgress("🔍 Finding matching server binary for " + arch + "...");
                String downloadUrl = findServerAssetInRelease(release, arch);
                if (downloadUrl == null) {
                    callback.onProgress("❌ No matching binary found for " + arch);
                    callback.onError("No matching server binary found for architecture: " + arch);
                    return;
                }
                callback.onProgress("✅ Found matching binary for download");

                callback.onProgress("📥 Starting download to /sdcard/Download/FridaServerInstaller/...");
                File downloadedFile = downloadAssetWithProgress(downloadUrl, callback);
                if (downloadedFile == null) {
                    callback.onProgress("❌ Download failed");
                    callback.onError("Failed to download Frida server");
                    return;
                }
                callback.onProgress("✅ Download completed: " + downloadedFile.getName());

                callback.onProgress("📦 Extracting server binary...");
                File extractedFile = extractXzFile(downloadedFile);
                if (extractedFile == null) {
                    callback.onProgress("❌ Extraction failed");
                    callback.onError("Failed to extract server binary");
                    return;
                }
                callback.onProgress("✅ Extraction completed");

                callback.onProgress("🔧 Setting executable permissions with root...");
                if (!setExecutablePermissions(extractedFile)) {
                    callback.onProgress("❌ Permission setting failed");
                    callback.onError("Failed to set executable permissions");
                    return;
                }
                callback.onProgress("✅ Executable permissions set successfully");
                
                saveServerInfo(release.tagName, arch);
                loadCurrentServerType(); // Reload to ensure consistency
                callback.onSuccess("Frida server " + release.tagName + " installed successfully!");

            } catch (Exception e) {
                Log.e(TAG, "Installation failed", e);
                callback.onError("Installation failed: " + e.getMessage());
            }
        }).start();
    }
    
    private void installFridaServerFromLatest(InstallCallback callback, boolean forceRedownload) {
        new Thread(() -> {
            try {
                callback.onProgress("🛑 Stopping any running Frida server...");
                stopFridaServer();
                
                callback.onProgress("🔐 Checking root permissions...");
                boolean rootStatus = isRooted();
                if (!rootStatus) {
                    callback.onProgress("❌ Root check failed - No root access");
                    callback.onError("Root access is required but not available");
                    return;
                } else {
                    callback.onProgress("✅ Root access confirmed - Device is rooted");
                }

                callback.onProgress("📱 Detecting device architecture...");
                String arch = getDeviceArchitecture();
                callback.onProgress("✅ Device architecture detected: " + arch);

                if (!forceRedownload && isServerAlreadyInstalled()) {
                    String serverInfo = getInstalledServerInfo();
                    callback.onProgress("📋 Found existing server: " + (serverInfo != null ? serverInfo : "Unknown version"));
                    callback.onSuccess("✅ Frida server already installed! " + (serverInfo != null ? serverInfo : ""));
                    return;
                }

                callback.onProgress("🌐 Fetching latest Frida release from GitHub...");
                JsonObject release = getLatestRelease();
                if (release == null) {
                    callback.onProgress("❌ Failed to fetch release information");
                    callback.onError("Failed to fetch latest release information");
                    return;
                }

                String version = release.get("tag_name").getAsString();
                callback.onProgress("✅ Latest Frida version found: " + version);

                callback.onProgress("🔍 Finding matching server binary for " + arch + "...");
                String downloadUrl = findServerAsset(release, arch);
                if (downloadUrl == null) {
                    callback.onProgress("❌ No matching binary found for " + arch);
                    callback.onError("No matching server binary found for architecture: " + arch);
                    return;
                }
                callback.onProgress("✅ Found matching binary for download");

                callback.onProgress("📥 Starting download to /sdcard/Download/FridaServerInstaller/...");
                File downloadedFile = downloadAssetWithProgress(downloadUrl, callback);
                if (downloadedFile == null) {
                    callback.onProgress("❌ Download failed");
                    callback.onError("Failed to download Frida server");
                    return;
                }
                callback.onProgress("✅ Download completed: " + downloadedFile.getName());

                callback.onProgress("📦 Extracting server binary...");
                File extractedFile = extractXzFile(downloadedFile);
                if (extractedFile == null) {
                    callback.onProgress("❌ Extraction failed");
                    callback.onError("Failed to extract server binary");
                    return;
                }
                callback.onProgress("✅ Extraction completed");

                callback.onProgress("🔧 Setting executable permissions with root...");
                if (!setExecutablePermissions(extractedFile)) {
                    callback.onProgress("❌ Permission setting failed");
                    callback.onError("Failed to set executable permissions");
                    return;
                }
                callback.onProgress("✅ Executable permissions set successfully");
                
                saveServerInfo(version, arch);
                loadCurrentServerType(); // Reload to ensure consistency
                callback.onSuccess("Frida server " + version + " installed successfully!");

            } catch (Exception e) {
                Log.e(TAG, "Installation failed", e);
                callback.onError("Installation failed: " + e.getMessage());
            }
        }).start();
    }
    
    public void installFromManualFile(String filePath, InstallCallback callback) {
        new Thread(() -> {
            try {
                callback.onProgress("🛑 Stopping any running Frida server...");
                stopFridaServer();
                
                callback.onProgress("🔐 Checking root permissions...");
                boolean rootStatus = isRooted();
                if (!rootStatus) {
                    callback.onProgress("❌ Root check failed - No root access");
                    callback.onError("Root access is required but not available");
                    return;
                } else {
                    callback.onProgress("✅ Root access confirmed - Device is rooted");
                }
                
                File sourceFile = new File(filePath);
                if (!sourceFile.exists()) {
                    callback.onProgress("❌ Selected file does not exist: " + filePath);
                    callback.onError("Selected file does not exist");
                    return;
                }
                
                callback.onProgress("📁 Processing selected file: " + sourceFile.getName() + " (" + formatFileSize(sourceFile.length()) + ")");
                
                File fridaDir = getFridaInternalDir();
                
                // Determine file type for processing
                String fileName = sourceFile.getName().toLowerCase();
                boolean isXzFile = fileName.endsWith(".xz");
                
                File targetFile;
                if (isXzFile) {
                    callback.onProgress("📦 Processing compressed file (.xz)...");
                    File tempFile = new File(fridaDir, "temp-server.xz");
                    copyFile(sourceFile, tempFile);
                    callback.onProgress("📦 Extracting server binary...");
                    targetFile = extractXzFile(tempFile);
                    tempFile.delete(); // Clean up temp file
                } else {
                    callback.onProgress("📁 Processing raw binary file...");
                    targetFile = new File(fridaDir, "frida-server");
                    
                    // Remove existing file if it exists to prevent ETXTBSY error
                    if (targetFile.exists()) {
                        targetFile.delete();
                    }
                    
                    copyFile(sourceFile, targetFile);
                }
                callback.onProgress("✅ File processing completed");
                
                if (targetFile == null) {
                    callback.onProgress("❌ File processing failed");
                    callback.onError("Failed to process server file");
                    return;
                }
                
                callback.onProgress("🔧 Setting executable permissions with root...");
                if (!setExecutablePermissions(targetFile)) {
                    callback.onProgress("❌ Permission setting failed");
                    callback.onError("Failed to set executable permissions");
                    return;
                }
                callback.onProgress("✅ Executable permissions set successfully");
                
                saveServerInfo("Manual Installation (" + sourceFile.getName() + ")", "Unknown");
                loadCurrentServerType(); // Reload to ensure consistency
                callback.onSuccess("✅ Frida server installed successfully from manual file!");
                
            } catch (Exception e) {
                Log.e(TAG, "Manual installation failed", e);
                callback.onProgress("❌ Manual installation error: " + e.getMessage());
                callback.onError("Manual installation failed: " + e.getMessage());
            }
        }).start();
    }

    private JsonObject getLatestRelease() throws IOException {
        Request request = new Request.Builder()
                .url(GITHUB_API_URL)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch release info: " + response.code());
            }
            String responseBody = response.body().string();
            return gson.fromJson(responseBody, JsonObject.class);
        }
    }
    
    public void getAllReleases(ReleasesCallback callback) {
        new Thread(() -> {
            try {
                List<FridaRelease> releases = fetchAllReleases();
                callback.onReleasesLoaded(releases);
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch releases", e);
                callback.onError("Failed to fetch releases: " + e.getMessage());
            }
        }).start();
    }
    
    private List<FridaRelease> fetchAllReleases() throws IOException {
        Request request = new Request.Builder()
                .url(GITHUB_ALL_RELEASES_URL + "?per_page=50")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch releases: " + response.code());
            }
            
            String responseBody = response.body().string();
            JsonArray releasesArray = gson.fromJson(responseBody, JsonArray.class);
            
            List<FridaRelease> releases = new ArrayList<>();
            for (int i = 0; i < releasesArray.size(); i++) {
                JsonObject releaseObj = releasesArray.get(i).getAsJsonObject();
                String tagName = releaseObj.get("tag_name").getAsString();
                String name = releaseObj.has("name") && !releaseObj.get("name").isJsonNull() 
                    ? releaseObj.get("name").getAsString() : tagName;
                String publishedAt = releaseObj.get("published_at").getAsString();
                boolean prerelease = releaseObj.get("prerelease").getAsBoolean();
                JsonArray assets = releaseObj.getAsJsonArray("assets");
                
                // Only include releases that have assets for Android
                if (hasAndroidAssets(assets)) {
                    releases.add(new FridaRelease(tagName, name, publishedAt, prerelease, assets));
                }
            }
            
            return releases;
        }
    }
    
    private boolean hasAndroidAssets(JsonArray assets) {
        for (int i = 0; i < assets.size(); i++) {
            JsonObject asset = assets.get(i).getAsJsonObject();
            String name = asset.get("name").getAsString();
            if (name.contains("android") && name.contains("frida-server")) {
                return true;
            }
        }
        return false;
    }

    private String findServerAsset(JsonObject release, String arch) {
        JsonArray assets = release.getAsJsonArray("assets");
        String version = release.get("tag_name").getAsString();
        
        String expectedName = "frida-server-" + version + "-android-" + arch + ".xz";
        
        for (int i = 0; i < assets.size(); i++) {
            JsonObject asset = assets.get(i).getAsJsonObject();
            String name = asset.get("name").getAsString();
            
            if (name.equals(expectedName)) {
                return asset.get("browser_download_url").getAsString();
            }
        }
        
        return null;
    }
    
    private String findServerAssetInRelease(FridaRelease release, String arch) {
        String version = release.tagName;
        String expectedName = "frida-server-" + version + "-android-" + arch + ".xz";
        
        for (int i = 0; i < release.assets.size(); i++) {
            JsonObject asset = release.assets.get(i).getAsJsonObject();
            String name = asset.get("name").getAsString();
            
            if (name.equals(expectedName)) {
                return asset.get("browser_download_url").getAsString();
            }
        }
        
        return null;
    }
    
    private File downloadAssetWithProgress(String url, InstallCallback callback) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        File downloadDir = getFridaDownloadDir();
        String fileName = url.substring(url.lastIndexOf("/") + 1);
        File outputFile = new File(downloadDir, fileName);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download asset: " + response.code());
            }

            long totalBytes = response.body().contentLength();
            long downloadedBytes = 0;

            try (InputStream inputStream = response.body().byteStream();
                 FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    downloadedBytes += bytesRead;
                    
                    if (totalBytes > 0) {
                        int progress = (int) ((downloadedBytes * 100) / totalBytes);
                        callback.onDownloadProgress(progress, downloadedBytes, totalBytes);
                    }
                }
            }
        }

        return outputFile;
    }

    private File downloadAsset(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        File downloadDir = new File(context.getFilesDir(), "frida");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        File outputFile = new File(downloadDir, "frida-server.xz");

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download asset: " + response.code());
            }

            try (InputStream inputStream = response.body().byteStream();
                 FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        }

        return outputFile;
    }

    private File extractXzFile(File xzFile) throws IOException {
        File internalDir = getFridaInternalDir();
        File outputFile = new File(internalDir, "frida-server");
        
        // Remove existing file if it exists to prevent ETXTBSY error
        if (outputFile.exists()) {
            outputFile.delete();
        }
        
        try (FileInputStream fileInputStream = new FileInputStream(xzFile);
             XZInputStream xzInputStream = new XZInputStream(fileInputStream);
             FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = xzInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        // Keep the downloaded file in Downloads folder, don't delete it
        return outputFile;
    }

    private boolean setExecutablePermissions(File file) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            String command = "chmod 755 " + file.getAbsolutePath() + "\n";
            process.getOutputStream().write(command.getBytes());
            process.getOutputStream().write("exit\n".getBytes());
            process.getOutputStream().flush();
            
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String errorLine;
            while ((errorLine = errorReader.readLine()) != null) {
                Log.e(TAG, "chmod error: " + errorLine);
            }
            
            int exitCode = process.waitFor();
            Log.d(TAG, "chmod exit code: " + exitCode);
            
            if (exitCode == 0) {
                return file.canExecute();
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set permissions", e);
            return false;
        }
    }

    public void startFridaServer(InstallCallback callback) {
        new Thread(() -> {
            try {
                File fridaDir = getFridaInternalDir();
                File serverFile = new File(fridaDir, "frida-server");
                
                if (!serverFile.exists()) {
                    callback.onError("Frida server not found. Please install it first.");
                    return;
                }
                
                callback.onProgress("🛑 Stopping any existing Frida server...");
                stopFridaServer();
                
                callback.onProgress("🚀 Starting Frida server: " + currentServerType);
                callback.onProgress("📡 Server will listen on 0.0.0.0:27042");
                callback.onProgress("📝 Real-time output will be shown below:");
                
                // Start server in foreground mode to capture output
                fridaServerProcess = Runtime.getRuntime().exec("su");
                String command = "cd /data/local/tmp && " + serverFile.getAbsolutePath() + " -l 0.0.0.0:27042\n";
                fridaServerProcess.getOutputStream().write(command.getBytes());
                fridaServerProcess.getOutputStream().flush();
                
                // Create threads to read stdout and stderr continuously
                Thread stdoutReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(fridaServerProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            callback.onProgress("📤 [STDOUT] " + line);
                        }
                        callback.onProgress("📤 [STDOUT] Stream ended");
                    } catch (Exception e) {
                        callback.onProgress("❌ Error reading stdout: " + e.getMessage());
                    }
                });
                
                Thread stderrReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(fridaServerProcess.getErrorStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            callback.onProgress("🔴 [STDERR] " + line);
                        }
                        callback.onProgress("🔴 [STDERR] Stream ended");
                    } catch (Exception e) {
                        callback.onProgress("❌ Error reading stderr: " + e.getMessage());
                    }
                });
                
                stdoutReader.setDaemon(true); // Don't prevent app shutdown
                stderrReader.setDaemon(true);
                stdoutReader.start();
                stderrReader.start();
                
                // Wait a bit for initial output
                Thread.sleep(3000);
                
                if (isServerRunning()) {
                    callback.onSuccess("✅ Frida server started successfully! Output will continue to be displayed in real-time.");
                } else {
                    callback.onError("❌ Failed to start Frida server. Check output above for errors.");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to start server", e);
                callback.onError("Failed to start server: " + e.getMessage());
            }
        }).start();
    }
    
    public void stopFridaServer() {
        try {
            // First try to terminate our managed process
            if (fridaServerProcess != null && fridaServerProcess.isAlive()) {
                fridaServerProcess.destroy();
                Thread.sleep(1000);
                if (fridaServerProcess.isAlive()) {
                    fridaServerProcess.destroyForcibly();
                }
                fridaServerProcess = null;
            }
            
            // Also kill any other frida-server processes
            Process process = Runtime.getRuntime().exec("su");
            process.getOutputStream().write("pkill frida-server\n".getBytes());
            process.getOutputStream().write("exit\n".getBytes());
            process.getOutputStream().flush();
            process.waitFor();
            Thread.sleep(1000);
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop server", e);
        }
    }
    
    public boolean isServerRunning() {
        try {
            Process checkProcess = Runtime.getRuntime().exec("su");
            checkProcess.getOutputStream().write("pgrep frida-server\n".getBytes());
            checkProcess.getOutputStream().write("exit\n".getBytes());
            checkProcess.getOutputStream().flush();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(checkProcess.getInputStream()));
            String pid = reader.readLine();
            int checkExitCode = checkProcess.waitFor();
            
            Log.d(TAG, "Server check PID: " + pid);
            Log.d(TAG, "Server check exit code: " + checkExitCode);
            
            return checkExitCode == 0 && pid != null && !pid.trim().isEmpty();
        } catch (Exception e) {
            Log.e(TAG, "Failed to check server status", e);
            return false;
        }
    }
    
    private void saveServerInfo(String version, String arch) {
        try {
            File fridaDir = getFridaInternalDir();
            File infoFile = new File(fridaDir, "server-info.txt");
            FileOutputStream fos = new FileOutputStream(infoFile);
            String info = version + " (" + arch + ")";
            fos.write(info.getBytes());
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save server info", e);
        }
    }
    
    private void copyFile(File source, File dest) throws IOException {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(dest)) {
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }
    
    private String validateFridaBinary(File file) {
        String fileName = file.getName().toLowerCase();
        
        // Check 1: File size - Frida server should be reasonable size (1MB - 50MB)
        long fileSize = file.length();
        if (fileSize < 1024 * 1024) { // Less than 1MB
            return "File too small (" + formatFileSize(fileSize) + ") - likely not a Frida server binary";
        }
        if (fileSize > 50 * 1024 * 1024) { // More than 50MB
            return "File too large (" + formatFileSize(fileSize) + ") - likely not a Frida server binary";
        }
        
        // Check 2: Filename validation
        boolean hasValidName = fileName.contains("frida") && fileName.contains("server");
        if (!hasValidName) {
            return "Filename does not contain 'frida' and 'server' - expected pattern like 'frida-server-x.x.x-android-arch'";
        }
        
        // Check 3: File extension validation
        boolean isCompressed = fileName.endsWith(".xz") || fileName.endsWith(".gz") || fileName.endsWith(".zip");
        boolean isBinary = !fileName.contains(".") || fileName.endsWith(".bin");
        if (!isCompressed && !isBinary) {
            return "Unsupported file format - expected .xz, .gz, .zip, or raw binary";
        }
        
        // Check 4: Architecture pattern validation (if present in filename)
        if (fileName.contains("android")) {
            boolean hasValidArch = fileName.contains("arm64") || fileName.contains("arm") || 
                                   fileName.contains("x86") || fileName.contains("aarch64") ||
                                   fileName.contains("x86_64");
            if (!hasValidArch) {
                return "No valid Android architecture found in filename (expected: arm64, arm, x86, x86_64)";
            }
        }
        
        // Check 5: Magic bytes validation for known formats
        try {
            byte[] header = new byte[16];
            FileInputStream fis = new FileInputStream(file);
            int bytesRead = fis.read(header);
            fis.close();
            
            if (bytesRead >= 6) {
                // Check for XZ magic bytes
                if (fileName.endsWith(".xz")) {
                    if (!(header[0] == (byte)0xFD && header[1] == '7' && header[2] == 'z' && 
                          header[3] == 'X' && header[4] == 'Z' && header[5] == 0x00)) {
                        return "Invalid XZ file - corrupted or not a valid .xz compressed file";
                    }
                }
                
                // Check for ELF magic bytes (raw binary)
                if (isBinary && bytesRead >= 4) {
                    if (!(header[0] == 0x7F && header[1] == 'E' && header[2] == 'L' && header[3] == 'F')) {
                        return "Not a valid ELF binary - Frida server should be an ELF executable";
                    }
                }
            }
        } catch (Exception e) {
            return "Cannot read file header - file may be corrupted or inaccessible";
        }
        
        return null; // Validation passed
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }
}