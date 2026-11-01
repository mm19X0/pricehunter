package com.pricehunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

class ProductsViewModel(
    private val repo: ProductRepository = FakeProductRepository()
) : ViewModel() {

    private val _all = MutableStateFlow<List<Product>>(emptyList())
    val all: StateFlow<List<Product>> = _all.asStateFlow()

    val query = MutableStateFlow("")
    val filters = MutableStateFlow(Filters())
    val sort = MutableStateFlow(Sort.BEST_DEAL)

    init {
        viewModelScope.launch { _all.value = repo.getProducts() }
    }

    val results: StateFlow<List<Product>> =
        combine(_all, query, filters, sort) { list, q, f, s ->
            val words = q.trim().lowercase().split(" ").filter { it.isNotBlank() }
            list.filter { p ->
                val text = "${p.name} ${p.brand} ${p.category}".lowercase()
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
}
