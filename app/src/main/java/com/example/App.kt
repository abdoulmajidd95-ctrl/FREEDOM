package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.isActive

/**
 * Game States for Space Dodger
 */
enum class GameState {
    START,
    PLAYING,
    GAME_OVER
}

/**
 * Type of dropped collectible bonuses
 */
enum class BonusType {
    SHIELD,
    EXTRA_POINTS
}

/**
 * Background star representation for parallax starfield
 */
data class Star(
    var x: Float,
    var y: Float,
    val speed: Float,
    val radius: Float,
    val alpha: Float,
    val isTwinkle: Boolean = false
)

/**
 * Asteroid entity with randomized polygonal vertices
 */
data class Asteroid(
    val id: Long,
    var x: Float,
    var y: Float,
    val radius: Float,
    val speedY: Float,
    val speedX: Float,
    var rotation: Float,
    val rotationSpeed: Float,
    val vertices: List<Offset>,
    val craterOffsets: List<Offset>,
    val baseColor: Color
)

/**
 * Collectible power-up entity
 */
data class BonusItem(
    val id: Long,
    var x: Float,
    var y: Float,
    val radius: Float,
    val speedY: Float,
    val type: BonusType,
    var pulsePhase: Float = 0f
)

/**
 * Visual particle effect for explosions, thrusters, and sparkles
 */
data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color,
    val radius: Float
)

/**
 * Floating score notification on bonus collect or asteroid dodge
 */
data class FloatingText(
    val id: Long,
    val text: String,
    var x: Float,
    var y: Float,
    var alpha: Float,
    val color: Color,
    var life: Float
)

/**
 * Single shared App composable for Compose Multiplatform / Android.
 * Contains the complete game loop, physics, input handling, canvas rendering,
 * and HUD / overlay states.
 */
