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

// Later we replace this with a real repository that talks to our server.
interface ProductRepository {
    suspend fun getProducts(): List<Product>
    suspend fun getPriceHistory(productId: String): List<PricePoint> = emptyList()
}

class FakeProductRepository : ProductRepository {
    override suspend fun getProducts(): List<Product> = listOf(
        Product("1", "Logitech G413 Mechanical Gaming Keyboard", "Logitech", "Keyboard", "sigma", 2450, 2900, null, true, "https://www.sigma-computer.com/en"),
        Product("2", "Redragon K552 Kumara RGB Keyboard", "Redragon", "Keyboard", "elnour", 1350, 1600, null, true, "https://elnour-tech.com/en/"),
        Product("3", "Razer DeathAdder Essential Mouse", "Razer", "Mouse", "compumarts", 990, null, null, true, "https://www.compumarts.com/"),
        Product("4", "Samsung 980 NVMe SSD 1TB", "Samsung", "SSD", "elbadr", 3600, 4300, null, true, "https://elbadrgroupeg.store/"),
        Product("5", "Kingston NV2 NVMe SSD 1TB", "Kingston", "SSD", "maximum", 2950, 3400, null, false, "https://maximumhardware.store/"),
        Product("6", "MSI GeForce RTX 4060 Ventus 2X 8GB", "MSI", "Graphics Card", "sigma", 19800, 22500, null, true, "https://www.sigma-computer.com/en"),
        Product("7", "Gigabyte RTX 4060 Windforce OC 8GB", "Gigabyte", "Graphics Card", "elnour", 19400, null, null, true, "https://elnour-tech.com/en/"),
        Product("8", "AMD Ryzen 5 5600 Processor", "AMD", "CPU", "compumarts", 5900, 6700, null, true, "https://www.compumarts.com/"),
        Product("9", "Corsair Vengeance 16GB DDR4 3200MHz", "Corsair", "RAM", "elbadr", 1750, 2100, null, true, "https://elbadrgroupeg.store/"),
        Product("10", "HyperX Cloud Stinger Gaming Headset", "HyperX", "Headset", "maximum", 1850, 2200, null, true, "https://maximumhardware.store/")
    )
}
