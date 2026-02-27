package com.apkm.installer.domain.usecase

import android.net.Uri
import com.apkm.installer.data.ApkmParser
import com.apkm.installer.domain.model.ApkmPackageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Parses a .apkm [Uri] and returns the extracted [ApkmPackageInfo].
 * Runs on [Dispatchers.IO] to avoid blocking the main thread.
 */
class ParseApkmUseCase @Inject constructor(
    private val parser: ApkmParser,
) {
    suspend operator fun invoke(uri: Uri): Result<ApkmPackageInfo> = withContext(Dispatchers.IO) {
        runCatching { parser.parse(uri) }
    }
}
