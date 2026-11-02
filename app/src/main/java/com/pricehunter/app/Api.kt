package com.pricehunter.app

import android.util.Log
import kotlinx.coroutines.CancellationException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

private const val BASE_URL = "https://pricehunter-server.vercel.app/"

interface PriceHunterApi {
    @GET("products")
    suspend fun products(@Query("limit") limit: Int, @Query("offset") offset: Int): List<Product>

    @GET("products/{productId}/history")
    suspend fun priceHistory(@Path("productId") productId: String): List<PricePoint>

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
    override suspend fun getProducts(): List<Product> = try {
        val pageSize = 3000
        val products = mutableListOf<Product>()
        var offset = 0
        do {
            val page = RetrofitClient.api.products(pageSize, offset)
            products.addAll(page)
            offset += page.size
        } while (page.size == pageSize)
        products
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("PriceHunter", "Unable to load products from server", e)
        throw e
    }

    override suspend fun getPriceHistory(productId: String): List<PricePoint> =
        RetrofitClient.api.priceHistory(productId)
}
