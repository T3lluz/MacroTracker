package com.macrotracker.data.remote

import com.macrotracker.R

enum class ClothingIcon(val iconRes: Int) {
    JACKET(R.drawable.ic_clothing_hoodie),
    TSHIRT(R.drawable.ic_clothing_tshirt),
    LAYERS(R.drawable.ic_clothing_tshirt),
    SHORTS(R.drawable.ic_clothing_pants),
    UMBRELLA(R.drawable.ic_clothing_umbrella),
    SUNGLASSES(R.drawable.ic_clothing_sunglasses),
    BOOTS(R.drawable.ic_clothing_boot),
    HAT(R.drawable.ic_clothing_beanie),
    SUN_HAT(R.drawable.ic_clothing_cap),
    GLOVES(R.drawable.ic_clothing_gloves),
    SCARF(R.drawable.ic_clothing_scarf),
    WINDBREAKER(R.drawable.ic_clothing_wind),
}

data class ClothingItem(
    val icon: ClothingIcon,
    val label: String,
)

data class ClothingAdvice(
    val headline: String,
    val detail: String,
    val items: List<ClothingItem>,
)

object ClothingAdvisor {
    /**
     * Near-term horizon for rain/snow clothing extras — looking too far ahead
     * (e.g. overnight showers) made daytime advice feel stale and overdressed.
     */
    private const val NEAR_TERM_HOURS = 3

    fun advise(weather: WeatherInfo): ClothingAdvice {
        val temp = comfortTemp(weather)
        val wind = weather.windSpeed
        val desc = weather.description.lowercase()
        val nearTermDescs = weather.hourlyForecasts
            .take(NEAR_TERM_HOURS)
            .map { it.description.lowercase() }
        val allNear = listOf(desc) + nearTermDescs

        val hasRain = allNear.any { it.contains("rain") || it.contains("shower") || it.contains("drizzle") }
        val hasSnow = allNear.any { it.contains("snow") || it.contains("sleet") }
        val isClear = desc.contains("clear") || desc.contains("fair")
        val isWindy = wind >= 8.0

        val items = linkedMapOf<ClothingIcon, String>()
        val headline: String
        val detail: String

        when {
            temp <= -10 -> {
                headline = "Bundle up — deep freeze"
                detail = "Heavy winter coat, insulated layers, thermal base, and warm boots."
                items[ClothingIcon.JACKET] = "Heavy coat"
                items[ClothingIcon.LAYERS] = "Thermals"
                items[ClothingIcon.BOOTS] = "Warm boots"
                items[ClothingIcon.HAT] = "Warm hat"
                items[ClothingIcon.GLOVES] = "Gloves"
                items[ClothingIcon.SCARF] = "Scarf"
            }
            temp <= 0 -> {
                headline = "Proper winter kit"
                detail = "Thick winter jacket, warm sweater, and insulated boots are essential."
                items[ClothingIcon.JACKET] = "Winter jacket"
                items[ClothingIcon.LAYERS] = "Sweater"
                items[ClothingIcon.BOOTS] = "Insulated boots"
                items[ClothingIcon.HAT] = "Hat"
                items[ClothingIcon.GLOVES] = "Gloves"
                items[ClothingIcon.SCARF] = "Scarf"
            }
            temp <= 5 -> {
                headline = "Cold — coat weather"
                detail = "Warm winter coat or heavy parka with layered clothing and sturdy boots."
                items[ClothingIcon.JACKET] = "Warm coat"
                items[ClothingIcon.LAYERS] = "Layers"
                items[ClothingIcon.BOOTS] = "Boots"
                items[ClothingIcon.HAT] = "Beanie"
                items[ClothingIcon.GLOVES] = "Gloves"
            }
            temp <= 10 -> {
                headline = "Chilly — jacket + layers"
                detail = "Warm jacket or insulated coat with a sweater or hoodie underneath."
                items[ClothingIcon.JACKET] = "Warm jacket"
                items[ClothingIcon.LAYERS] = "Sweater"
                if (temp <= 8) {
                    items[ClothingIcon.HAT] = "Beanie"
                    items[ClothingIcon.GLOVES] = "Gloves"
                }
            }
            temp <= 14 -> {
                headline = "Cool — light jacket"
                detail = "A light jacket or fleece over a long-sleeve shirt should keep you comfortable."
                items[ClothingIcon.JACKET] = "Light jacket"
                items[ClothingIcon.TSHIRT] = "Long sleeve"
            }
            temp <= 17 -> {
                headline = "Mild — long sleeve"
                detail = "A t-shirt or light long-sleeve is enough; bring a thin layer only if you'll be out late."
                items[ClothingIcon.TSHIRT] = "T-shirt"
                items[ClothingIcon.LAYERS] = "Optional layer"
            }
            temp <= 22 -> {
                headline = "Warm — keep it light"
                detail = "T-shirt weather. Light pants or shorts will feel comfortable."
                items[ClothingIcon.TSHIRT] = "T-shirt"
                items[ClothingIcon.SHORTS] = "Light pants"
            }
            temp <= 26 -> {
                headline = "Hot — stay cool"
                detail = "Light, breathable clothing such as a t-shirt and shorts."
                items[ClothingIcon.TSHIRT] = "T-shirt"
                items[ClothingIcon.SHORTS] = "Shorts"
            }
            else -> {
                headline = "Scorching — dress light"
                detail = "Stick to a t-shirt and shorts, and seek shade when you can."
                items[ClothingIcon.TSHIRT] = "T-shirt"
                items[ClothingIcon.SHORTS] = "Shorts"
            }
        }

        if (isWindy && temp <= 14) items.putIfAbsent(ClothingIcon.WINDBREAKER, "Windproof layer")
        if (isWindy && temp > 14 && temp <= 20) items[ClothingIcon.WINDBREAKER] = "Light windbreaker"
        if (hasRain) {
            items[ClothingIcon.UMBRELLA] = "Umbrella"
            // Only suggest a rain shell when it is cool enough that an outer layer makes sense,
            // or when no top-layer item is already recommended.
            if (temp <= 18) {
                items.putIfAbsent(ClothingIcon.JACKET, "Waterproof jacket")
            } else {
                items.putIfAbsent(ClothingIcon.WINDBREAKER, "Light rain shell")
            }
            items[ClothingIcon.BOOTS] = "Waterproof shoes"
        }
        if (hasSnow && !hasRain) {
            items.putIfAbsent(ClothingIcon.JACKET, "Waterproof outerwear")
            items[ClothingIcon.BOOTS] = "Waterproof boots"
            items.putIfAbsent(ClothingIcon.SCARF, "Warm layers")
        }
        if (isClear && temp > 15) items[ClothingIcon.SUNGLASSES] = "Sunglasses"
        if (isClear && temp > 22) items[ClothingIcon.SUN_HAT] = "Sun hat"

        val extras = buildList {
            if (hasRain || hasSnow) add("Rain is nearby — waterproof footwear helps.")
            if (isWindy && temp <= 20) add("It will feel cooler in the wind.")
        }
        val fullDetail = (listOf(detail) + extras).joinToString(" ")

        return ClothingAdvice(
            headline = headline,
            detail = fullDetail,
            items = items.map { (icon, label) -> ClothingItem(icon, label) },
        )
    }

    /** Air temp adjusted slightly for wind so advice tracks how it feels outdoors. */
    private fun comfortTemp(weather: WeatherInfo): Double {
        var t = weather.temperature
        if (weather.windSpeed >= 5.0 && t < 18.0) {
            t -= ((weather.windSpeed - 4.0).coerceAtMost(5.0) * 0.35)
        }
        return t
    }
}
