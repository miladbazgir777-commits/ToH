package com.tomeofhealing.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tomeofhealing.app.model.EmblemDefinition
import com.tomeofhealing.app.ui.theme.LocalTomeVisualTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val TomeOfHealingMainEmblem = EmblemDefinition(
    base = "open_tome",
    centralSymbol = "rod_of_asclepius",
    secondarySymbol = "arcane_halo",
    frame = "silver_filigree",
    primaryColor = 0xFF2B114A,
    secondaryColor = 0xFFC9D0DA,
    accentColor = 0xFF102B59,
    glow = 0.22f
)

@Composable
fun FantasyBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val theme = LocalTomeVisualTheme.current
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(theme.background), Color(theme.surface).copy(alpha = 0.92f), Color(theme.background))
                )
            )
            val line = Color(theme.primary).copy(alpha = 0.035f + theme.textureStrength * 0.08f)
            when (theme.material) {
                "aged_wood" -> {
                    var y = 18f
                    while (y < size.height) {
                        drawLine(line, Offset(0f, y), Offset(size.width, y + 7f), strokeWidth = 1f)
                        y += 34f
                    }
                }
                "parchment" -> {
                    var y = 22f
                    while (y < size.height) {
                        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += 42f
                    }
                }
                else -> {
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(line, Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 1f)
                        x += 72f
                    }
                }
            }
            drawCircle(Color(theme.accent).copy(alpha = 0.07f), radius = size.minDimension * 0.42f, center = Offset(size.width * .86f, size.height * .14f))
        }
        content()
    }
}

@Composable
fun FantasyPanel(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val theme = LocalTomeVisualTheme.current
    val borderColor = if (emphasized) Color(theme.primary).copy(alpha = .82f) else Color(theme.primary).copy(alpha = .34f)
    val shape = RoundedCornerShape(if (emphasized) 16.dp else 13.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(theme.surface).copy(alpha = .97f),
                        Color(theme.secondary).copy(alpha = if (emphasized) .32f else .14f),
                        Color(theme.surface).copy(alpha = .98f)
                    )
                )
            )
            .border(if (emphasized) 1.5.dp else 1.dp, borderColor, shape),
        content = content
    )
}

