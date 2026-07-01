package ngo.xnet.zeeksworld

import de.fabmax.kool.KoolApplication
import de.fabmax.kool.KoolConfigJvm

fun main() = KoolApplication(
    config = KoolConfigJvm(
        windowTitle = "Zekes World"
    )
) {
    val game = ZekesGame()
    game.createScenes(ctx).forEach { ctx.scenes += it }
}
