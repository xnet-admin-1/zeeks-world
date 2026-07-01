package ngo.xnet.zeeksworld

import org.json.JSONObject

data class LatLon(val lat: Double, val lon: Double)
data class Building(val outline: List<LatLon>, val height: Double, val name: String)
data class Road(val points: List<LatLon>, val width: Double, val type: String)
data class Park(val outline: List<LatLon>, val name: String)
data class Water(val outline: List<LatLon>)
data class OsmData(
    val buildings: List<Building>,
    val roads: List<Road>,
    val parks: List<Park>,
    val water: List<Water>
)

object OsmFetcher {
    fun loadBundled(): OsmData {
        val json = OsmFetcher::class.java.getResourceAsStream("/meridian_osm.json")!!
            .bufferedReader().readText()
        return parseResponse(JSONObject(json))
    }

    private fun parseResponse(json: JSONObject): OsmData {
        val buildings = mutableListOf<Building>()
        val roads = mutableListOf<Road>()
        val parks = mutableListOf<Park>()
        val water = mutableListOf<Water>()

        val elements = json.getJSONArray("elements")
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: continue
            val geom = el.optJSONArray("geometry") ?: continue
            val coords = (0 until geom.length()).map { j ->
                val n = geom.getJSONObject(j)
                LatLon(n.getDouble("lat"), n.getDouble("lon"))
            }

            when {
                tags.has("building") -> {
                    var h = 4.0
                    tags.optString("height", "").toDoubleOrNull()?.let { v ->
                        h = (v / 2.0).coerceIn(3.0, 5.0)
                    }
                    buildings += Building(coords, h, tags.optString("name", ""))
                }
                tags.has("highway") -> {
                    val type = tags.getString("highway")
                    roads += Road(coords, roadWidth(type), type)
                }
                tags.optString("leisure") == "park" ->
                    parks += Park(coords, tags.optString("name", ""))
                tags.optString("natural") == "water" ->
                    water += Water(coords)
            }
        }
        return OsmData(buildings, roads, parks, water)
    }

    private fun roadWidth(type: String): Double = when (type) {
        "motorway", "trunk" -> 4.0
        "primary", "secondary" -> 3.0
        "tertiary", "residential" -> 2.0
        else -> 1.0
    }
}
