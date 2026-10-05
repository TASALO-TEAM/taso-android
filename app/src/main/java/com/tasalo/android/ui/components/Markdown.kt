package com.tasalo.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tasalo.android.util.MarkdownParser
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
            Text("•", Modifier.width(16.dp))
            Text(styled(block.spans, linkColor, codeBackground), style = MaterialTheme.typography.bodyMedium)
        }
        is MdBlock.Numbered -> Row(modifier) {
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
            AsyncImage(
                model = block.url,
                contentDescription = block.alt.ifBlank { null },
                contentScale = ContentScale.FillWidth,
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Text(
                styled(listOf(MdSpan("🖼 " + block.alt.ifBlank { "Imagen" }, url = block.url)), linkColor, codeBackground),
                modifier,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        MdBlock.Rule -> HorizontalDivider(modifier)
    }
}

private fun styled(
    spans: List<MdSpan>,
    linkColor: androidx.compose.ui.graphics.Color,
    codeBackground: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            fontFamily = if (span.code) FontFamily.Monospace else null,
            background = if (span.code) codeBackground else androidx.compose.ui.graphics.Color.Unspecified,
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
