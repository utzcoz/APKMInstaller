package com.apkm.installer

import android.app.Application
import com.apkm.installer.data.SplitApkInstaller
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ApkMInstallerApp : Application() {

    /**
     * Exposed so that [com.apkm.installer.data.InstallResultReceiver] – which cannot
     * use Hilt field injection via manifest registration – can reach the installer.
     */
    @Inject
    lateinit var splitApkInstaller: SplitApkInstaller
}
