package com.pricehunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

private fun Set<String>.toggle(x: String) = if (x in this) this - x else this + x

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: ProductsViewModel) {
    val results by vm.results.collectAsState()
    val all by vm.all.collectAsState()
    val query by vm.query.collectAsState()
    val filters by vm.filters.collectAsState()
    val sort by vm.sort.collectAsState()
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("PriceHunter", fontWeight = FontWeight.Bold) })
        }
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(pad)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search keyboard, GPU, SSD...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            AssistChip(onClick = { showFilters = true }, label = { Text("Filters") })
                        }
                        items(stores) { s ->
                            FilterChip(
                                selected = s.id in filters.stores,
                                onClick = { vm.filters.value = filters.copy(stores = filters.stores.toggle(s.id)) },
                                label = { Text(s.name) }
                            )
                        }
                    }
                    Text(
                        "${results.size} products",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(results, key = { it.id }) { ProductCard(it) }
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
        Text("Sort by", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sort.values().forEach {
                FilterChip(selected = sort == it, onClick = { onSort(it) }, label = { Text(it.label) })
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
            Text("In stock only", Modifier.weight(1f))
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
fun ProductCard(p: Product) {
    val uriHandler = LocalUriHandler.current
    Card(
        onClick = { uriHandler.openUri(p.url) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = p.imageUrl,
                contentDescription = p.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(12.dp)
            )
            p.discountPercent?.let {
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
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(storeName(p.storeId), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("%,d EGP".format(p.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            p.oldPrice?.let {
                Text(
                    "%,d EGP".format(it),
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!p.inStock) {
                Text("Out of stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
