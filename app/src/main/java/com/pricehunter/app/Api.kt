package com.pricehunter.app

import android.util.Log
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

object ApiConfig {
    const val BASE_URL = BuildConfig.API_BASE_URL
}

interface PriceApi {
    @GET("products")
    suspend fun products(@Query("limit") limit: Int, @Query("offset") offset: Int): List<Product>

    @GET("products/{productId}/history")
    suspend fun priceHistory(@Path("productId") productId: String): List<PricePoint>
}

class RemoteProductRepository : ProductRepository {
    private val api: PriceApi = Retrofit.Builder()
        .baseUrl(ApiConfig.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(PriceApi::class.java)

    @Volatile
    var lastFailed = false

    override suspend fun getProducts(): List<Product> = try {
        val pageSize = 3000
        val products = mutableListOf<Product>()
        var offset = 0
        do {
            val page = api.products(pageSize, offset)
            products.addAll(page)
            offset += page.size
        } while (page.size == pageSize)
        products.also { lastFailed = false }
    } catch (e: Exception) {
        lastFailed = true
        // Server not reachable: show sample data so the app still opens.
        Log.w("PriceHunter", "Server unreachable, using sample data", e)
        FakeProductRepository().getProducts()
    }

    override suspend fun getPriceHistory(productId: String): List<PricePoint> =
        api.priceHistory(productId)
}
