package app.awavenue.c001apk.data

import app.awavenue.c001apk.model.Entity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One variant of a product, as listed by the product detail's `configRows`. */
data class ProductConfigInfo(
    val id: String,
    val productId: String?,
    val title: String,
    val price: String?,
    val url: String?,
    val isAddCompare: Boolean,
) {
    companion object {
        fun from(entity: Entity): ProductConfigInfo? {
            val id = entity.string("id").trim()
            if (id.isEmpty()) return null
            return ProductConfigInfo(
                id = id,
                productId = entity.string("product_id").trim().ifEmpty { null },
                title = entity.string("title").trim().ifEmpty { "默认配置" },
                price = entity.string("price").trim().ifEmpty { null },
                url = entity.string("url").trim().ifEmpty { null },
                isAddCompare = entity.bool("is_add_compare"),
            )
        }
    }
}

data class ProductComparisonSelection(
    val productTitle: String,
    val productLogo: String?,
    val config: ProductConfigInfo,
)

enum class ToggleResult {
    ADDED,
    REMOVED,
    LIMIT_REACHED,
    INVALID,
}

/**
 * The cross-product comparison list. It is deliberately process-wide: a selection made on one
 * product page has to survive going back and opening another product.
 */
class ProductComparisonStore {
    private val _selections = MutableStateFlow<List<ProductComparisonSelection>>(emptyList())
    val selections: StateFlow<List<ProductComparisonSelection>> = _selections.asStateFlow()

    fun toggle(selection: ProductComparisonSelection): ToggleResult {
        val configId = selection.config.id
        if (configId.isBlank()) return ToggleResult.INVALID
        val current = _selections.value
        if (current.any { it.config.id == configId }) {
            _selections.value = current.filterNot { it.config.id == configId }
            return ToggleResult.REMOVED
        }
        if (current.size >= MAX_SELECTIONS) return ToggleResult.LIMIT_REACHED
        _selections.value = current + selection
        return ToggleResult.ADDED
    }

    fun remove(configId: String) {
        _selections.value = _selections.value.filterNot { it.config.id == configId }
    }

    companion object {
        const val MAX_SELECTIONS = 10
    }
}
