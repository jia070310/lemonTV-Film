package com.lemon.yingshi.tv.data.remote.api

import com.lemon.yingshi.tv.data.remote.model.MacCmsListResponse
import com.lemon.yingshi.tv.data.remote.model.MacCmsRestVodDetailResponse
import com.lemon.yingshi.tv.data.remote.model.MacCmsRestVodListResponse
import com.lemon.yingshi.tv.data.remote.model.MacCmsTypeListResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface MacCmsApi {

    @GET
    suspend fun fetchVodList(@Url url: String): Response<MacCmsListResponse>

    @GET
    suspend fun fetchTypeList(@Url url: String): Response<MacCmsTypeListResponse>

    @GET
    suspend fun fetchRestVodDetail(@Url url: String): Response<MacCmsRestVodDetailResponse>

    @GET
    suspend fun fetchRestVodList(@Url url: String): Response<MacCmsRestVodListResponse>
}
