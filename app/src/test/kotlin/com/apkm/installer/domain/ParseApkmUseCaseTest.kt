package com.apkm.installer.domain

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import app.cash.turbine.test
import com.apkm.installer.data.ApkmParser
import com.apkm.installer.domain.model.ApkmPackageInfo
import com.apkm.installer.domain.usecase.ParseApkmUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseApkmUseCaseTest {

    private val parser = mockk<ApkmParser>()
    private val useCase = ParseApkmUseCase(parser)

    @Test
    fun `invoke returns success when parser succeeds`() = runTest {
        val uri = mockk<Uri>()
        val expected = fakePackageInfo()
        every { parser.parse(uri) } returns expected

        val result = useCase(uri)

        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun `invoke returns failure when parser throws`() = runTest {
        val uri = mockk<Uri>()
        every { parser.parse(uri) } throws IllegalArgumentException("bad apkm")

        val result = useCase(uri)

        assertTrue(result.isFailure)
        assertEquals("bad apkm", result.exceptionOrNull()?.message)
    }

    private fun fakePackageInfo() = ApkmPackageInfo(
        appName = "Test App",
        packageName = "com.example.test",
        versionName = "2.0.0",
        versionCode = 200,
        icon = null,
        permissions = listOf("android.permission.INTERNET"),
        apkFiles = listOf("/cache/base.apk"),
        totalSizeBytes = 1_000_000,
    )
}
