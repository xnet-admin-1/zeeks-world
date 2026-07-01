package ngo.xnet.zeeksworld

import de.fabmax.kool.scene.OrbitInputTransform
import de.fabmax.kool.math.deg
import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.InputStack
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.UniversalKeyCode
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ksl.KslPbrShader
import de.fabmax.kool.scene.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Time
import kotlin.math.cos
import kotlin.math.sin

class ZekesGame {
    val world = World()
    var oliverGreeted = false
    var btnForward = false; var btnBack = false; var btnLeft = false; var btnRight = false; var btnJump = false
    var btnPlace = false; var btnDestroy = false; var btnSprint = false

    private val palette = arrayOf(Block.GRASS, Block.DIRT, Block.STONE, Block.WOOD, Block.SAND, Block.AMETHYST)
    var selectedSlot = 0

    // Gravity state
    private var velocityY = 0f
    private var onGround = false
    private val GRAVITY = -20f
    private val JUMP_VELOCITY = 10f

    // Oliver dialogue
    private var lastOliverSpeakTime = 0.0
    private var lastChunkX = Int.MAX_VALUE
    private var lastChunkZ = Int.MAX_VALUE
    private val oliverEvents = listOf(
        "Zeek found a new area! Comment on what you see.",
        "Zeek has been walking for a while. Say something encouraging!",
        "It's getting dark! Say something about nighttime.",
        "The sun is coming up! Greet the morning.",
        "Zeek placed a block! React with excitement.",
        "Zeek broke a block! React playfully."
    )

    var onSpeech: ((String) -> Unit)? = null

