package com.example.restate.toolwindow

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.download.DownloadableFileSetDescription
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit
import java.util.Comparator

/**
 * Manages the Restate server binary, including downloading and updating it.
 */
class RestateServerManager {
    private val LOG = Logger.getInstance(RestateServerManager::class.java)

    // GitHub repository information
    private val REPO_OWNER = "restatedev"
    private val REPO_NAME = "restate"

    // Binary paths
    private val RESTATE_BINARY_DIR = Paths.get(PathManager.getSystemPath(), "restate-plugin")
    private val RESTATE_BINARY_PATH: Path
        get() = RESTATE_BINARY_DIR.resolve("restate-server")

    init {
        // Create binary directory if it doesn't exist
        Files.createDirectories(RESTATE_BINARY_DIR)
    }

    /**
     * Downloads the latest release of the Restate server binary.
     * 
     * @return The path to the downloaded binary
     * @throws Exception if the download fails
     */
    fun downloadLatestRelease(project: Project): Path {
        LOG.info("Downloading latest Restate release")

        try {
            // Connect to GitHub API
            val github = GitHubBuilder().build()
            val repository = github.getRepository("$REPO_OWNER/$REPO_NAME")

            // Get the latest release
            val latestRelease = repository.listReleases().iterator().next()
            LOG.info("Latest release: ${latestRelease.name}")

            // Find the appropriate asset for the current platform
            val os = getOperatingSystem()
            val arch = getArchitecture()
            LOG.info("Looking for asset for OS: $os, Architecture: $arch")

            val asset = latestRelease.listAssets().find {
                it.name.contains("restate-server") && 
                it.name.contains(os) && 
                it.name.contains(arch) 
            } ?: throw Exception("Could not find appropriate release asset for your platform")

            // Now use Intellij Platform downloader.
            val downloaderService = DownloadableFileService.getInstance()
            val downloadFileDescription = downloaderService.createFileDescription(
                asset.browserDownloadUrl,
                asset.name
            )
            val downloader = downloaderService.createDownloader(
                listOf(downloadFileDescription),
                "Restate Server"
            )

            LOG.info("Downloading Restate binary from: ${asset.browserDownloadUrl}")

            // Create a temporary directory for the download
            val tempDir = Files.createTempDirectory("restate-download")
            val result = downloader.downloadWithBackgroundProgress(tempDir.toCanonicalPath(), project)
                .get()
            val downloadedFile =   result?.get(0)?.first?.inputStream?: throw Exception("No file was downloaded!")

            try {
                // Download the tar.xz file
//                val url = URI.create(asset.browserDownloadUrl).toURL()
//                url.openConnection().getInputStream().use { input ->
//                    Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
//                }
//                LOG.info("Downloaded archive to: $tempFile")

                // Extract the tar.xz file
                val extractDir = tempDir.resolve("extracted")
                Files.createDirectories(extractDir)

                LOG.info("Extracting archive using Apache Commons Compression...")

                // Create a chain of streams to handle the XZ and TAR formats
                var extractedBinary: Path? = null

                BufferedInputStream(downloadedFile).use { fileIn ->
                    XZCompressorInputStream(fileIn).use { xzIn ->
                        TarArchiveInputStream(xzIn).use { tarIn ->
                            var entry = tarIn.nextEntry
                            while (entry != null) {
                                // Check if this entry is the restate-server binary
                                if (!entry.isDirectory && entry.name.endsWith("restate-server")) {
                                    LOG.info("Found restate-server binary in archive: ${entry.name}")

                                    // Create the target path
                                    val entryPath = extractDir.resolve("restate-server")

                                    // Create parent directories if they don't exist
                                    Files.createDirectories(entryPath.parent)

                                    // Copy the file content
                                    Files.copy(tarIn, entryPath, StandardCopyOption.REPLACE_EXISTING)

                                    // Save the path to the extracted binary
                                    extractedBinary = entryPath

                                    // We found what we need, no need to continue
                                    break
                                } else {
                                    LOG.info("Skipping archive entry: ${entry.name}")
                                }

                                entry = tarIn.nextEntry
                            }
                        }
                    }
                }

                LOG.info("Archive extraction completed")

                // Check if we found and extracted the binary
                if (extractedBinary == null) {
                    throw Exception("Could not find restate-server binary in extracted archive")
                }

                LOG.info("Found extracted binary at: $extractedBinary")

                // Copy the binary to the final location
                Files.copy(extractedBinary, RESTATE_BINARY_PATH, StandardCopyOption.REPLACE_EXISTING)

                // Make the binary executable
                makeExecutable(RESTATE_BINARY_PATH)

                LOG.info("Successfully installed Restate binary to: $RESTATE_BINARY_PATH")
                return RESTATE_BINARY_PATH
            } finally {
                // Clean up temporary files
                try {
                    Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach { Files.delete(it) }
                } catch (e: Exception) {
                    LOG.info("Failed to clean up temporary files", e)
                }
            }
        } catch (e: Exception) {
            LOG.error("Error downloading Restate release", e)
            throw e
        }
    }

    /**
     * Checks if the binary exists and is up to date.
     * 
     * @return true if the binary needs to be updated, false otherwise
     */
    fun shouldCheckForUpdates(): Boolean {
        // Check for updates once a day
        val file = RESTATE_BINARY_PATH.toFile()
        if (!file.exists()) return true

        val lastModified = file.lastModified()
        val oneDayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)

        return lastModified < oneDayAgo
    }

    /**
     * Gets the path to the Restate server binary.
     * 
     * @return The path to the binary
     */
    fun getBinaryPath(): Path {
        return RESTATE_BINARY_PATH
    }

    /**
     * Makes a file executable.
     * 
     * @param path The path to the file
     * @throws Exception if the operation fails
     */
    private fun makeExecutable(path: Path) {
        try {
            val file = path.toFile()
            if (!file.canExecute()) {
                // Try using PosixFilePermission if available
                try {
                    val permissions = Files.getPosixFilePermissions(path).toMutableSet()
                    permissions.add(PosixFilePermission.OWNER_EXECUTE)
                    Files.setPosixFilePermissions(path, permissions)
                } catch (e: UnsupportedOperationException) {
                    // Fallback for non-POSIX systems (e.g., Windows)
                    file.setExecutable(true)
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to make binary executable", e)
            throw e
        }
    }

    /**
     * Gets the operating system name in a format compatible with Restate's release assets.
     * 
     * @return The operating system name
     * @throws Exception if the operating system is not supported
     */
    private fun getOperatingSystem(): String {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("linux") -> "linux"
            osName.contains("mac") || osName.contains("darwin") -> "darwin"
            osName.contains("windows") -> "windows"
            else -> throw Exception("Unsupported operating system: $osName")
        }
    }

    /**
     * Gets the architecture name in a format compatible with Restate's release assets.
     * 
     * @return The architecture name
     * @throws Exception if the architecture is not supported
     */
    private fun getArchitecture(): String {
        val arch = System.getProperty("os.arch").lowercase()
        return when {
            arch.contains("amd64") || arch.contains("x86_64") -> "x86_64"
            arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
            else -> throw Exception("Unsupported architecture: $arch")
        }
    }
}
