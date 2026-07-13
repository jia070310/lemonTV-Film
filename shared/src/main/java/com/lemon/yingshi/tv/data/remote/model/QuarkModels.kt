package com.lemon.yingshi.tv.data.remote.model

import com.google.gson.annotations.SerializedName

data class QuarkTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: Long,
    @SerializedName("token_type") val tokenType: String
)

data class QuarkFileResponse(
    @SerializedName("data") val data: QuarkFileListData?,
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?
)

data class QuarkFileListData(
    @SerializedName("list") val list: List<QuarkFileItem>?,
    @SerializedName("next_page_token") val nextPageToken: String?
)

data class QuarkFileItem(
    @SerializedName("fid") val fileId: String,
    @SerializedName("file_name") val fileName: String,
    @SerializedName("file_size") val fileSize: Long,
    @SerializedName("file_type") val fileType: String, // "file" or "folder"
    @SerializedName("pdir_fid") val parentId: String?,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("updated_at") val updatedAt: Long,
    @SerializedName("thumbnail") val thumbnail: String?
)