@Composable
fun RuneDivider(modifier: Modifier = Modifier) {
    val theme = LocalTomeVisualTheme.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).height(8.dp)) {
            drawLine(Color(theme.primary).copy(alpha = .45f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
        }
        Text("◇", color = Color(theme.primary).copy(alpha = .72f), modifier = Modifier.padding(horizontal = 8.dp))
        Canvas(Modifier.weight(1f).height(8.dp)) {
            drawLine(Color(theme.primary).copy(alpha = .45f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
        }
    }
}

@Composable
fun EmblemView(
    emblem: EmblemDefinition,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    animate: Boolean = false,
    showBanner: Boolean = true
) {
    val theme = LocalTomeVisualTheme.current
    val pulse = if (animate && theme.animationScale > 0f) {
        val transition = rememberInfiniteTransition(label = "emblemPulse")
        val p by transition.animateFloat(
            initialValue = .78f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween((2600 / theme.animationScale.coerceAtLeast(.2f)).toInt(), easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "pulse"
        )
        p
    } else 1f

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val primary = Color(emblem.primaryColor)
            val silver = Color(emblem.secondaryColor)
            val accent = Color(emblem.accentColor)
            val c = center
            val r = this.size.minDimension * .39f
            val glowAlpha = (emblem.glow * pulse).coerceIn(0f, .65f)
            if (glowAlpha > .01f) {
                drawCircle(accent.copy(alpha = glowAlpha * .35f), r * 1.28f, c)
                drawCircle(primary.copy(alpha = glowAlpha * .24f), r * 1.08f, c)
            }
            drawSecondary(emblem.secondarySymbol, c, r, accent, silver)
            drawBase(emblem.base, c, r, primary, silver)
            drawCentral(emblem.centralSymbol, c, r, silver, accent)
            drawFrame(emblem.frame, c, r, silver, primary)
        }
        emblem.initials?.takeIf { it.isNotBlank() }?.let {
            Text(it.take(3).uppercase(), color = Color(emblem.secondaryColor), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        }
        if (showBanner) emblem.banner?.takeIf { it.isNotBlank() }?.let {
            Text(
                it.take(16),
                color = Color(emblem.secondaryColor),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomCenter).background(Color(emblem.primaryColor).copy(alpha = .82f), RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

private fun DrawScope.drawSecondary(symbol: String, c: Offset, r: Float, accent: Color, silver: Color) {
    when (symbol) {
        "arcane_halo" -> {
            drawCircle(accent.copy(alpha = .55f), r * .82f, c, style = Stroke(r * .035f))
            for (i in 0 until 12) {
                val a = (i / 12f) * (2f * PI).toFloat()
                val p1 = Offset(c.x + cos(a.toDouble()).toFloat() * r * .88f, c.y + sin(a.toDouble()).toFloat() * r * .88f)
                val p2 = Offset(c.x + cos(a.toDouble()).toFloat() * r * .98f, c.y + sin(a.toDouble()).toFloat() * r * .98f)
                drawLine(silver.copy(alpha = .52f), p1, p2, r * .018f)
            }
        }
        "laurel" -> {
            for (i in 0..6) {
                val y = c.y - r * .55f + i * r * .18f
                drawOval(silver.copy(alpha = .45f), topLeft = Offset(c.x - r * .83f, y), size = Size(r * .25f, r * .11f))
                drawOval(silver.copy(alpha = .45f), topLeft = Offset(c.x + r * .58f, y), size = Size(r * .25f, r * .11f))
            }
        }
        "wings" -> {
            val left = Path().apply { moveTo(c.x - r * .18f, c.y); cubicTo(c.x-r*.52f,c.y-r*.62f,c.x-r*.95f,c.y-r*.30f,c.x-r*.86f,c.y+r*.16f); cubicTo(c.x-r*.63f,c.y-r*.02f,c.x-r*.40f,c.y+r*.10f,c.x-r*.18f,c.y+r*.18f); close() }
            val right = Path().apply { moveTo(c.x + r * .18f, c.y); cubicTo(c.x+r*.52f,c.y-r*.62f,c.x+r*.95f,c.y-r*.30f,c.x+r*.86f,c.y+r*.16f); cubicTo(c.x+r*.63f,c.y-r*.02f,c.x+r*.40f,c.y+r*.10f,c.x+r*.18f,c.y+r*.18f); close() }
            drawPath(left, silver.copy(alpha = .25f)); drawPath(right, silver.copy(alpha = .25f))
        }
    }
}

private fun DrawScope.drawBase(base: String, c: Offset, r: Float, primary: Color, silver: Color) {
    when (base) {
        "shield" -> {
            val p = Path().apply { moveTo(c.x-r*.68f,c.y-r*.62f); lineTo(c.x+r*.68f,c.y-r*.62f); lineTo(c.x+r*.57f,c.y+r*.28f); lineTo(c.x,c.y+r*.82f); lineTo(c.x-r*.57f,c.y+r*.28f); close() }
            drawPath(p, primary.copy(alpha=.82f)); drawPath(p, silver.copy(alpha=.7f), style=Stroke(r*.045f))
        }
        "circle", "medallion" -> { drawCircle(primary.copy(alpha=.78f), r*.72f,c); drawCircle(silver.copy(alpha=.72f),r*.72f,c,style=Stroke(r*.045f)) }
        "hex" -> {
            val p = Path(); for(i in 0..5){ val a=(PI/3*i-PI/2).toFloat(); val pt=Offset(c.x+cos(a.toDouble()).toFloat()*r*.72f,c.y+sin(a.toDouble()).toFloat()*r*.72f); if(i==0)p.moveTo(pt.x,pt.y) else p.lineTo(pt.x,pt.y)}; p.close(); drawPath(p,primary.copy(alpha=.8f));drawPath(p,silver.copy(alpha=.7f),style=Stroke(r*.04f))
        }
        "rune_plate" -> { drawRoundRect(primary.copy(alpha=.82f), Offset(c.x-r*.65f,c.y-r*.67f), Size(r*1.3f,r*1.34f), cornerRadius=androidx.compose.ui.geometry.CornerRadius(r*.12f)); drawRoundRect(silver.copy(alpha=.65f), Offset(c.x-r*.65f,c.y-r*.67f), Size(r*1.3f,r*1.34f), cornerRadius=androidx.compose.ui.geometry.CornerRadius(r*.12f), style=Stroke(r*.04f)) }
        else -> { // open_tome
            val left = Path().apply { moveTo(c.x,c.y-r*.35f); cubicTo(c.x-r*.22f,c.y-r*.48f,c.x-r*.68f,c.y-r*.42f,c.x-r*.72f,c.y+r*.35f); cubicTo(c.x-r*.38f,c.y+r*.22f,c.x-r*.17f,c.y+r*.25f,c.x,c.y+r*.42f); close() }
            val right = Path().apply { moveTo(c.x,c.y-r*.35f); cubicTo(c.x+r*.22f,c.y-r*.48f,c.x+r*.68f,c.y-r*.42f,c.x+r*.72f,c.y+r*.35f); cubicTo(c.x+r*.38f,c.y+r*.22f,c.x+r*.17f,c.y+r*.25f,c.x,c.y+r*.42f); close() }
            drawPath(left, primary.copy(alpha=.82f)); drawPath(right, primary.copy(alpha=.82f)); drawPath(left,silver.copy(alpha=.72f),style=Stroke(r*.035f));drawPath(right,silver.copy(alpha=.72f),style=Stroke(r*.035f)); drawLine(silver.copy(alpha=.7f),Offset(c.x,c.y-r*.34f),Offset(c.x,c.y+r*.4f),r*.025f)
        }
    }
}

private fun DrawScope.drawCentral(symbol: String, c: Offset, r: Float, silver: Color, accent: Color) {
    when(symbol){
        "rod_of_asclepius" -> {
            drawLine(silver, Offset(c.x,c.y-r*.57f), Offset(c.x,c.y+r*.55f), r*.07f, cap=StrokeCap.Round)
            val snake = Path().apply { moveTo(c.x-r*.06f,c.y-r*.35f); cubicTo(c.x+r*.42f,c.y-r*.25f,c.x-r*.42f,c.y-r*.02f,c.x+r*.08f,c.y+r*.08f); cubicTo(c.x+r*.38f,c.y+r*.17f,c.x-r*.26f,c.y+r*.37f,c.x-r*.02f,c.y+r*.46f) }
            drawPath(snake,accent,style=Stroke(r*.075f,cap=StrokeCap.Round))
            drawCircle(accent,r*.09f,Offset(c.x-r*.01f,c.y-r*.40f))
        }
        "heart" -> { val p=Path().apply{ moveTo(c.x,c.y+r*.48f); cubicTo(c.x-r*.72f,c.y+r*.02f,c.x-r*.45f,c.y-r*.52f,c.x,c.y-r*.18f); cubicTo(c.x+r*.45f,c.y-r*.52f,c.x+r*.72f,c.y+r*.02f,c.x,c.y+r*.48f);close()}; drawPath(p,accent.copy(alpha=.92f)); drawPath(p,silver.copy(alpha=.8f),style=Stroke(r*.035f)) }
        "brain" -> { drawCircle(accent.copy(alpha=.72f),r*.32f,Offset(c.x-r*.18f,c.y));drawCircle(accent.copy(alpha=.72f),r*.32f,Offset(c.x+r*.18f,c.y));drawCircle(silver.copy(alpha=.55f),r*.08f,Offset(c.x-r*.25f,c.y-r*.18f));drawCircle(silver.copy(alpha=.55f),r*.08f,Offset(c.x+r*.22f,c.y+r*.14f)) }
        "eye" -> { val p=Path().apply{ moveTo(c.x-r*.55f,c.y);quadraticBezierTo(c.x,c.y-r*.40f,c.x+r*.55f,c.y);quadraticBezierTo(c.x,c.y+r*.40f,c.x-r*.55f,c.y);close()};drawPath(p,silver.copy(alpha=.22f));drawPath(p,silver,style=Stroke(r*.04f));drawCircle(accent,r*.18f,c) }
        "lungs" -> { drawOval(accent.copy(alpha=.65f),Offset(c.x-r*.5f,c.y-r*.25f),Size(r*.42f,r*.78f));drawOval(accent.copy(alpha=.65f),Offset(c.x+r*.08f,c.y-r*.25f),Size(r*.42f,r*.78f));drawLine(silver,Offset(c.x,c.y-r*.48f),Offset(c.x,c.y+r*.12f),r*.05f) }
        "bone" -> { drawLine(silver,Offset(c.x-r*.35f,c.y+r*.35f),Offset(c.x+r*.35f,c.y-r*.35f),r*.13f,cap=StrokeCap.Round);drawCircle(silver,r*.12f,Offset(c.x-r*.4f,c.y+r*.4f));drawCircle(silver,r*.12f,Offset(c.x+r*.4f,c.y-r*.4f)) }
        "flask" -> { val p=Path().apply{ moveTo(c.x-r*.13f,c.y-r*.48f);lineTo(c.x+r*.13f,c.y-r*.48f);lineTo(c.x+r*.12f,c.y-r*.10f);lineTo(c.x+r*.42f,c.y+r*.42f);lineTo(c.x-r*.42f,c.y+r*.42f);lineTo(c.x-r*.12f,c.y-r*.10f);close()};drawPath(p,accent.copy(alpha=.72f));drawPath(p,silver,style=Stroke(r*.04f)) }
        "cross" -> { drawRect(silver,Offset(c.x-r*.12f,c.y-r*.48f),Size(r*.24f,r*.96f));drawRect(silver,Offset(c.x-r*.48f,c.y-r*.12f),Size(r*.96f,r*.24f)) }
        "skull" -> { drawCircle(silver.copy(alpha=.78f),r*.32f,Offset(c.x,c.y-r*.08f));drawCircle(Color.Black.copy(alpha=.6f),r*.07f,Offset(c.x-r*.12f,c.y-r*.12f));drawCircle(Color.Black.copy(alpha=.6f),r*.07f,Offset(c.x+r*.12f,c.y-r*.12f));drawRect(silver.copy(alpha=.78f),Offset(c.x-r*.2f,c.y+r*.15f),Size(r*.4f,r*.24f)) }
        else -> { drawCircle(silver.copy(alpha=.8f),r*.22f,c); drawCircle(accent,r*.08f,c) }
    }
}

private fun DrawScope.drawFrame(frame: String, c: Offset, r: Float, silver: Color, primary: Color) {
    when(frame){
        "silver_filigree" -> {
            drawCircle(silver.copy(alpha=.78f),r*1.02f,c,style=Stroke(r*.035f)); drawCircle(primary.copy(alpha=.8f),r*.94f,c,style=Stroke(r*.02f))
            for(i in 0 until 4){ val a=(PI/2*i).toFloat(); val p=Offset(c.x+cos(a.toDouble()).toFloat()*r*1.02f,c.y+sin(a.toDouble()).toFloat()*r*1.02f);drawCircle(silver.copy(alpha=.85f),r*.09f,p,style=Stroke(r*.025f)) }
        }
        "rune_metal" -> { drawCircle(primary.copy(alpha=.75f),r*1.03f,c,style=Stroke(r*.12f));drawCircle(silver.copy(alpha=.66f),r*1.03f,c,style=Stroke(r*.025f)) }
        "plain" -> drawCircle(silver.copy(alpha=.62f),r*.98f,c,style=Stroke(r*.035f))
    }
}
