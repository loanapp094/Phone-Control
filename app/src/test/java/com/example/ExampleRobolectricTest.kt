package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.calls.CallLogRepository
import com.example.data.contacts.ContactRepository
import com.example.data.export.FileExporter
import com.example.data.export.ZipExportManager
import com.example.data.security.KeyStoreManager
import com.example.data.security.PreferenceManager
import com.example.data.sms.SmsRepository
import com.example.domain.device.DeviceInfoProvider
import com.example.domain.export.ExportProgress
import com.example.domain.export.ExportState
import com.example.domain.export.ExportType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TeleManage", appName)
    }

    @Test
    fun `device info provider generates status`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = DeviceInfoProvider(context)
        val status = provider.getDeviceStatus(isServiceRunning = true, serviceStartTimeMs = System.currentTimeMillis())

        assertNotNull(status.deviceName)
        assertTrue(status.isServiceRunning)
        val telegramMsg = provider.generateStatusTelegramMessage(isServiceRunning = true, serviceStartTimeMs = 1000L)
        assertTrue(telegramMsg.contains("Device Status"))
        assertTrue(telegramMsg.contains("Battery"))
    }

    @Test
    fun `keystore manager encrypt and decrypt`() {
        val manager = KeyStoreManager()
        val originalToken = "123456789:ABCdefGhIJKlmNoPQRstuVWXyz"
        val encrypted = manager.encrypt(originalToken)
        val decrypted = manager.decrypt(encrypted)

        assertEquals(originalToken, decrypted)
    }

    @Test
    fun `preference manager defaults for contacts calls and sms`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefManager = PreferenceManager(context)
        val config = prefManager.botConfigFlow.value

        assertTrue(config.isContactAccessEnabled)
        assertTrue(config.isCallHistoryAccessEnabled)
        assertTrue(config.isSmsAccessEnabled)
        assertTrue(config.isSmsNotificationForwardingEnabled)
    }

    @Test
    fun `repositories instantiate correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val contactRepo = ContactRepository(context)
        val callRepo = CallLogRepository(context)
        val smsRepo = SmsRepository(context)

        assertNotNull(contactRepo)
        assertNotNull(callRepo)
        assertNotNull(smsRepo)
        // Permissions not granted by default in robolectric test context
        assertFalse(contactRepo.hasPermission())
        assertFalse(callRepo.hasPermission())
        assertFalse(smsRepo.hasPermission())
    }

    @Test
    fun `export type parsing from command`() {
        assertEquals(ExportType.CONTACTS, ExportType.fromCommand("contacts"))
        assertEquals(ExportType.CALLS, ExportType.fromCommand("calls"))
        assertEquals(ExportType.SMS, ExportType.fromCommand("sms"))
        assertEquals(ExportType.PHOTOS, ExportType.fromCommand("photos"))
        assertEquals(ExportType.VIDEOS, ExportType.fromCommand("videos"))
        assertEquals(ExportType.MEDIA, ExportType.fromCommand("media"))
        assertEquals(ExportType.FILES, ExportType.fromCommand("files"))
        assertEquals(ExportType.ALL, ExportType.fromCommand("all"))
        assertNull(ExportType.fromCommand("invalid_command"))
    }

    @Test
    fun `export progress state evaluates isRunning correctly`() {
        val idle = ExportProgress(state = ExportState.IDLE)
        assertFalse(idle.isRunning)

        val preparing = ExportProgress(state = ExportState.PREPARING)
        assertTrue(preparing.isRunning)

        val exporting = ExportProgress(state = ExportState.EXPORTING)
        assertTrue(exporting.isRunning)

        val zipping = ExportProgress(state = ExportState.ZIPPING)
        assertTrue(zipping.isRunning)

        val uploading = ExportProgress(state = ExportState.UPLOADING)
        assertTrue(uploading.isRunning)

        val completed = ExportProgress(state = ExportState.COMPLETED)
        assertFalse(completed.isRunning)

        val cancelled = ExportProgress(state = ExportState.CANCELLED)
        assertFalse(cancelled.isRunning)

        val failed = ExportProgress(state = ExportState.FAILED)
        assertFalse(failed.isRunning)
    }

    @Test
    fun `zip export manager creates valid zip archives and cleans up`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val tempDir = File(context.cacheDir, "test_export_${System.currentTimeMillis()}").apply { mkdirs() }
            val outputDir = File(context.cacheDir, "test_output_${System.currentTimeMillis()}").apply { mkdirs() }

            try {
                // Create test files
                val file1 = File(tempDir, "sample1.txt").apply { writeText("Hello World from TeleManage") }
                val file2 = File(tempDir, "sample2.json").apply { writeText("{\"key\":\"value\"}") }

                val zipManager = ZipExportManager(maxArchiveSizeBytes = 10 * 1024 * 1024)
                val result = zipManager.createZipArchives(
                    sourceFiles = listOf(file1, file2),
                    outputDir = outputDir,
                    archiveBaseName = "test_archive"
                )

                assertTrue(result.isSuccess)
                val archives = result.getOrThrow()
                assertEquals(1, archives.size)
                val zipFile = archives.first()
                assertTrue(zipFile.exists())
                assertTrue(zipFile.length() > 0)

                // Verify ZIP contents
                val zip = ZipFile(zipFile)
                assertNotNull(zip.getEntry("sample1.txt"))
                assertNotNull(zip.getEntry("sample2.json"))
                zip.close()

                // Verify cleanup
                zipManager.cleanDirectory(tempDir)
                assertFalse(tempDir.exists())
            } finally {
                tempDir.deleteRecursively()
                outputDir.deleteRecursively()
            }
        }
    }

    @Test
    fun `file exporter creates files manifest`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val outputDir = File(context.cacheDir, "test_file_export_${System.currentTimeMillis()}").apply { mkdirs() }

            try {
                // Put a test file in context.filesDir
                val dummyFile = File(context.filesDir, "test_export_doc.txt").apply { writeText("Sample app content") }

                val fileExporter = FileExporter(context)
                val result = fileExporter.exportAccessibleFiles(outputDir)

                assertTrue(result.isSuccess)
                val exportedFiles = result.getOrThrow()
                assertTrue(exportedFiles.isNotEmpty())
                assertTrue(exportedFiles.any { it.name == "files_manifest.json" })

                dummyFile.delete()
            } finally {
                outputDir.deleteRecursively()
            }
        }
    }

    @Test
    fun `preference manager records heartbeat and recovery lifecycle state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefManager = PreferenceManager(context)

        // Test heartbeat recording
        val initialHeartbeat = prefManager.getLastHeartbeat()
        prefManager.updateLastHeartbeat(1700000000000L)
        assertEquals(1700000000000L, prefManager.getLastHeartbeat())

        // Test recovery recording
        prefManager.recordServiceRecovery(1700000050000L)
        assertEquals(1700000050000L, prefManager.getLastRecoveryTimestamp())

        // Test lifecycle state
        prefManager.setLastKnownLifecycleState("RECOVERED_AFTER_KILL")
        assertEquals("RECOVERED_AFTER_KILL", prefManager.getLastKnownLifecycleState())

        // Test desired service state
        prefManager.setServiceDesiredEnabled(true)
        assertTrue(prefManager.isServiceDesiredEnabled())
        prefManager.setServiceDesiredEnabled(false)
        assertFalse(prefManager.isServiceDesiredEnabled())
    }
}
