package com.pricehunter.app

import android.util.Log
import kotlinx.coroutines.CancellationException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

private const val BASE_URL = "https://pricehunter-server-lifsk2n0r-mm-o19.vercel.app/"

object ApiConfig {
    const val REFRESH_API_KEY = "use-a-long-random-secret"
}

data class RefreshResponse(val started: Boolean)

interface PriceHunterApi {
    @GET("products")
    suspend fun products(@Query("limit") limit: Int, @Query("offset") offset: Int): List<Product>

    @GET("products/{productId}/history")
    suspend fun priceHistory(@Path("productId") productId: String): List<PricePoint>

    @POST("refresh")
    suspend fun refresh(@Header("X-API-Key") apiKey: String): RefreshResponse
}

object RetrofitClient {
    val api: PriceHunterApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PriceHunterApi::class.java)
    }
}

class RemoteProductRepository : ProductRepository {
    @Volatile
    var lastFailed = false

    override suspend fun getProducts(): List<Product> = try {
        val pageSize = 3000
        val products = mutableListOf<Product>()
        var offset = 0
        do {
            val page = RetrofitClient.api.products(pageSize, offset)
            products.addAll(page)
            offset += page.size
        } while (page.size == pageSize)
        products.also { lastFailed = false }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        lastFailed = true
        // Server not reachable: show sample data so the app still opens.
        Log.w("PriceHunter", "Server unreachable, using sample data", e)
        FakeProductRepository().getProducts()
    }

    override suspend fun getPriceHistory(productId: String): List<PricePoint> =
        RetrofitClient.api.priceHistory(productId)

    override suspend fun refresh(): RefreshResponse =
        RetrofitClient.api.refresh(ApiConfig.REFRESH_API_KEY)
}
