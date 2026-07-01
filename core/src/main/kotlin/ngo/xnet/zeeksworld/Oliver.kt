package ngo.xnet.zeeksworld

import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import kotlin.math.sin
import kotlin.math.sqrt

class Oliver(startPos: Vec3f = Vec3f(3f, 1f, 3f)) {
    enum class State { IDLE, FOLLOWING, CELEBRATING, SLEEPING }

    val position = MutableVec3f(startPos)
    var state = State.IDLE
    private var time = 0f
    private var lastSpeakTime = -10f

    fun update(dt: Float, playerPos: Vec3f) {
        time += dt
        val dx = playerPos.x - position.x
        val dz = playerPos.z - position.z
        val dist = sqrt(dx * dx + dz * dz)

        state = if (dist > 3f) State.FOLLOWING else State.IDLE

        when (state) {
            State.FOLLOWING -> {
                val speed = 4f * dt
                val nx = dx / dist
                val nz = dz / dist
                position.x += nx * speed
                position.z += nz * speed
                position.y = playerPos.y + sin(time * 6f) * 0.15f
            }
            State.IDLE -> {
                position.y = playerPos.y + sin(time * 2f) * 0.2f
            }
            else -> {}
        }
    }

    fun shouldSpeak(): Boolean {
        if (time - lastSpeakTime >= 10f) {
            lastSpeakTime = time
            return true
        }
        return false
    }
}
