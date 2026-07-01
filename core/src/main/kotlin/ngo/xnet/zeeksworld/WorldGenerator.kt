package ngo.xnet.zeeksworld

import kotlin.math.*

object WorldGenerator {
    private const val WORLD_RADIUS = 100
    private const val METERS_PER_DEG_LAT = 111320.0

    fun generate(osm: OsmData, centerLat: Double, centerLon: Double, world: World) {
        val metersPerDegLon = METERS_PER_DEG_LAT * cos(centerLat * PI / 180)

        fun toBlock(ll: LatLon): Pair<Int, Int> {
            val dx = (ll.lon - centerLon) * metersPerDegLon
            val dz = (ll.lat - centerLat) * METERS_PER_DEG_LAT
            return dx.roundToInt() to dz.roundToInt()
        }

        fun inBounds(x: Int, z: Int) = x in -WORLD_RADIUS until WORLD_RADIUS && z in -WORLD_RADIUS until WORLD_RADIUS

        // Ground layer
        val groundRadius = ((WORLD_RADIUS / CHUNK_SIZE) + 1) * CHUNK_SIZE
        for (x in -groundRadius until groundRadius)
            for (z in -groundRadius until groundRadius)
                world.setBlock(x, 0, z, Block.GRASS)

        // Water (ground level)
        for (w in osm.water)
            fillPolygon(w.outline, ::toBlock, ::inBounds) { x, z -> world.setBlock(x, 0, z, Block.WATER) }

        // Parks (ground level)
        for (p in osm.parks) {
            fillPolygon(p.outline, ::toBlock, ::inBounds) { x, z -> world.setBlock(x, 0, z, Block.GRASS) }
            if (p.outline.isNotEmpty()) {
                val (bx, bz) = toBlock(p.outline[0])
                for (dx in intArrayOf(-5, 5))
                    for (dz in intArrayOf(-5, 5))
                        if (inBounds(bx + dx, bz + dz)) placeTree(world, bx + dx, bz + dz)
            }
        }

        // Roads (ground level)
        for (r in osm.roads) {
            val halfW = ceil(r.width / 2).toInt()
            for (i in 0 until r.points.size - 1) {
                val (x0, z0) = toBlock(r.points[i])
                val (x1, z1) = toBlock(r.points[i + 1])
                plotLine(x0, z0, x1, z1) { x, z ->
                    for (dx in -halfW..halfW)
                        for (dz in -halfW..halfW)
                            if (inBounds(x + dx, z + dz)) world.setBlock(x + dx, 0, z + dz, Block.STONE)
                }
            }
        }

        // Buildings (walls start at y=1 on top of ground)
        for (b in osm.buildings) {
            val height = (b.height * 2.0).roundToInt().coerceIn(6, 15)

            // Build all walls first
            for (i in 0 until b.outline.size - 1) {
                val (x0, z0) = toBlock(b.outline[i])
                val (x1, z1) = toBlock(b.outline[i + 1])
                plotLine(x0, z0, x1, z1) { x, z ->
                    if (!inBounds(x, z)) return@plotLine
                    for (y in 1..height)
                        world.setBlock(x, y, z, if (y > 1 && y < height && x % 3 == 0) Block.GLASS else Block.STONE)
                }
            }

            // Carve door after walls are complete
            if (b.outline.size >= 2) {
                val (x0, z0) = toBlock(b.outline[0])
                val (x1, z1) = toBlock(b.outline[1])
                val wallPts = mutableListOf<Pair<Int, Int>>()
                plotLine(x0, z0, x1, z1) { x, z -> wallPts.add(x to z) }
                val mid = wallPts.size / 2
                val start = (mid - 2).coerceAtLeast(0)
                val end = (mid + 1).coerceAtMost(wallPts.size - 1)
                for (i in start..end) {
                    val (px, pz) = wallPts[i]
                    if (!inBounds(px, pz)) continue
                    for (y in 1..4) world.setBlock(px, y, pz, Block.AIR)
                }
            }

            // Floor at ground level
            fillPolygon(b.outline, ::toBlock, ::inBounds) { x, z -> world.setBlock(x, 0, z, Block.WOOD) }
        }

        // Trees
        val rng = java.util.Random(42)
        for (x in -groundRadius until groundRadius step 6) {
            for (z in -groundRadius until groundRadius step 6) {
                if (rng.nextFloat() > 0.12f) continue
                if (world.getBlock(x, 0, z) == Block.GRASS && world.getBlock(x, 1, z) == Block.AIR) {
                    placeTree(world, x, z)
                }
            }
        }

        // Bushes
        for (x in -groundRadius until groundRadius step 4) {
            for (z in -groundRadius until groundRadius step 4) {
                if (rng.nextFloat() > 0.18f) continue
                if (world.getBlock(x, 0, z) == Block.GRASS && world.getBlock(x, 1, z) == Block.AIR) {
                    world.setBlock(x, 1, z, Block.LEAF)
                }
            }
        }
    }

    private fun placeTree(world: World, x: Int, z: Int) {
        val trunkHeight = 5
        for (y in 1..trunkHeight) world.setBlock(x, y, z, Block.WOOD)
        for (dy in 0..2) for (dx in -2..2) for (dz in -2..2) {
            if (dx * dx + dz * dz <= 5) world.setBlock(x + dx, trunkHeight + 1 + dy, z + dz, Block.LEAF)
        }
    }

    private fun plotLine(x0: Int, z0: Int, x1: Int, z1: Int, fn: (Int, Int) -> Unit) {
        var cx = x0; var cz = z0
        val dx = abs(x1 - x0); val dz = abs(z1 - z0)
        val sx = if (x0 < x1) 1 else -1
        val sz = if (z0 < z1) 1 else -1
        var err = dx - dz
        while (true) {
            fn(cx, cz)
            if (cx == x1 && cz == z1) break
            val e2 = 2 * err
            if (e2 > -dz) { err -= dz; cx += sx }
            if (e2 < dx) { err += dx; cz += sz }
        }
    }

    private fun fillPolygon(
        outline: List<LatLon>,
        toBlock: (LatLon) -> Pair<Int, Int>,
        inBounds: (Int, Int) -> Boolean,
        fn: (Int, Int) -> Unit
    ) {
        if (outline.size < 3) return
        val pts = outline.map { toBlock(it) }
        val xs = pts.map { it.first }
        val zs = pts.map { it.second }
        for (z in zs.min()..zs.max())
            for (x in xs.min()..xs.max())
                if (inBounds(x, z) && pointInPolygon(x, z, pts)) fn(x, z)
    }

    private fun pointInPolygon(x: Int, z: Int, pts: List<Pair<Int, Int>>): Boolean {
        var inside = false
        var j = pts.size - 1
        for (i in pts.indices) {
            val (xi, zi) = pts[i]; val (xj, zj) = pts[j]
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi)
                inside = !inside
            j = i
        }
        return inside
    }
}