@Composable
fun App() {
    var gameState by remember { mutableStateOf(GameState.START) }
    var score by remember { mutableIntStateOf(0) }
    var highScore by remember { mutableIntStateOf(0) }
    var dodgedCount by remember { mutableIntStateOf(0) }
    var gameDuration by remember { mutableFloatStateOf(0f) }

    // Spaceship state
    var shipX by remember { mutableFloatStateOf(0f) }
    var shipY by remember { mutableFloatStateOf(0f) }
    var shipVx by remember { mutableFloatStateOf(0f) }
    var shipTilt by remember { mutableFloatStateOf(0f) }
    var hasShield by remember { mutableStateOf(false) }
    var shieldTimeRemaining by remember { mutableFloatStateOf(0f) }
    var engineGlowPhase by remember { mutableFloatStateOf(0f) }

    // Touch input state: -1f (left), +1f (right), 0f (neutral)
    var steeringInput by remember { mutableFloatStateOf(0f) }

    // Game entity collections
    val stars = remember { mutableStateListOf<Star>() }
    val asteroids = remember { mutableStateListOf<Asteroid>() }
    val bonuses = remember { mutableStateListOf<BonusItem>() }
    val particles = remember { mutableStateListOf<Particle>() }
    val floatingTexts = remember { mutableStateListOf<FloatingText>() }

    var nextEntityId by remember { mutableLongStateOf(1L) }
    var asteroidSpawnTimer by remember { mutableFloatStateOf(0.5f) }
    var bonusSpawnTimer by remember { mutableFloatStateOf(6.0f) }
    var survivalScoreTimer by remember { mutableFloatStateOf(0f) }
    var lastFrameTimeNanos by remember { mutableLongStateOf(0L) }

    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070814))
    ) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val shipBaseY = screenHeight * 0.85f
        val shipWidth = 46f
        val shipHeight = 56f
        val shipHitRadius = 24f

        // Initialize stars once when screen dimensions are known
        LaunchedEffect(screenWidth, screenHeight) {
            if (screenWidth > 0f && screenHeight > 0f && stars.isEmpty()) {
                val rand = Random(42)
                for (i in 0 until 90) {
                    val layer = rand.nextInt(3)
                    val (speed, radius, alpha) = when (layer) {
                        0 -> Triple(20f + rand.nextFloat() * 15f, 1.2f, 0.45f)
                        1 -> Triple(45f + rand.nextFloat() * 25f, 1.8f, 0.7f)
                        else -> Triple(85f + rand.nextFloat() * 40f, 2.5f, 0.95f)
                    }
                    stars.add(
                        Star(
                            x = rand.nextFloat() * screenWidth,
                            y = rand.nextFloat() * screenHeight,
                            speed = speed,
                            radius = radius,
                            alpha = alpha,
                            isTwinkle = layer == 2 && rand.nextBoolean()
                        )
                    )
                }
            }
            if (shipX == 0f && screenWidth > 0f) {
                shipX = screenWidth * 0.5f
                shipY = shipBaseY
            }
        }

        // Helper function to reset & start the game
        fun startGame() {
            shipX = screenWidth * 0.5f
            shipY = shipBaseY
            shipVx = 0f
            shipTilt = 0f
            hasShield = false
            shieldTimeRemaining = 0f
            score = 0
            dodgedCount = 0
            gameDuration = 0f
            asteroidSpawnTimer = 0.5f
            bonusSpawnTimer = 7.0f
            survivalScoreTimer = 0f
            asteroids.clear()
            bonuses.clear()
            particles.clear()
            floatingTexts.clear()
            lastFrameTimeNanos = 0L
            gameState = GameState.PLAYING
        }

        // Active Game Loop using withFrameNanos
        LaunchedEffect(gameState, screenWidth, screenHeight) {
            if (gameState != GameState.PLAYING || screenWidth <= 0f || screenHeight <= 0f) return@LaunchedEffect

            lastFrameTimeNanos = 0L
            while (isActive && gameState == GameState.PLAYING) {
                withFrameNanos { currentFrameNanos ->
                    if (lastFrameTimeNanos == 0L) {
                        lastFrameTimeNanos = currentFrameNanos
                        return@withFrameNanos
                    }

                    val rawDt = (currentFrameNanos - lastFrameTimeNanos) / 1_000_000_000f
                    lastFrameTimeNanos = currentFrameNanos
                    // Clamp delta time to prevent physics anomalies on frame hitching
                    val dt = rawDt.coerceIn(0.001f, 0.05f)

                    gameDuration += dt
                    engineGlowPhase = (engineGlowPhase + dt * 14f) % (2f * PI.toFloat())

                    // 1. Difficulty Scaling
                    val baseSpeed = 220f + (gameDuration * 11f).coerceAtMost(520f)
                    val spawnInterval = (1.05f - (gameDuration * 0.022f)).coerceAtLeast(0.32f)

                    // 2. Spaceship Movement & Steering Physics
                    val targetVx = steeringInput * 520f
                    shipVx += (targetVx - shipVx) * (dt * 13f)
                    shipX = (shipX + shipVx * dt).coerceIn(32f, screenWidth - 32f)
                    shipTilt = (shipVx / 520f) * 24f

                    // Shield timer decay
                    if (hasShield) {
                        shieldTimeRemaining -= dt
                        if (shieldTimeRemaining <= 0f) {
                            hasShield = false
                            shieldTimeRemaining = 0f
                            floatingTexts.add(
                                FloatingText(
                                    id = nextEntityId++,
                                    text = "SHIELD EXPIRED",
                                    x = shipX,
                                    y = shipY - 35f,
                                    alpha = 1f,
                                    color = Color(0xFF64B5F6),
                                    life = 1.0f
                                )
                            )
                        }
                    }

                    // Continuous score accrual for surviving
                    survivalScoreTimer += dt
                    if (survivalScoreTimer >= 0.25f) {
                        score += 3
                        survivalScoreTimer = 0f
                    }

                    // Thruster particles
                    if (Random.nextFloat() < 0.8f) {
                        particles.add(
                            Particle(
                                x = shipX + (Random.nextFloat() * 12f - 6f),
                                y = shipY + 22f,
                                vx = (Random.nextFloat() * 30f - 15f) - (shipVx * 0.1f),
                                vy = 110f + Random.nextFloat() * 70f,
                                life = 0.28f,
                                maxLife = 0.28f,
                                color = if (Random.nextBoolean()) Color(0xFF00E5FF) else Color(0xFFFF9100),
                                radius = 2.5f + Random.nextFloat() * 2f
                            )
                        )
                    }

                    // 3. Asteroid Spawning
                    asteroidSpawnTimer -= dt
                    if (asteroidSpawnTimer <= 0f) {
                        asteroidSpawnTimer = spawnInterval * (0.8f + Random.nextFloat() * 0.4f)
                        val radius = 18f + Random.nextFloat() * 22f
                        val astX = radius + Random.nextFloat() * (screenWidth - radius * 2f)
                        val astSpeedY = baseSpeed * (0.85f + Random.nextFloat() * 0.35f)
                        val astSpeedX = (Random.nextFloat() * 60f - 30f)

                        // Generate unique craggy polygon vertices
                        val numPoints = 8 + Random.nextInt(4)
                        val verts = mutableListOf<Offset>()
                        for (i in 0 until numPoints) {
                            val angle = (i.toFloat() / numPoints) * 2f * PI.toFloat()
                            val r = radius * (0.75f + Random.nextFloat() * 0.45f)
                            verts.add(Offset(r * cos(angle), r * sin(angle)))
                        }

                        // Generate crater details
                        val craters = mutableListOf<Offset>()
                        val numCraters = 1 + Random.nextInt(3)
                        for (i in 0 until numCraters) {
                            val crAngle = Random.nextFloat() * 2f * PI.toFloat()
                            val crDist = Random.nextFloat() * (radius * 0.55f)
                            craters.add(Offset(crDist * cos(crAngle), crDist * sin(crAngle)))
                        }

                        val shade = 0.5f + Random.nextFloat() * 0.35f
                        val astColor = Color(
                            red = (0.55f * shade),
                            green = (0.52f * shade),
                            blue = (0.58f * shade)
                        )

                        asteroids.add(
                            Asteroid(
                                id = nextEntityId++,
                                x = astX,
                                y = -radius - 10f,
                                radius = radius,
                                speedY = astSpeedY,
                                speedX = astSpeedX,
                                rotation = Random.nextFloat() * 360f,
                                rotationSpeed = (Random.nextFloat() * 90f - 45f),
                                vertices = verts,
                                craterOffsets = craters,
                                baseColor = astColor
                            )
                        )
                    }

                    // 4. Bonus Power-up Spawning
                    bonusSpawnTimer -= dt
                    if (bonusSpawnTimer <= 0f) {
                        bonusSpawnTimer = 8f + Random.nextFloat() * 7f
                        val bonusType = if (Random.nextFloat() < 0.45f) BonusType.SHIELD else BonusType.EXTRA_POINTS
                        val bonusX = 35f + Random.nextFloat() * (screenWidth - 70f)
                        bonuses.add(
                            BonusItem(
                                id = nextEntityId++,
                                x = bonusX,
                                y = -30f,
                                radius = 20f,
                                speedY = 160f + Random.nextFloat() * 40f,
                                type = bonusType
                            )
                        )
                    }

                    // 5. Update Stars
                    for (i in stars.indices) {
                        val star = stars[i]
                        star.y += star.speed * dt
                        if (star.y > screenHeight) {
                            star.y = 0f
                            star.x = Random.nextFloat() * screenWidth
                        }
                    }

                    // 6. Update Bonuses & Check Collection
                    val remainingBonuses = mutableListOf<BonusItem>()
                    for (bonus in bonuses) {
                        bonus.y += bonus.speedY * dt
                        bonus.pulsePhase = (bonus.pulsePhase + dt * 4.5f) % (2f * PI.toFloat())

                        // Distance to player spaceship
                        val dist = hypot(shipX - bonus.x, shipY - bonus.y)
                        if (dist < (shipHitRadius + bonus.radius + 6f)) {
                            // Bonus Collected!
                            when (bonus.type) {
                                BonusType.SHIELD -> {
                                    hasShield = true
                                    shieldTimeRemaining = 12f
                                    score += 60
                                    floatingTexts.add(
                                        FloatingText(
                                            id = nextEntityId++,
                                            text = "SHIELD ONLINE!",
                                            x = shipX,
                                            y = shipY - 45f,
                                            alpha = 1f,
                                            color = Color(0xFF00E5FF),
                                            life = 1.3f
                                        )
                                    )
                                    // Shield pickup sparkles
                                    for (p in 0 until 18) {
                                        val ang = Random.nextFloat() * 2f * PI.toFloat()
                                        val spd = 60f + Random.nextFloat() * 110f
                                        particles.add(
                                            Particle(
                                                x = bonus.x,
                                                y = bonus.y,
                                                vx = spd * cos(ang),
                                                vy = spd * sin(ang),
                                                life = 0.5f,
                                                maxLife = 0.5f,
                                                color = Color(0xFF00E5FF),
                                                radius = 3.5f
                                            )
                                        )
                                    }
                                }
                                BonusType.EXTRA_POINTS -> {
                                    score += 200
                                    floatingTexts.add(
                                        FloatingText(
                                            id = nextEntityId++,
                                            text = "+200 PTS",
                                            x = shipX,
                                            y = shipY - 45f,
                                            alpha = 1f,
                                            color = Color(0xFFFFD54F),
                                            life = 1.3f
                                        )
                                    )
                                    // Star sparkle explosion
                                    for (p in 0 until 20) {
                                        val ang = Random.nextFloat() * 2f * PI.toFloat()
                                        val spd = 70f + Random.nextFloat() * 120f
                                        particles.add(
                                            Particle(
                                                x = bonus.x,
                                                y = bonus.y,
                                                vx = spd * cos(ang),
                                                vy = spd * sin(ang),
                                                life = 0.55f,
                                                maxLife = 0.55f,
                                                color = Color(0xFFFFCA28),
                                                radius = 3.5f
                                            )
                                        )
                                    }
                                }
                            }
                        } else if (bonus.y <= screenHeight + 40f) {
                            remainingBonuses.add(bonus)
                        }
                    }
                    bonuses.clear()
                    bonuses.addAll(remainingBonuses)

                    // 7. Update Asteroids & Check Collisions
                    var hitDetected = false
                    val remainingAsteroids = mutableListOf<Asteroid>()

                    for (ast in asteroids) {
                        ast.y += ast.speedY * dt
                        ast.x += ast.speedX * dt
                        ast.rotation += ast.rotationSpeed * dt

                        val dist = hypot(shipX - ast.x, shipY - ast.y)
                        val collisionDistance = shipHitRadius + (ast.radius * 0.85f)

                        if (dist < collisionDistance) {
                            // Collision occurred!
                            if (hasShield) {
                                // Shield absorbs the blow!
                                hasShield = false
                                shieldTimeRemaining = 0f
                                score += 50
                                floatingTexts.add(
                                    FloatingText(
                                        id = nextEntityId++,
                                        text = "SHIELD ABSORBED!",
                                        x = shipX,
                                        y = shipY - 45f,
                                        alpha = 1f,
                                        color = Color(0xFF81D4FA),
                                        life = 1.2f
                                    )
                                )
                                // Spawn shield shatter shockwave
                                for (p in 0 until 28) {
                                    val ang = Random.nextFloat() * 2f * PI.toFloat()
                                    val spd = 80f + Random.nextFloat() * 160f
                                    particles.add(
                                        Particle(
                                            x = ast.x,
                                            y = ast.y,
                                            vx = spd * cos(ang),
                                            vy = spd * sin(ang),
                                            life = 0.6f,
                                            maxLife = 0.6f,
                                            color = if (p % 2 == 0) Color(0xFF00E5FF) else Color(0xFFE1F5FE),
                                            radius = 3.5f
                                        )
                                    )
                                }
                                // Asteroid destroyed
                                continue
                            } else {
                                // Fatal crash!
                                hitDetected = true
                                // Big ship explosion
                                for (p in 0 until 45) {
                                    val ang = Random.nextFloat() * 2f * PI.toFloat()
                                    val spd = 40f + Random.nextFloat() * 220f
                                    val col = when (Random.nextInt(4)) {
                                        0 -> Color(0xFFFF5252)
                                        1 -> Color(0xFFFF9800)
                                        2 -> Color(0xFFFFEB3B)
                                        else -> Color(0xFF00E5FF)
                                    }
                                    particles.add(
                                        Particle(
                                            x = shipX,
                                            y = shipY,
                                            vx = spd * cos(ang),
                                            vy = spd * sin(ang),
                                            life = 0.85f,
                                            maxLife = 0.85f,
                                            color = col,
                                            radius = 3f + Random.nextFloat() * 3.5f
                                        )
                                    )
                                }
                                break
                            }
                        }

                        // Check if safely dodged off bottom screen
                        if (ast.y > screenHeight + ast.radius + 20f) {
                            score += 15
                            dodgedCount++
                        } else {
                            remainingAsteroids.add(ast)
                        }
                    }

                    if (hitDetected) {
                        gameState = GameState.GAME_OVER
                        if (score > highScore) {
                            highScore = score
                        }
                        return@withFrameNanos
                    }

                    asteroids.clear()
                    asteroids.addAll(remainingAsteroids)

                    // 8. Update Particles
                    val remainingParticles = mutableListOf<Particle>()
                    for (p in particles) {
                        p.x += p.vx * dt
                        p.y += p.vy * dt
                        p.life -= dt
                        if (p.life > 0f) {
                            remainingParticles.add(p)
                        }
                    }
                    particles.clear()
                    particles.addAll(remainingParticles)

                    // 9. Update Floating Texts
                    val remainingTexts = mutableListOf<FloatingText>()
                    for (ft in floatingTexts) {
                        ft.y -= 45f * dt
                        ft.life -= dt
                        ft.alpha = (ft.life / 1.2f).coerceIn(0f, 1f)
                        if (ft.life > 0f) {
                            remainingTexts.add(ft)
                        }
                    }
                    floatingTexts.clear()
                    floatingTexts.addAll(remainingTexts)
                }
            }
        }

        // Touch Input Gestures: Tapping / Holding Left or Right half
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(gameState) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (gameState == GameState.PLAYING) {
                            val touchX = down.position.x
                            steeringInput = if (touchX < size.width * 0.5f) -1f else 1f
                            // Track pointer continuously until released
                            do {
                                val event = awaitPointerEvent()
                                val activePointer = event.changes.firstOrNull { it.id == down.id }
                                if (activePointer != null && activePointer.pressed) {
                                    steeringInput = if (activePointer.position.x < size.width * 0.5f) -1f else 1f
                                }
                            } while (event.changes.any { it.pressed })
                            steeringInput = 0f
                        }
                    }
                }
        ) {
            // Full Screen Responsive Game Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("game_canvas")
            ) {
                // 1. Draw Deep Space Cosmic Nebula Background
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF05060F),
                            Color(0xFF0C1026),
                            Color(0xFF070914)
                        )
                    )
                )

                // Soft glowing space nebula clusters
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1F7C4DFF), Color.Transparent),
                        center = Offset(size.width * 0.3f, size.height * 0.35f),
                        radius = size.width * 0.6f
                    ),
                    center = Offset(size.width * 0.3f, size.height * 0.35f),
                    radius = size.width * 0.6f
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1800E5FF), Color.Transparent),
                        center = Offset(size.width * 0.75f, size.height * 0.65f),
                        radius = size.width * 0.55f
                    ),
                    center = Offset(size.width * 0.75f, size.height * 0.65f),
                    radius = size.width * 0.55f
                )

                // 2. Draw Stars
                for (star in stars) {
                    drawCircle(
                        color = Color.White.copy(alpha = star.alpha),
                        radius = star.radius,
                        center = Offset(star.x, star.y)
                    )
                    if (star.isTwinkle) {
                        drawLine(
                            color = Color(0xFF80D8FF).copy(alpha = star.alpha * 0.8f),
                            start = Offset(star.x - star.radius * 2.5f, star.y),
                            end = Offset(star.x + star.radius * 2.5f, star.y),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color(0xFF80D8FF).copy(alpha = star.alpha * 0.8f),
                            start = Offset(star.x, star.y - star.radius * 2.5f),
                            end = Offset(star.x, star.y + star.radius * 2.5f),
                            strokeWidth = 1f
                        )
                    }
                }

                // 3. Draw Asteroids
                for (ast in asteroids) {
                    drawAsteroid(ast)
                }

                // 4. Draw Bonuses
                for (bonus in bonuses) {
                    drawBonusItem(bonus)
                }

                // 5. Draw Particle FX
                for (p in particles) {
                    val pAlpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                    drawCircle(
                        color = p.color.copy(alpha = pAlpha),
                        radius = p.radius * pAlpha,
                        center = Offset(p.x, p.y)
                    )
                }

                // 6. Draw Player Spaceship (if playing or start)
                if (gameState != GameState.GAME_OVER) {
                    drawSpaceship(
                        x = shipX,
                        y = shipY,
                        tilt = shipTilt,
                        hasShield = hasShield,
                        shieldTime = shieldTimeRemaining,
                        engineGlow = engineGlowPhase
                    )
                }

                // 7. Draw Floating Floating Scores/Notifications
                for (ft in floatingTexts) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = ft.text,
                        topLeft = Offset(ft.x - 50f, ft.y),
                        style = TextStyle(
                            color = ft.color.copy(alpha = ft.alpha),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            // In-Game Steering Indicators on Canvas Bottom
            if (gameState == GameState.PLAYING) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = if (steeringInput < 0f) Color(0x5500E5FF) else Color(0x1AFFFFFF),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(width = 90.dp, height = 52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "◀ LEFT",
                                color = if (steeringInput < 0f) Color(0xFF00E5FF) else Color(0x88FFFFFF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Surface(
                        color = if (steeringInput > 0f) Color(0x5500E5FF) else Color(0x1AFFFFFF),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(width = 90.dp, height = 52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "RIGHT ▶",
                                color = if (steeringInput > 0f) Color(0xFF00E5FF) else Color(0x88FFFFFF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // HUD: Top Game Bar (Score, High Score, Shield Status)
            GameTopHud(
                score = score,
                highScore = highScore,
                hasShield = hasShield,
                shieldTimeRemaining = shieldTimeRemaining,
                dodgedCount = dodgedCount
            )

            // START SCREEN OVERLAY
            AnimatedVisibility(
                visible = gameState == GameState.START,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                StartScreenOverlay(
                    highScore = highScore,
                    onStartGame = { startGame() }
                )
            }

            // GAME OVER OVERLAY
            AnimatedVisibility(
                visible = gameState == GameState.GAME_OVER,
                enter = fadeIn() + scaleIn(initialScale = 0.92f),
                exit = fadeOut() + scaleOut(targetScale = 0.92f)
            ) {
                GameOverScreenOverlay(
                    score = score,
                    highScore = highScore,
                    dodgedCount = dodgedCount,
                    durationSeconds = gameDuration.toInt(),
                    onRestart = { startGame() }
                )
            }
        }
    }
}

/**
 * HUD at top of screen
 */
@Composable
private fun GameTopHud(
    score: Int,
    highScore: Int,
    hasShield: Boolean,
    shieldTimeRemaining: Float,
    dodgedCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Current Score Card
        Surface(
            color = Color(0x33101633),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3300E5FF))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "SCORE",
                    color = Color(0xFF00E5FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "$score",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Active Shield Indicator pill
        if (hasShield) {
            Surface(
                color = Color(0x4400E5FF),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E5FF))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Shield Active",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${shieldTimeRemaining.toInt()}s",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Best High Score Card
        Surface(
            color = Color(0x331E1528),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFD54F))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Best",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "BEST",
                        color = Color(0xFFFFD54F),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
                Text(
                    text = "$highScore",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Start Screen Overlay with instructions and launch button
 */
@Composable
private fun StartScreenOverlay(
    highScore: Int,
    onStartGame: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD060714))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onStartGame() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(32.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // Futuristic Title Badge
            Surface(
                color = Color(0x2600E5FF),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x5500E5FF))
            ) {
                Text(
                    text = "COSMIC ARCADE",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SPACE\nDODGER",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
                lineHeight = 48.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Game instructions card
            Surface(
                color = Color(0x22131C38),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x332A3B66)),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "HOW TO PLAY",
                        color = Color(0xFF80D8FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "• Tap or Hold LEFT to steer left\n• Tap or Hold RIGHT to steer right\n• Avoid falling asteroids\n• Collect Shields & Stars for bonuses",
                        color = Color(0xFFCFD8DC),
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Start
                    )
                }
            }

            if (highScore > 0) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0x26FFD54F), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "High Score",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BEST RECORD: $highScore",
                        color = Color(0xFFFFE082),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = onStartGame,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color(0xFF070914)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(56.dp)
                    .testTag("start_game_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TAP TO PLAY",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

/**
 * Game Over Screen Overlay with stats breakdown and restart button
 */
@Composable
private fun GameOverScreenOverlay(
    score: Int,
    highScore: Int,
    dodgedCount: Int,
    durationSeconds: Int,
    onRestart: () -> Unit
) {
    val isNewHigh = score >= highScore && score > 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60A0612))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onRestart() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(28.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Surface(
                color = Color(0x33FF1744),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x88FF1744))
            ) {
                Text(
                    text = "CRITICAL COLLISION",
                    color = Color(0xFFFF5252),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "GAME OVER",
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            if (isNewHigh) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Color(0x33FFD54F),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD54F))
                ) {
                    Text(
                        text = "★ NEW HIGH SCORE! ★",
                        color = Color(0xFFFFD54F),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Score Summary Card
            Surface(
                color = Color(0x2E1B152A),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33443366)),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "FINAL SCORE",
                        color = Color(0xFFB39DDB),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$score",
                        color = Color.White,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0x33FFFFFF))
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "DODGED",
                                color = Color(0xFF90A4AE),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$dodgedCount",
                                color = Color(0xFF00E5FF),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "SURVIVED",
                                color = Color(0xFF90A4AE),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${durationSeconds}s",
                                color = Color(0xFFFFB74D),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "BEST",
                                color = Color(0xFF90A4AE),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$highScore",
                                color = Color(0xFFFFD54F),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color(0xFF070914)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(56.dp)
                    .testTag("restart_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Restart",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PLAY AGAIN",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

/**
 * Draw player spaceship on canvas with tilt, plasma thruster, and active shield ring
 */
private fun DrawScope.drawSpaceship(
    x: Float,
    y: Float,
    tilt: Float,
    hasShield: Boolean,
    shieldTime: Float,
    engineGlow: Float
) {
    rotate(degrees = tilt, pivot = Offset(x, y)) {
        // 1. Thruster Exhaust Flame
        val flameLength = 22f + sin(engineGlow) * 7f
        val flamePath = Path().apply {
            moveTo(x - 8f, y + 16f)
            lineTo(x, y + 16f + flameLength)
            lineTo(x + 8f, y + 16f)
            close()
        }
        drawPath(
            path = flamePath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFF00E5FF), Color(0x007C4DFF)),
                startY = y + 16f,
                endY = y + 16f + flameLength
            )
        )

        // 2. Spaceship Hull Path
        val shipPath = Path().apply {
            moveTo(x, y - 28f) // Nose tip
            lineTo(x + 18f, y + 16f) // Right wing tip
            lineTo(x + 10f, y + 12f) // Right wing inner
            lineTo(x + 6f, y + 18f) // Right engine
            lineTo(x - 6f, y + 18f) // Left engine
            lineTo(x - 10f, y + 12f) // Left wing inner
            lineTo(x - 18f, y + 16f) // Left wing tip
            close()
        }

        // Hull gradient: metallic cyan & midnight slate
        drawPath(
            path = shipPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFE0F7FA), Color(0xFF263238), Color(0xFF102027)),
                startY = y - 28f,
                endY = y + 18f
            )
        )

        // Hull border outline
        drawPath(
            path = shipPath,
            color = Color(0xFF80DEEA),
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )

        // Cockpit canopy glow
        val canopyPath = Path().apply {
            moveTo(x, y - 14f)
            lineTo(x + 4.5f, y + 2f)
            lineTo(x, y + 5f)
            lineTo(x - 4.5f, y + 2f)
            close()
        }
        drawPath(
            path = canopyPath,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFF00E5FF), Color(0xFF0097A7)),
                center = Offset(x, y - 6f),
                radius = 12f
            )
        )

        // Wing accent lights
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = 2f,
            center = Offset(x - 16f, y + 15f)
        )
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = 2f,
            center = Offset(x + 16f, y + 15f)
        )

        // 3. Energy Shield Bubble
        if (hasShield) {
            val shieldRadius = 36f
            val pulse = sin(shieldTime * 8f) * 2.5f

            // Translucent shield glow fill
            drawCircle(
                color = Color(0x2200E5FF),
                radius = shieldRadius + pulse,
                center = Offset(x, y)
            )
            // Outer shield aura ring
            drawCircle(
                color = Color(0xFF80D8FF).copy(alpha = 0.85f),
                radius = shieldRadius + pulse,
                center = Offset(x, y),
                style = Stroke(width = 2.5f)
            )
            // Inner shield highlight ring
            drawCircle(
                color = Color(0x55E1F5FE),
                radius = shieldRadius * 0.75f,
                center = Offset(x, y),
                style = Stroke(width = 1f)
            )
        }
    }
}