    fun createScenes(ctx: KoolContext): List<Scene> {
        val lat = 43.6057601
        val lon = -116.3932135

        world.generateFlat(100)
        val osmData = OsmFetcher.loadBundled()
        WorldGenerator.generate(osmData, lat, lon, world)
        GeoapifyEnricher.enrichWorld(world, lat, lon)

        val mainScene = scene {
            val keys = mutableSetOf<Int>()
            val inputHandler = InputStack.InputHandler("fly-cam")
            inputHandler.keyboardListeners += InputStack.KeyboardListener { keyEvents, _ ->
                for (ev in keyEvents) {
                    val code = ev.keyCode.code
                    if (ev.isPressed || ev.isRepeated) keys += code
                    if (ev.isReleased) keys -= code
                }
            }
            InputStack.pushTop(inputHandler)

            val orbit = orbitCamera {
                setRotation(20f, -30f)
                setZoom(40.0)
                setTranslation(0f, 5f, 0f)
                rightDragMethod = OrbitInputTransform.DragMethod.NONE
                middleDragMethod = OrbitInputTransform.DragMethod.NONE
            }

            onUpdate {
                val dt = Time.deltaT
                val sprinting = btnSprint || KEY_SHIFT in keys
                val speed = (if (sprinting) 16f else 8f) * dt
                val yawRad = Math.toRadians(orbit.verticalRotation.toDouble()).toFloat()
                val fwdX = -sin(yawRad)
                val fwdZ = -cos(yawRad)
                val rightX = cos(yawRad)
                val rightZ = -sin(yawRad)

                var dx = 0f; var dz = 0f
                if (KEY_W in keys) { dx += fwdX * speed; dz += fwdZ * speed }
                if (KEY_S in keys) { dx -= fwdX * speed; dz -= fwdZ * speed }
                if (KEY_A in keys) { dx -= rightX * speed; dz -= rightZ * speed }
                if (KEY_D in keys) { dx += rightX * speed; dz += rightZ * speed }

                if (btnForward) { dx += fwdX * speed; dz += fwdZ * speed }
                if (btnBack) { dx -= fwdX * speed; dz -= fwdZ * speed }
                if (btnLeft) { dx -= rightX * speed; dz -= rightZ * speed }
                if (btnRight) { dx += rightX * speed; dz += rightZ * speed }

                val t = orbit.translation
                val newX = (t.x + dx).toFloat()
                val newZ = (t.z + dz).toFloat()
                val curY = t.y.toFloat()

                // Horizontal collision: check if destination is passable at player height
                val feetY = curY.toInt()
                val headY = feetY + 1
                val blocked = world.getBlock(newX.toInt(), feetY, newZ.toInt()).solid ||
                              world.getBlock(newX.toInt(), headY, newZ.toInt()).solid
                val finalX: Float; val finalZ: Float
                if (blocked) {
                    finalX = t.x.toFloat(); finalZ = t.z.toFloat()
                } else {
                    finalX = newX; finalZ = newZ
                }

                // Gravity
                if ((btnJump || KEY_SPACE in keys) && onGround) {
                    velocityY = JUMP_VELOCITY
                    onGround = false
                }
                velocityY += GRAVITY * dt
                var newY = curY + velocityY * dt

                // Ground check at current XZ - scan from player's feet down
                var groundY = 0f
                for (y in curY.toInt() downTo 0) {
                    if (world.getBlock(finalX.toInt(), y, finalZ.toInt()).solid) {
                        groundY = (y + 1).toFloat(); break
                    }
                }
                if (newY <= groundY) {
                    newY = groundY
                    velocityY = 0f
                    onGround = true
                } else {
                    onGround = false
                }

                orbit.setTranslation(finalX, newY, finalZ)

                // Block placement/destruction
                val targetX = (finalX + fwdX * 2f).toInt()
                val targetZ = (finalZ + fwdZ * 2f).toInt()

                if (KEY_Q in keys || btnDestroy) {
                    keys -= KEY_Q; btnDestroy = false
                    for (y in 15 downTo 0) {
                        if (world.getBlock(targetX, y, targetZ).solid) {
                            world.setBlock(targetX, y, targetZ, Block.AIR)
                            oliverSpeak(5)
                            break
                        }
                    }
                }
                if (KEY_E in keys || btnPlace) {
                    keys -= KEY_E; btnPlace = false
                    var placeY = 0
                    for (y in 15 downTo 0) {
                        if (world.getBlock(targetX, y, targetZ).solid) { placeY = y + 1; break }
                    }
                    if (placeY < 15) {
                        world.setBlock(targetX, placeY, targetZ, palette[selectedSlot])
                        oliverSpeak(4)
                    }
                }

                for (i in 0..5) {
                    if (UniversalKeyCode('1' + i).code in keys) { selectedSlot = i; break }
                }

                // Oliver: new area dialogue
                val chunkX = finalX.toInt() / 16
                val chunkZ = finalZ.toInt() / 16
                if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                    lastChunkX = chunkX; lastChunkZ = chunkZ
                    oliverSpeak(0)
                }
            }

            lighting.singleDirectionalLight {
                setup(Vec3f(-1f, -2f, -1f))
                setColor(Color.WHITE, 5f)
            }

            // Per-chunk meshes
            val chunkMeshes = mutableMapOf<ChunkPos, ColorMesh>()
            val pbrShader = KslPbrShader {
                color { vertexColor() }
                metallic(0f)
                roughness(0.25f)
            }

            // Initial build
            for ((pos, chunk) in world.chunks) {
                val mesh = addColorMesh {
                    generate { ChunkMesher.buildGeometry(chunk, pos, world, this) }
                    shader = pbrShader
                }
                chunk.dirty = false
                chunkMeshes[pos] = mesh
            }

            // Rebuild only dirty chunks
            onUpdate {
                for ((pos, chunk) in world.chunks) {
                    if (!chunk.dirty) continue
                    chunk.dirty = false
                    val existing = chunkMeshes[pos]
                    if (existing != null) {
                        existing.generate { ChunkMesher.buildGeometry(chunk, pos, world, this) }
                    } else {
                        val mesh = addColorMesh {
                            generate { ChunkMesher.buildGeometry(chunk, pos, world, this) }
                            shader = pbrShader
                        }
                        chunkMeshes[pos] = mesh
                    }
                }
            }

            val playerMesh = addColorMesh("player") {
                generate { }
                shader = KslPbrShader { color { vertexColor() } }
            }

            val oliverMesh = addColorMesh("oliver") {
                generate { }
                shader = KslPbrShader { color { vertexColor() } }
            }

            var lastPx = 0f; var lastPz = 0f; var lastPy = 0f; var walkPhase = 0f
            var lastPlayerSwing = -1f; var lastOliverSwing = -1f

            onUpdate {
                val t = orbit.translation
                val yaw = orbit.verticalRotation.toFloat()
                val px = t.x.toFloat(); val pz = t.z.toFloat(); val py = t.y.toFloat()

                val moved = (px - lastPx) * (px - lastPx) + (pz - lastPz) * (pz - lastPz) > 0.0001f ||
                    (py - lastPy) * (py - lastPy) > 0.0001f
                if (moved) walkPhase += Time.deltaT * 8f else walkPhase = 0f
                lastPx = px; lastPz = pz; lastPy = py
                val swing = if (moved) sin(walkPhase) * 0.4f else 0f

                // Only rebuild player mesh when swing changes meaningfully
                val quantizedSwing = (swing * 10f).toInt() / 10f
                if (quantizedSwing != lastPlayerSwing) {
                    lastPlayerSwing = quantizedSwing
                    val s = quantizedSwing
                    playerMesh.generate {
                        // Legs (blue)
                        color = Color(0.2f, 0.3f, 0.7f, 1f)
                        cube { origin.set(-0.175f, 0.5f + s * 0.5f, 0f); size.set(0.25f, 1f, 0.4f) }
                        cube { origin.set(0.175f, 0.5f - s * 0.5f, 0f); size.set(0.25f, 1f, 0.4f) }
                        // Torso (purple)
                        color = Color(0.5f, 0.2f, 0.8f, 1f)
                        cube { origin.set(0f, 1.6f, 0f); size.set(0.7f, 1.2f, 0.4f) }
                        // Arms (skin)
                        color = Color(0.9f, 0.7f, 0.5f, 1f)
                        cube { origin.set(-0.475f, 1.6f + (-s * 0.3f), 0f); size.set(0.25f, 1f, 0.4f) }
                        cube { origin.set(0.475f, 1.6f + (s * 0.3f), 0f); size.set(0.25f, 1f, 0.4f) }
                        // Head (skin)
                        cube { origin.set(0f, 2.5f, 0f); size.set(0.6f, 0.6f, 0.6f) }
                    }
                }
                playerMesh.transform.setIdentity()
                    .translate(px, py, pz)
                    .rotate(yaw.deg, Vec3f.Y_AXIS)

                val oRad = Math.toRadians((yaw + 150.0)).toFloat()
                val ox = px + sin(oRad) * 3f
                val oz = pz + cos(oRad) * 3f
                val oSwing = if (moved) sin(walkPhase * 1.3f) * 0.3f else 0f

                val quantizedOSwing = (oSwing * 10f).toInt() / 10f
                if (quantizedOSwing != lastOliverSwing) {
                    lastOliverSwing = quantizedOSwing
                    val os = quantizedOSwing
                    oliverMesh.generate {
                        // Body
                        color = Color(1f, 0.6f, 0.2f, 1f)
                        cube { origin.set(0f, 0.65f, 0f); size.set(0.6f, 0.5f, 1.0f) }
                        // Front legs
                        color = Color(1f, 0.55f, 0.15f, 1f)
                        cube { origin.set(-0.15f, 0.2f + os * 0.2f, -0.35f); size.set(0.15f, 0.4f, 0.15f) }
                        cube { origin.set(0.15f, 0.2f - os * 0.2f, -0.35f); size.set(0.15f, 0.4f, 0.15f) }
                        // Back legs
                        cube { origin.set(-0.15f, 0.2f - os * 0.2f, 0.35f); size.set(0.15f, 0.4f, 0.15f) }
                        cube { origin.set(0.15f, 0.2f + os * 0.2f, 0.35f); size.set(0.15f, 0.4f, 0.15f) }
                        // Head
                        color = Color(1f, 0.7f, 0.3f, 1f)
                        cube { origin.set(0f, 0.75f, -0.7f); size.set(0.4f, 0.4f, 0.4f) }
                        // Ears
                        color = Color(1f, 0.5f, 0.1f, 1f)
                        cube { origin.set(-0.1f, 1.025f, -0.7f); size.set(0.12f, 0.15f, 0.1f) }
                        cube { origin.set(0.1f, 1.025f, -0.7f); size.set(0.12f, 0.15f, 0.1f) }
                        // Tail
                        color = Color(1f, 0.6f, 0.2f, 1f)
                        cube { origin.set(0f, 0.7f, 0.7f); size.set(0.12f, 0.12f, 0.4f) }
                        cube { origin.set(0f, 0.82f, 0.95f); size.set(0.12f, 0.12f, 0.2f) }
                    }
                }
                oliverMesh.transform.setIdentity()
                    .translate(ox, py, oz)
                    .rotate(yaw.deg, Vec3f.Y_AXIS)

                // Initial greeting
                if (!oliverGreeted && Time.gameTime > 3.0) {
                    oliverGreeted = true
                    oliverSpeak("Zeek just started exploring the neighborhood! Greet him.")
                }

                // Idle dialogue (every 30s if not moving)
                if (!moved && Time.gameTime - lastOliverSpeakTime > 30.0) {
                    oliverSpeak(1)
                }
            }
        }

        return listOf(mainScene)
    }

    private fun oliverSpeak(eventIndex: Int) {
        if (Time.gameTime - lastOliverSpeakTime < 10.0) return
        lastOliverSpeakTime = Time.gameTime
        OliverLlm.generateResponse(oliverEvents[eventIndex]) { response ->
            onSpeech?.invoke(response)
        }
    }

    private fun oliverSpeak(prompt: String) {
        lastOliverSpeakTime = Time.gameTime
        OliverLlm.generateResponse(prompt) { response ->
            onSpeech?.invoke(response)
        }
    }

    companion object {
        private val KEY_W = UniversalKeyCode('W').code
        private val KEY_A = UniversalKeyCode('A').code
        private val KEY_S = UniversalKeyCode('S').code
        private val KEY_D = UniversalKeyCode('D').code
        private val KEY_Q = UniversalKeyCode('Q').code
        private val KEY_E = UniversalKeyCode('E').code
        private val KEY_SPACE = UniversalKeyCode(' ').code
        private val KEY_SHIFT = KeyboardInput.KEY_SHIFT_LEFT.code
    }
}
