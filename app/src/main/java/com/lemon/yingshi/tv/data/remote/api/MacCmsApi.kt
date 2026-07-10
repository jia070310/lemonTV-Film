package com.lemon.yingshi.tv.data.remote.api

import com.lemon.yingshi.tv.data.remote.model.MacCmsListResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface MacCmsApi {

    @GET
    suspend fun fetchVodList(@Url url: String): Response<MacCmsListResponse>
}