/**
 * Draw individual craggy asteroid with craters and rotation
 */
private fun DrawScope.drawAsteroid(asteroid: Asteroid) {
    rotate(degrees = asteroid.rotation, pivot = Offset(asteroid.x, asteroid.y)) {
        if (asteroid.vertices.isNotEmpty()) {
            val polyPath = Path().apply {
                val first = asteroid.vertices.first()
                moveTo(asteroid.x + first.x, asteroid.y + first.y)
                for (i in 1 until asteroid.vertices.size) {
                    val v = asteroid.vertices[i]
                    lineTo(asteroid.x + v.x, asteroid.y + v.y)
                }
                close()
            }

            // Rocky shaded body
            drawPath(
                path = polyPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        asteroid.baseColor.copy(alpha = 1f),
                        asteroid.baseColor.copy(red = asteroid.baseColor.red * 0.6f, green = asteroid.baseColor.green * 0.6f, blue = asteroid.baseColor.blue * 0.6f)
                    ),
                    start = Offset(asteroid.x - asteroid.radius, asteroid.y - asteroid.radius),
                    end = Offset(asteroid.x + asteroid.radius, asteroid.y + asteroid.radius)
                )
            )

            // Surface ridge stroke
            drawPath(
                path = polyPath,
                color = Color(0x33FFFFFF),
                style = Stroke(width = 1.5f)
            )

            // Craters
            for (crater in asteroid.craterOffsets) {
                drawCircle(
                    color = Color(0x44000000),
                    radius = asteroid.radius * 0.22f,
                    center = Offset(asteroid.x + crater.x, asteroid.y + crater.y)
                )
                drawCircle(
                    color = Color(0x22FFFFFF),
                    radius = asteroid.radius * 0.22f,
                    center = Offset(asteroid.x + crater.x, asteroid.y + crater.y),
                    style = Stroke(width = 1f)
                )
            }
        }
    }
}

