package com.pricehunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.google.gson.JsonParseException
import retrofit2.HttpException

private fun Exception.userMessage(): String = when (this) {
    is HttpException -> "server returned HTTP ${code()}"
    is JsonParseException ->
        "server returned an invalid response; check whether Vercel deployment protection is enabled"
    else -> localizedMessage ?: javaClass.simpleName
}

data class Filters(
    val stores: Set<String> = emptySet(),
    val brands: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val maxPrice: Int? = null,
    val onlyDeals: Boolean = false,
    val inStockOnly: Boolean = false
)

enum class Sort(val label: String) {
    BEST_DEAL("Best deal"),
    PRICE_LOW("Price: low to high"),
    PRICE_HIGH("Price: high to low")
}

data class PriceHistoryState(
    val productId: String? = null,
    val points: List<PricePoint> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class RefreshState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

data class ProductLoadState(val error: String? = null)

class ProductsViewModel(
    private val repo: ProductRepository = RemoteProductRepository()
) : ViewModel() {

    private val _all = MutableStateFlow<List<Product>>(emptyList())
    val all: StateFlow<List<Product>> = _all.asStateFlow()

    private val _productLoadState = MutableStateFlow(ProductLoadState())
    val productLoadState: StateFlow<ProductLoadState> = _productLoadState.asStateFlow()
    val query = MutableStateFlow("")
    val filters = MutableStateFlow(Filters())
    val sort = MutableStateFlow(Sort.BEST_DEAL)
    private val _priceHistory = MutableStateFlow(PriceHistoryState())
    val priceHistory: StateFlow<PriceHistoryState> = _priceHistory.asStateFlow()
    private val _refreshState = MutableStateFlow(RefreshState())
    val refreshState: StateFlow<RefreshState> = _refreshState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _all.value = repo.getProducts()
                _productLoadState.value = ProductLoadState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PriceHunter", "Unable to load products", e)
                _productLoadState.value = ProductLoadState(
                    error = "Couldn't load products: ${e.userMessage()}"
                )
            }
        }
    }

    val results: StateFlow<List<Product>> =
        combine(_all, query, filters, sort) { list, q, f, s ->
            val words = q.trim().lowercase().split(" ").filter { it.isNotBlank() }
            list.filter { p ->
                val text = "${p.name} ${p.brand} ${p.category} ${storeName(p.storeId)} ${p.storeId}".lowercase()
                words.all { it in text } &&
                    (f.stores.isEmpty() || p.storeId in f.stores) &&
                    (f.brands.isEmpty() || p.brand in f.brands) &&
                    (f.categories.isEmpty() || p.category in f.categories) &&
                    (f.maxPrice == null || p.price <= f.maxPrice) &&
                    (!f.onlyDeals || (p.discountPercent ?: 0) > 0) &&
                    (!f.inStockOnly || p.inStock)
            }.let { filtered ->
                when (s) {
                    Sort.BEST_DEAL -> filtered.sortedByDescending { it.discountPercent ?: 0 }
                    Sort.PRICE_LOW -> filtered.sortedBy { it.price }
                    Sort.PRICE_HIGH -> filtered.sortedByDescending { it.price }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadPriceHistory(productId: String) {
        _priceHistory.value = PriceHistoryState(productId = productId, isLoading = true)
        viewModelScope.launch {
            try {
                _priceHistory.value = PriceHistoryState(
                    productId = productId,
                    points = repo.getPriceHistory(productId)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PriceHunter", "Unable to load price history for $productId", e)
                _priceHistory.value = PriceHistoryState(
                    productId = productId,
                    error = "Price history is currently unavailable."
                )
            }
        }

    }

    fun refreshProducts() {
        if (_refreshState.value.isLoading) return
        _refreshState.value = RefreshState(isLoading = true)
        viewModelScope.launch {
            try {
                _all.value = repo.getProducts()
                _productLoadState.value = ProductLoadState()
                _refreshState.value = RefreshState(
                    message = "Loaded ${_all.value.size} saved products. Prices update every three hours."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PriceHunter", "Unable to reload products", e)
                _productLoadState.value = ProductLoadState(
                    error = "Couldn't load products: ${e.userMessage()}"
                )
                _refreshState.value = RefreshState(
                    error = "Unable to reload products: ${e.userMessage()}"
                )
            }
        }
    }
}
