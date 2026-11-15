/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:Suppress("UnstableApi")

package moe.rukamori.archivetune.ui.player.canvas.network

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import moe.rukamori.archivetune.di.CanvasCache
import okhttp3.OkHttpClient

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface CanvasArtworkPlayerEntryPoint {
    @CanvasCache
    fun canvasCache(): Cache
}

internal fun resolveCanvasCache(context: Context): Cache? =
    runCatching {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            CanvasArtworkPlayerEntryPoint::class.java,
        ).canvasCache()
    }.getOrNull()

internal fun createOkHttpClient(): OkHttpClient =
    moe.rukamori.archivetune.network.createMediaHttpClient()

internal fun createCanvasMediaSourceFactory(
    context: Context,
    okHttpClient: OkHttpClient,
    canvasCache: Cache?,
): MediaSource.Factory {
    val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
    val upstreamFactory =
        if (canvasCache != null) {
            CacheDataSource
                .Factory()
                .setCache(canvasCache)
                .setCacheKeyFactory { dataSpec ->
                    dataSpec.key ?: dataSpec.uri.buildUpon().clearQuery().build().toString()
                }
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            httpDataSourceFactory
        }
    return DefaultMediaSourceFactory(
        DefaultDataSource.Factory(
            context,
            upstreamFactory,
        ),
    )
}
