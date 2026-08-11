package com.macrotracker.ui.screens.ai

import com.macrotracker.R

data class DishSuggestion(
    val label: String,
    /** Full query used when the chip sends immediately (empty-draft defaults). */
    val query: String,
    /** Fragment appended into the composer for contextual refinements. */
    val append: String = query,
    val iconRes: Int? = null,
    /** When true, tapping fills/sends a full query instead of refining the draft. */
    val replacesDraft: Boolean = false,
)

private data class DishFamily(
    val keywords: List<String>,
    val addons: List<DishSuggestion>,
)

private val defaultSuggestions = listOf(
    DishSuggestion("Avocado", "1 medium avocado", iconRes = R.drawable.ic_food_avocado, replacesDraft = true),
    DishSuggestion("Apple", "1 medium apple", iconRes = R.drawable.ic_food_apple, replacesDraft = true),
    DishSuggestion("Salad", "large garden salad with grilled chicken", iconRes = R.drawable.ic_food_salad, replacesDraft = true),
    DishSuggestion("Grain bowl", "chicken grain bowl with quinoa and veggies", iconRes = R.drawable.ic_food_bowl, replacesDraft = true),
    DishSuggestion("Eggs", "2 scrambled eggs with cheese", iconRes = R.drawable.ic_food_egg, replacesDraft = true),
)

private val families = listOf(
    DishFamily(
        keywords = listOf("burger", "hamburger", "cheeseburger", "smashburger"),
        addons = listOf(
            DishSuggestion("Bacon", "bacon", append = "bacon"),
            DishSuggestion("Cheddar", "cheddar cheese", append = "cheddar cheese"),
            DishSuggestion("Fries", "side of fries", append = "a side of fries"),
            DishSuggestion("Avocado", "avocado", append = "avocado", iconRes = R.drawable.ic_food_avocado),
            DishSuggestion("Double patty", "double patty", append = "a double patty"),
        ),
    ),
    DishFamily(
        keywords = listOf("pizza"),
        addons = listOf(
            DishSuggestion("Pepperoni", "pepperoni", append = "pepperoni"),
            DishSuggestion("Mushrooms", "mushrooms", append = "mushrooms"),
            DishSuggestion("Extra cheese", "extra cheese", append = "extra cheese"),
            DishSuggestion("Olives", "black olives", append = "black olives"),
        ),
    ),
    DishFamily(
        keywords = listOf("salad", "poke"),
        addons = listOf(
            DishSuggestion("Chicken", "grilled chicken", append = "grilled chicken"),
            DishSuggestion("Feta", "feta cheese", append = "feta"),
            DishSuggestion("Avocado", "avocado", append = "avocado", iconRes = R.drawable.ic_food_avocado),
            DishSuggestion("Egg", "boiled egg", append = "a boiled egg", iconRes = R.drawable.ic_food_egg),
            DishSuggestion("Olive oil", "olive oil dressing", append = "olive oil dressing"),
        ),
    ),
    DishFamily(
        keywords = listOf("sandwich", "wrap", "sub", "bagel", "toastie"),
        addons = listOf(
            DishSuggestion("Bacon", "bacon", append = "bacon"),
            DishSuggestion("Cheese", "cheese", append = "cheese"),
            DishSuggestion("Avocado", "avocado", append = "avocado", iconRes = R.drawable.ic_food_avocado),
            DishSuggestion("Chips", "a bag of chips", append = "a side of chips"),
        ),
    ),
    DishFamily(
        keywords = listOf("coffee", "latte", "cappuccino", "espresso", "americano", "mocha"),
        addons = listOf(
            DishSuggestion("Oat milk", "oat milk", append = "oat milk"),
            DishSuggestion("Extra shot", "extra espresso shot", append = "an extra shot"),
            DishSuggestion("Syrup", "vanilla syrup", append = "vanilla syrup"),
            DishSuggestion("Whipped cream", "whipped cream", append = "whipped cream"),
        ),
    ),
    DishFamily(
        keywords = listOf("egg", "eggs", "omelette", "omelet", "scramble", "breakfast"),
        addons = listOf(
            DishSuggestion("Cheese", "cheese", append = "cheese"),
            DishSuggestion("Bacon", "bacon", append = "bacon"),
            DishSuggestion("Toast", "toast", append = "toast"),
            DishSuggestion("Avocado", "avocado", append = "avocado", iconRes = R.drawable.ic_food_avocado),
        ),
    ),
    DishFamily(
        keywords = listOf("pasta", "spaghetti", "penne", "lasagna", "noodle"),
        addons = listOf(
            DishSuggestion("Meatballs", "meatballs", append = "meatballs"),
            DishSuggestion("Parmesan", "parmesan", append = "parmesan"),
            DishSuggestion("Garlic bread", "garlic bread", append = "garlic bread"),
            DishSuggestion("Chicken", "grilled chicken", append = "grilled chicken"),
        ),
    ),
    DishFamily(
        keywords = listOf("sushi", "ramen", "pho"),
        addons = listOf(
            DishSuggestion("Edamame", "edamame", append = "edamame"),
            DishSuggestion("Miso soup", "miso soup", append = "miso soup"),
            DishSuggestion("Extra fish", "extra fish", append = "extra fish"),
            DishSuggestion("Rice bowl", "extra rice", append = "extra rice"),
        ),
    ),
    DishFamily(
        keywords = listOf("taco", "burrito", "quesadilla", "nacho"),
        addons = listOf(
            DishSuggestion("Guac", "guacamole", append = "guacamole"),
            DishSuggestion("Sour cream", "sour cream", append = "sour cream"),
            DishSuggestion("Rice", "rice", append = "rice"),
            DishSuggestion("Beans", "black beans", append = "black beans"),
            DishSuggestion("Cheese", "cheese", append = "cheese"),
        ),
    ),
    DishFamily(
        keywords = listOf("steak", "ribeye", "sirloin"),
        addons = listOf(
            DishSuggestion("Mashed potatoes", "mashed potatoes", append = "mashed potatoes"),
            DishSuggestion("Asparagus", "asparagus", append = "asparagus"),
            DishSuggestion("Butter", "garlic butter", append = "garlic butter"),
            DishSuggestion("Fries", "fries", append = "fries"),
        ),
    ),
    DishFamily(
        keywords = listOf("chicken", "wings", "nugget"),
        addons = listOf(
            DishSuggestion("Rice", "rice", append = "rice", iconRes = R.drawable.ic_food_bowl),
            DishSuggestion("Broccoli", "broccoli", append = "broccoli"),
            DishSuggestion("Sauce", "bbq sauce", append = "bbq sauce"),
            DishSuggestion("Salad", "side salad", append = "a side salad", iconRes = R.drawable.ic_food_salad),
        ),
    ),
    DishFamily(
        keywords = listOf("oatmeal", "porridge", "muesli", "cereal"),
        addons = listOf(
            DishSuggestion("Banana", "banana", append = "banana"),
            DishSuggestion("Peanut butter", "peanut butter", append = "peanut butter"),
            DishSuggestion("Berries", "berries", append = "berries"),
            DishSuggestion("Honey", "honey", append = "honey"),
        ),
    ),
    DishFamily(
        keywords = listOf("smoothie", "shake", "protein shake"),
        addons = listOf(
            DishSuggestion("Protein", "protein powder", append = "protein powder"),
            DishSuggestion("Peanut butter", "peanut butter", append = "peanut butter"),
            DishSuggestion("Banana", "banana", append = "banana"),
            DishSuggestion("Oats", "oats", append = "oats"),
        ),
    ),
    DishFamily(
        keywords = listOf("yogurt", "yoghurt", "skyr"),
        addons = listOf(
            DishSuggestion("Granola", "granola", append = "granola"),
            DishSuggestion("Berries", "berries", append = "berries"),
            DishSuggestion("Honey", "honey", append = "honey"),
            DishSuggestion("Apple", "apple", append = "apple", iconRes = R.drawable.ic_food_apple),
        ),
    ),
    DishFamily(
        keywords = listOf("bowl", "rice bowl", "grain bowl", "buddha"),
        addons = listOf(
            DishSuggestion("Egg", "fried egg", append = "a fried egg", iconRes = R.drawable.ic_food_egg),
            DishSuggestion("Avocado", "avocado", append = "avocado", iconRes = R.drawable.ic_food_avocado),
            DishSuggestion("Chicken", "chicken", append = "chicken"),
            DishSuggestion("Tofu", "tofu", append = "tofu"),
        ),
    ),
    DishFamily(
        keywords = listOf("fries", "chips", "potato"),
        addons = listOf(
            DishSuggestion("Ketchup", "ketchup", append = "ketchup"),
            DishSuggestion("Mayo", "mayo", append = "mayo"),
            DishSuggestion("Cheese", "cheese sauce", append = "cheese sauce"),
            DishSuggestion("Burger", "with a burger", append = "a burger"),
        ),
    ),
    DishFamily(
        keywords = listOf("ice cream", "dessert", "cake", "cookie", "brownie"),
        addons = listOf(
            DishSuggestion("Chocolate", "chocolate sauce", append = "chocolate sauce"),
            DishSuggestion("Whipped cream", "whipped cream", append = "whipped cream"),
            DishSuggestion("Berries", "berries", append = "berries"),
            DishSuggestion("Nuts", "nuts", append = "nuts"),
        ),
    ),
)

