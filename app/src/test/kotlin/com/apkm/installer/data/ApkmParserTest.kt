package com.apkm.installer.data

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkmParserTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var packageManager: PackageManager
    private lateinit var context: Context
    private lateinit var parser: ApkmParser

    @Before
    fun setUp() {
        packageManager = mockk(relaxed = true)
        context =
            mockk {
                every { packageManager } returns this@ApkmParserTest.packageManager
                every { cacheDir } returns tempFolder.root
            }
        parser = ApkmParser(context)
    }

    @Test
    fun `parse returns ApkmPackageInfo for valid apkm zip`() {
        val apkmBytes = buildApkmZip(listOf("base.apk", "split_config.arm64.apk"))
        val uri = mockUriReturning(apkmBytes)

        val appInfo = ApplicationInfo().apply { packageName = "com.example.testapp" }
        val fakePkgInfo =
            PackageInfo().apply {
                packageName = "com.example.testapp"
                versionName = "1.2.3"
                requestedPermissions = arrayOf("android.permission.INTERNET")
                applicationInfo = appInfo
            }
        every { packageManager.getPackageArchiveInfo(any(), any<Int>()) } returns fakePkgInfo

        val result = parser.parse(uri)

        assertEquals("com.example.testapp", result.packageName)
        assertEquals("1.2.3", result.versionName)
        assertEquals(2, result.apkFiles.size)
        assertEquals(listOf("android.permission.INTERNET"), result.permissions)
        assertTrue(result.totalSizeBytes > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse throws when zip contains no APK files`() {
        val apkmBytes = buildApkmZip(listOf("info.json"))
        val uri = mockUriReturning(apkmBytes)
        parser.parse(uri)
    }

    @Test
    fun `base_apk is sorted first in apkFiles`() {
        val apkmBytes = buildApkmZip(listOf("split_config.arm64.apk", "base.apk"))
        val uri = mockUriReturning(apkmBytes)

        val appInfo = ApplicationInfo().apply { packageName = "com.example.app" }
        val fakePkgInfo =
            PackageInfo().apply {
                packageName = "com.example.app"
                versionName = "1.0"
                applicationInfo = appInfo
            }
        every { packageManager.getPackageArchiveInfo(any(), any<Int>()) } returns fakePkgInfo

        val result = parser.parse(uri)

        assertTrue("base.apk should be first", result.apkFiles.first().endsWith("base.apk"))
    }

    // ──────────────────────── helpers ────────────────────────

    private fun buildApkmZip(entries: List<String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            entries.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write("fake content for $name".toByteArray())
                zip.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun mockUriReturning(bytes: ByteArray): Uri {
        val contentResolver =
            mockk<ContentResolver> {
                every { openInputStream(any()) } returns bytes.inputStream()
            }
        every { context.contentResolver } returns contentResolver
        return mockk()
    }
}
