package com.pricehunter.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun Set<String>.toggle(x: String) = if (x in this) this - x else this + x

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: ProductsViewModel) {
    val results by vm.results.collectAsState()
    val all by vm.all.collectAsState()
    val query by vm.query.collectAsState()
    val filters by vm.filters.collectAsState()
    val sort by vm.sort.collectAsState()
    val productLoadState by vm.productLoadState.collectAsState()
    val history by vm.priceHistory.collectAsState()
    val refreshState by vm.refreshState.collectAsState()
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("pricehunter_local", Context.MODE_PRIVATE)
    }
    var favoriteIds by remember(preferences) {
        mutableStateOf(preferences.getStringSet("favorite_ids", emptySet()).orEmpty().toSet())
    }
    var showFilters by remember { mutableStateOf(false) }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var selectedTab by remember { mutableStateOf("Products") }
    val categories = all.map { it.category }.distinct().sorted()
    val activeFilterCount =
        (if (filters.stores.isNotEmpty()) 1 else 0) +
            (if (filters.brands.isNotEmpty()) 1 else 0) +
            (if (filters.categories.isNotEmpty()) 1 else 0) +
            (if (filters.maxPrice != null) 1 else 0) +
            (if (filters.onlyDeals) 1 else 0) +
            (if (filters.inStockOnly) 1 else 0)

    fun toggleFavorite(productId: String) {
        val updated = if (productId in favoriteIds) favoriteIds - productId else favoriteIds + productId
        favoriteIds = updated
        preferences.edit().putStringSet("favorite_ids", updated).apply()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("PriceHunter", fontWeight = FontWeight.Bold) })
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf("Products", "Categories", "Favorites").forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(if (tab == "Favorites") "★" else if (tab == "Categories") "▦" else "⌂") },
                        label = { Text(tab) }
                    )
                }
            }
        }
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(pad)
        ) {
            if (selectedTab == "Categories") {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Browse categories",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(categories) { category ->
                    Card(
                        onClick = {
                            vm.filters.value = filters.copy(categories = setOf(category))
                            selectedTab = "Products"
                        },
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("▦", color = MaterialTheme.colorScheme.primary)
                            Text(category, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${all.count { it.category == category }} products",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        productLoadState.error?.let { error ->
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(12.dp)
                            )
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { vm.query.value = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search products, brands, categories, stores...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                AssistChip(
                                    onClick = { showFilters = true },
                                    label = {
                                        Text(
                                            if (activeFilterCount > 0) "Filters ($activeFilterCount)"
                                            else "Filters"
                                        )
                                    }
                                )
                            }
                            items(stores) { store ->
                                FilterChip(
                                    selected = store.id in filters.stores,
                                    onClick = {
                                        vm.filters.value = filters.copy(stores = filters.stores.toggle(store.id))
                                    },
                                    label = { Text(store.name) }
                                )
                            }
                        }
                        Text(
                            "${results.size} products",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = vm::refreshProducts,
                                enabled = !refreshState.isLoading
                            ) {
                                if (refreshState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Reload saved prices")
                            }
                            refreshState.message?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            refreshState.error?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                val visibleResults = if (selectedTab == "Favorites") {
                    results.filter { it.id in favoriteIds }
                } else {
                    results
                }
                if (visibleResults.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (selectedTab == "Favorites") "Your favorites will appear here." else "No products found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                } else {
                    items(visibleResults, key = { it.id }) { product ->
                        ProductCard(
                            product = product,
                            isFavorite = product.id in favoriteIds,
                            onFavorite = { toggleFavorite(product.id) },
                            onClick = {
                                selectedProduct = product
                            }
                        )
                    }
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            FiltersSheet(
                all = all,
                filters = filters,
                sort = sort,
                resultCount = results.size,
                onFilters = { vm.filters.value = it },
                onSort = { vm.sort.value = it },
                onClose = { showFilters = false }
            )
        }
    }

    selectedProduct?.let { product ->
        ModalBottomSheet(onDismissRequest = { selectedProduct = null }) {
            ProductDetailsSheet(
                product = product,
                history = history.takeIf { it.productId == product.id } ?: PriceHistoryState(),
                similarProducts = all.filter {
                    it.id != product.id && it.category.equals(product.category, ignoreCase = true) &&
                        (it.discountPercent ?: 0) > 0
                }.sortedByDescending { it.discountPercent ?: 0 }.take(6),
                isFavorite = product.id in favoriteIds,
                onFavorite = { toggleFavorite(product.id) },
                onLoadHistory = vm::loadPriceHistory,
                onProductSelected = { selected ->
                    selectedProduct = selected
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltersSheet(
    all: List<Product>,
    filters: Filters,
    sort: Sort,
    resultCount: Int,
    onFilters: (Filters) -> Unit,
    onSort: (Sort) -> Unit,
    onClose: () -> Unit
) {
    val maxAll = (all.maxOfOrNull { it.price } ?: 1000).toFloat()
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Choose multiple options. Selections in different sections are combined.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Sort by", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sort.values().forEach {
                FilterChip(selected = sort == it, onClick = { onSort(it) }, label = { Text(it.label) })
            }
        }
        Text("Store", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stores.forEach { store ->
                FilterChip(
                    selected = store.id in filters.stores,
                    onClick = {
                        onFilters(filters.copy(stores = filters.stores.toggle(store.id)))
                    },
                    label = { Text(store.name) }
                )
            }
        }
        Text("Category", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            all.map { it.category }.distinct().sorted().forEach { c ->
                FilterChip(
                    selected = c in filters.categories,
                    onClick = { onFilters(filters.copy(categories = filters.categories.toggle(c))) },
                    label = { Text(c) }
                )
            }
        }
        Text("Brand", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            all.map { it.brand }.distinct().sorted().forEach { b ->
                FilterChip(
                    selected = b in filters.brands,
                    onClick = { onFilters(filters.copy(brands = filters.brands.toggle(b))) },
                    label = { Text(b) }
                )
            }
        }
        Text("Max price: ${(filters.maxPrice ?: maxAll.toInt())} EGP", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = (filters.maxPrice ?: maxAll.toInt()).toFloat(),
            onValueChange = { onFilters(filters.copy(maxPrice = it.toInt())) },
            valueRange = 0f..maxAll
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Only deals", Modifier.weight(1f))
            Switch(filters.onlyDeals, { onFilters(filters.copy(onlyDeals = it)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Available only (in stock)", Modifier.weight(1f))
            Switch(filters.inStockOnly, { onFilters(filters.copy(inStockOnly = it)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onFilters(Filters()) }, modifier = Modifier.weight(1f)) { Text("Reset") }
            Button(onClick = onClose, modifier = Modifier.weight(2f)) { Text("Show $resultCount results") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductCard(
    product: Product,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = product.imageUrl,
                contentDescription = product.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(12.dp)
            )
            product.discountPercent?.let {
                Text(
                    "-$it%",
                    color = MaterialTheme.colorScheme.onSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondary)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            TextButton(
                onClick = onFavorite,
                modifier = Modifier.align(Alignment.TopEnd),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(if (isFavorite) "★" else "☆", style = MaterialTheme.typography.titleLarge)
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(storeName(product.storeId), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(product.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("%,d EGP".format(product.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            product.oldPrice?.let {
                Text(
                    "%,d EGP".format(it),
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!product.inStock) {
                Text("Out of stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductDetailsSheet(
    product: Product,
    history: PriceHistoryState,
    similarProducts: List<Product>,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onLoadHistory: (String) -> Unit,
    onProductSelected: (Product) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(product.id) { onLoadHistory(product.id) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = product.imageUrl,
            contentDescription = product.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(product.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onFavorite) { Text(if (isFavorite) "★ Saved" else "☆ Save") }
        }
        Text("%,d EGP".format(product.price), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        product.discountPercent?.let { Text("$it% off", color = MaterialTheme.colorScheme.secondary) }
        DetailLine("Category", product.category)
        DetailLine("Brand", product.brand)
        DetailLine("Store", storeName(product.storeId))
        DetailLine("Availability", if (product.inStock) "In stock" else "Out of stock")
        val specifications = product.specifications.orEmpty()
        Text("Specifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (specifications.isEmpty()) {
            Text(
                "The store has not provided detailed specifications for this product.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            specifications.forEach { (name, value) -> DetailLine(name, value) }
        }

        Text("Price over time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        when {
            history.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            history.error != null -> Text(history.error, color = MaterialTheme.colorScheme.error)
            history.points.isEmpty() -> Text(
                "No price changes have been recorded yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> {
                PriceHistoryChart(history.points)
                history.points.sortedByDescending { it.ts }.forEach { point ->
                    DetailLine(
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(point.ts * 1000)),
                        "%,d EGP".format(point.price)
                    )
                }
            }
        }

        Button(onClick = { uriHandler.openUri(product.url) }, modifier = Modifier.fillMaxWidth()) {
            Text("Open product on ${storeName(product.storeId)}")
        }
        Text("Similar deals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (similarProducts.isEmpty()) {
            Text("No discounted products in this category right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                similarProducts.forEach { similar ->
                    AssistChip(
                        onClick = { onProductSelected(similar) },
                        label = { Text("${similar.name.take(28)} · %,d EGP".format(similar.price)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PriceHistoryChart(points: List<PricePoint>) {
    val sorted = points.sortedBy { it.ts }
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp)) {
        if (sorted.size < 2) return@Canvas
        val minPrice = sorted.minOf { it.price }.toFloat()
        val maxPrice = sorted.maxOf { it.price }.toFloat()
        val range = (maxPrice - minPrice).coerceAtLeast(1f)
        val path = Path()
        sorted.forEachIndexed { index, point ->
            val x = size.width * index / (sorted.size - 1)
            val y = size.height - (point.price - minPrice) / range * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
    }
}
