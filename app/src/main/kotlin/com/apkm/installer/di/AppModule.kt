package com.apkm.installer.di

import android.content.Context
import com.apkm.installer.data.ApkmParser
import com.apkm.installer.data.SplitApkInstaller
import com.apkm.installer.domain.usecase.InstallPackageUseCase
import com.apkm.installer.domain.usecase.ParseApkmUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideApkmParser(
        @ApplicationContext context: Context,
    ): ApkmParser = ApkmParser(context)

    @Provides
    @Singleton
    fun provideSplitApkInstaller(
        @ApplicationContext context: Context,
    ): SplitApkInstaller = SplitApkInstaller(context)

    @Provides
    fun provideParseApkmUseCase(parser: ApkmParser): ParseApkmUseCase = ParseApkmUseCase(parser)

    @Provides
    fun provideInstallPackageUseCase(installer: SplitApkInstaller): InstallPackageUseCase = InstallPackageUseCase(installer)
}
