package com.pricehunter.app

data class Store(val id: String, val name: String, val url: String)

data class Product(
    val id: String,
    val name: String,
    val brand: String,
    val category: String,
    val storeId: String,
    val price: Int,
    val oldPrice: Int?,
    val imageUrl: String?,
    val inStock: Boolean,
    val url: String,
    val specifications: Map<String, String>? = null
) {
    val discountPercent: Int?
        get() = oldPrice?.takeIf { it > price }?.let { (it - price) * 100 / it }
}

data class PricePoint(val price: Int, val ts: Long)

val stores = listOf(
    Store("sigma", "Sigma Computer", "https://www.sigma-computer.com/en"),
    Store("elnour", "El Nour Tech", "https://elnour-tech.com/en/"),
    Store("compumarts", "CompuMarts", "https://www.compumarts.com/"),
    Store("elbadr", "El Badr Group", "https://elbadrgroupeg.store/"),
    Store("maximum", "Maximum Hardware", "https://maximumhardware.store/")
)

fun storeName(id: String) = stores.firstOrNull { it.id == id }?.name ?: id

interface ProductRepository {
    suspend fun getProducts(): List<Product>
    suspend fun getPriceHistory(productId: String): List<PricePoint> = emptyList()
}
