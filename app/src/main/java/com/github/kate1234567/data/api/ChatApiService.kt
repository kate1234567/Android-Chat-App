package com.github.kate1234567.data.api

import com.github.kate1234567.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ChatApiService {

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<String>

    @GET("channels")
    suspend fun getChannels(@Header("X-Auth-Token") token: String): Response<List<String>>

    @GET("channel/{channel}")
    suspend fun getMessages(
        @Header("X-Auth-Token") token: String,
        @Path("channel") channel: String,
        @Query("limit") limit: Int = 20,
        @Query("lastKnownId") lastKnownId: String = "0",
        @Query("reverse") reverse: Boolean = false
    ): Response<List<Message>>

    @POST("messages")
    suspend fun sendMessage(
        @Header("X-Auth-Token") token: String,
        @Body message: SendMessageRequest
    ): Response<String>

    @POST("logout")
    suspend fun logout(@Header("X-Auth-Token") token: String): Response<Unit>
}
