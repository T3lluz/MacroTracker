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
    fun advise(weather: WeatherInfo): ClothingAdvice {
        val temp = weather.temperature
        val wind = weather.windSpeed
        val desc = weather.description.lowercase()
        val hourlyDescs = weather.hourlyForecasts.take(12).map { it.description.lowercase() }
        val allDescs = listOf(desc) + hourlyDescs

        val hasRain = allDescs.any { it.contains("rain") || it.contains("shower") || it.contains("drizzle") }
        val hasSnow = allDescs.any { it.contains("snow") || it.contains("sleet") }
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
            temp <= 15 -> {
                headline = "Cool — medium jacket"
                detail = "A medium-weight jacket or fleece with a long-sleeve shirt should keep you comfortable."
                items[ClothingIcon.JACKET] = "Medium jacket"
                items[ClothingIcon.TSHIRT] = "Long sleeve"
            }
            temp <= 20 -> {
                headline = "Mild — light layer"
                detail = "A light jacket or cardigan works well over a t-shirt or light long-sleeve."
                items[ClothingIcon.JACKET] = "Light jacket"
                items[ClothingIcon.TSHIRT] = "T-shirt"
            }
            temp <= 25 -> {
                headline = "Warm — keep it light"
                detail = "Light clothing like a t-shirt and comfortable trousers or shorts will work well."
                items[ClothingIcon.TSHIRT] = "T-shirt"
                items[ClothingIcon.SHORTS] = "Light pants"
            }
            else -> {
                headline = "Hot — stay cool"
                detail = "Light, breathable clothing such as a t-shirt and shorts."
                items[ClothingIcon.TSHIRT] = "T-shirt"
                items[ClothingIcon.SHORTS] = "Shorts"
            }
        }

        if (isWindy && temp <= 15) items.putIfAbsent(ClothingIcon.WINDBREAKER, "Windproof layer")
        if (isWindy && temp > 15) items[ClothingIcon.WINDBREAKER] = "Windbreaker"
        if (hasRain) {
            items[ClothingIcon.UMBRELLA] = "Umbrella"
            items.putIfAbsent(ClothingIcon.JACKET, "Waterproof jacket")
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
            if (hasRain || hasSnow) add("Waterproof footwear helps today.")
            if (isWindy) add("It will feel cooler in the wind.")
        }
        val fullDetail = (listOf(detail) + extras).joinToString(" ")

        return ClothingAdvice(
            headline = headline,
            detail = fullDetail,
            items = items.map { (icon, label) -> ClothingItem(icon, label) },
        )
    }
}