/**
 * Suggestions for the composer: default starters when empty, otherwise
 * dish-family add-ons that match keywords in [draft].
 */
fun suggestionsForDraft(draft: String): List<DishSuggestion> {
    val trimmed = draft.trim()
    if (trimmed.isEmpty()) return defaultSuggestions

    val lower = trimmed.lowercase()
    val matched = linkedMapOf<String, DishSuggestion>()
    for (family in families) {
        if (family.keywords.any { keyword -> lower.contains(keyword) }) {
            for (addon in family.addons) {
                val alreadyInDraft = lower.contains(addon.append.lowercase()) ||
                    lower.contains(addon.label.lowercase())
                if (!alreadyInDraft) {
                    matched.putIfAbsent(addon.label.lowercase(), addon)
                }
            }
        }
    }
    return matched.values.take(6).toList()
}

/** Merge an add-on into the current draft in a natural “with …” phrase. */
fun refineDraft(draft: String, suggestion: DishSuggestion): String {
    if (suggestion.replacesDraft) return suggestion.query
    val base = draft.trim()
    if (base.isEmpty()) return suggestion.query
    val piece = suggestion.append.trim()
    if (piece.isEmpty()) return base
    if (base.contains(piece, ignoreCase = true)) return base
    val connector = if (base.contains(" with ", ignoreCase = true) || base.endsWith("with", true)) {
        ", "
    } else {
        " with "
    }
    return base.trimEnd(',', ' ') + connector + piece
}

fun suggestionStripTitle(draft: String): String {
    return if (draft.trim().isEmpty()) "Quick bites" else "Add to this dish"
}
