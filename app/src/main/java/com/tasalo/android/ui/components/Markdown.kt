package com.tasalo.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tasalo.android.util.MarkdownParser
import com.tasalo.android.util.MdAlign
import com.tasalo.android.util.MdBlock
import com.tasalo.android.util.MdDialect
import com.tasalo.android.util.MdSpan

/**
 * Lector de Markdown para las notas de versión, los mensajes del equipo y los posts del blog.
 * Con `images = true` las imágenes (solo https) se cargan al mostrarse; si no, se muestran como enlace.
 */
@Composable
fun MarkdownView(
    text: String,
    modifier: Modifier = Modifier,
    dialect: MdDialect = MdDialect.STANDARD,
    images: Boolean = false,
) {
    val blocks = remember(text, dialect) { MarkdownParser.parse(text, dialect) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block -> MarkdownBlockView(block, images = images) }
    }
}

/** Un solo bloque: permite dibujar textos largos en una lista perezosa (el detalle de un post del blog). */
@Composable
fun MarkdownBlockView(block: MdBlock, modifier: Modifier = Modifier, images: Boolean = false) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant

    when (block) {
        is MdBlock.Heading -> Text(
            styled(block.spans, linkColor, codeBackground),
            style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
            fontWeight = FontWeight.Bold,
            modifier = modifier.padding(top = 4.dp),
        )
        is MdBlock.Bullet -> Row(modifier.padding(start = (block.indent * 16).dp)) {
            val marker = when (block.checked) {
                null -> "•"
                true -> "☑"
                false -> "☐"
            }
            Text(marker, Modifier.width(if (block.checked == null) 16.dp else 24.dp))
            Text(styled(block.spans, linkColor, codeBackground), style = MaterialTheme.typography.bodyMedium)
        }
        is MdBlock.Numbered -> Row(modifier.padding(start = (block.indent * 16).dp)) {
            Text("${block.number}.", Modifier.width(24.dp))
            Text(styled(block.spans, linkColor, codeBackground), style = MaterialTheme.typography.bodyMedium)
        }
        is MdBlock.Paragraph ->
            Text(styled(block.spans, linkColor, codeBackground), modifier, style = MaterialTheme.typography.bodyMedium)
        is MdBlock.Code -> Text(
            block.text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier
                .fillMaxWidth()
                .background(codeBackground, RoundedCornerShape(8.dp))
                .padding(8.dp),
        )
        is MdBlock.Image -> if (images) {
            val uriHandler = LocalUriHandler.current
            val link = block.link
            AsyncImage(
                model = block.url,
                contentDescription = block.alt.ifBlank { null },
                contentScale = ContentScale.FillWidth,
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (link != null) Modifier.clickable { runCatching { uriHandler.openUri(link) } } else Modifier),
            )
        } else {
            Text(
                styled(listOf(MdSpan("🖼 " + block.alt.ifBlank { "Imagen" }, url = block.url)), linkColor, codeBackground),
                modifier,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        is MdBlock.Quote -> Row(modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(linkColor.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
            )
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.blocks.forEach { MarkdownBlockView(it, images = images) }
                }
            }
        }
        is MdBlock.Table -> MarkdownTable(block, modifier, linkColor, codeBackground)
        MdBlock.Rule -> HorizontalDivider(modifier)
    }
}

/** Tabla con desplazamiento horizontal: cada columna se ajusta al texto más largo (con un mínimo y un máximo). */
@Composable
private fun MarkdownTable(block: MdBlock.Table, modifier: Modifier, linkColor: Color, codeBackground: Color) {
    val widths = remember(block) {
        block.header.indices.map { column ->
            val longest = (listOf(block.header[column]) + block.rows.map { it[column] })
                .maxOf { cell -> cell.sumOf { span -> span.text.length } }
            (longest.coerceIn(5, 40) * 7 + 20).dp
        }
    }
    Column(
        modifier
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
    ) {
        TableRowView(block.header, block.aligns, widths, header = true, linkColor, codeBackground)
        block.rows.forEach { TableRowView(it, block.aligns, widths, header = false, linkColor, codeBackground) }
    }
}

@Composable
private fun TableRowView(
    cells: List<List<MdSpan>>,
    aligns: List<MdAlign>,
    widths: List<Dp>,
    header: Boolean,
    linkColor: Color,
    codeBackground: Color,
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val headerBackground = MaterialTheme.colorScheme.surfaceVariant
    Row(Modifier.height(IntrinsicSize.Min)) {
        cells.forEachIndexed { column, spans ->
            Box(
                Modifier
                    .width(widths[column])
                    .fillMaxHeight()
                    .background(if (header) headerBackground else Color.Transparent)
                    .border(0.5.dp, line)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    styled(spans, linkColor, codeBackground),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (header) FontWeight.Bold else null,
                    textAlign = when (aligns.getOrNull(column)) {
                        MdAlign.CENTER -> TextAlign.Center
                        MdAlign.END -> TextAlign.End
                        else -> TextAlign.Start
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun styled(
    spans: List<MdSpan>,
    linkColor: Color,
    codeBackground: Color,
): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            fontFamily = if (span.code) FontFamily.Monospace else null,
            background = if (span.code) codeBackground else Color.Unspecified,
            textDecoration = if (span.strike) TextDecoration.LineThrough else null,
        )
        val url = span.url
        if (url != null) {
            withLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                ),
            ) {
                withStyle(style) { append(span.text) }
            }
        } else {
            withStyle(style) { append(span.text) }
        }
    }
}
