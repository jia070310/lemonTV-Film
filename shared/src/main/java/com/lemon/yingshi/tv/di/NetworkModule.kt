package com.lemon.yingshi.tv.di

import com.lemon.yingshi.tv.data.remote.api.MacCmsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** 播放专用 OkHttp：无日志拦截，连接池 + 多路并发，供 ExoPlayer 缓冲复用 */
    @Provides
    @Singleton
    @Named("playback")
    fun providePlaybackOkHttpClient(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 8
            maxRequestsPerHost = 4
        }
        return OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .dispatcher(dispatcher)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** 离线下载专用 OkHttp：高并发连接池，供 HLS 分片多线程下载 */
    @Provides
    @Singleton
    @Named("offlineDownload")
    fun provideOfflineDownloadOkHttpClient(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 16
        }
        return OkHttpClient.Builder()
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .dispatcher(dispatcher)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    @Named("maccms")
    fun provideMacCmsRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideMacCmsApi(@Named("maccms") retrofit: Retrofit): MacCmsApi {
        return retrofit.create(MacCmsApi::class.java)
    }
}