/**
 * Draw power-up collectible with pulsing radiant aura
 */
private fun DrawScope.drawBonusItem(bonus: BonusItem) {
    val pulse = sin(bonus.pulsePhase) * 3f
    val center = Offset(bonus.x, bonus.y)

    when (bonus.type) {
        BonusType.SHIELD -> {
            // Cyan Shield Orb
            drawCircle(
                color = Color(0x2600E5FF),
                radius = bonus.radius + 6f + pulse,
                center = center
            )
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = bonus.radius,
                center = center,
                style = Stroke(width = 2.5f)
            )
            // Inner core
            drawCircle(
                color = Color(0x6600E5FF),
                radius = bonus.radius * 0.65f,
                center = center
            )
            // Shield crest icon path
            val crestPath = Path().apply {
                moveTo(center.x, center.y - 8f)
                lineTo(center.x + 6f, center.y - 4f)
                lineTo(center.x + 4f, center.y + 6f)
                lineTo(center.x, center.y + 9f)
                lineTo(center.x - 4f, center.y + 6f)
                lineTo(center.x - 6f, center.y - 4f)
                close()
            }
            drawPath(path = crestPath, color = Color.White)
        }
        BonusType.EXTRA_POINTS -> {
            // Golden Star Orb
            drawCircle(
                color = Color(0x33FFD54F),
                radius = bonus.radius + 6f + pulse,
                center = center
            )
            drawCircle(
                color = Color(0xFFFFD54F),
                radius = bonus.radius,
                center = center,
                style = Stroke(width = 2.5f)
            )
            drawCircle(
                color = Color(0x66FFA000),
                radius = bonus.radius * 0.65f,
                center = center
            )
            // 4-point star shape
            val starPath = Path().apply {
                moveTo(center.x, center.y - 9f)
                lineTo(center.x + 2.5f, center.y - 2.5f)
                lineTo(center.x + 9f, center.y)
                lineTo(center.x + 2.5f, center.y + 2.5f)
                lineTo(center.x, center.y + 9f)
                lineTo(center.x - 2.5f, center.y + 2.5f)
                lineTo(center.x - 9f, center.y)
                lineTo(center.x - 2.5f, center.y - 2.5f)
                close()
            }
            drawPath(path = starPath, color = Color.White)
        }
    }
}
