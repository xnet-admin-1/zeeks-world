package ngo.xnet.zeeksworld

import org.json.JSONObject

object GeoapifyEnricher {
    data class PlaceInfo(
        val name: String,
        val categories: List<String>,
        val lat: Double,
        val lon: Double
    )

    fun enrichWorld(world: World, centerLat: Double, centerLon: Double) {
        val json = GeoapifyEnricher::class.java.getResourceAsStream("/meridian_places.json")
            ?.bufferedReader()?.readText() ?: return
        val features = JSONObject(json).optJSONArray("features") ?: return

        val metersPerDegLat = 111320.0
        val metersPerDegLon = metersPerDegLat * Math.cos(Math.toRadians(centerLat))

        val places = mutableListOf<PlaceInfo>()
        for (i in 0 until features.length()) {
            val props = features.getJSONObject(i).getJSONObject("properties")
            places.add(PlaceInfo(
                name = props.optString("name", ""),
                categories = props.optJSONArray("categories")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                lat = props.optDouble("lat", 0.0),
                lon = props.optDouble("lon", 0.0)
            ))
        }

        println("Geoapify: enriching with ${places.size} places")
        for (place in places) {
            if (place.name.isEmpty()) continue
            val bx = ((place.lon - centerLon) * metersPerDegLon).toInt()
            val bz = ((place.lat - centerLat) * metersPerDegLat).toInt()

            val block = when {
                place.categories.any { "catering" in it } -> Block.RUBY
                place.categories.any { "commercial" in it } -> Block.AMETHYST
                place.categories.any { "leisure" in it } -> Block.GLOW
                place.categories.any { "education" in it } -> Block.CLOUD
                else -> Block.GLOW
            }

            for (y in 1..3) {
                world.setBlock(bx, y, bz, block)
            }
            println("  📍 ${place.name} at ($bx, $bz)")
        }
    }
}
