package com.pricehunter.app

import android.util.Log
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

object ApiConfig {
    // Android emulator -> your PC:        http://10.0.2.2:8000/
    // Real phone on the same Wi-Fi:       http://YOUR_PC_IP:8000/   (example: http://192.168.1.20:8000/)
    // After deploying the server online:  https://your-domain.com/
    const val BASE_URL = "http://192.168.1.20:8000/"
}

interface PriceApi {
    @GET("products")
    suspend fun products(): List<Product>
}

class RemoteProductRepository : ProductRepository {
    private val api: PriceApi = Retrofit.Builder()
        .baseUrl(ApiConfig.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(PriceApi::class.java)

    override suspend fun getProducts(): List<Product> = try {
        api.products()
    } catch (e: Exception) {
        // Server not reachable: show sample data so the app still opens.
        Log.w("PriceHunter", "Server unreachable, using sample data", e)
        FakeProductRepository().getProducts()
    }
}
