package com.macrotracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary

/**
 * Lightweight Markdown renderer for GitHub release notes.
 * Supports headings, bold/italic, inline code, markdown links, and bare URLs.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = TextSecondary,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = 18.sp,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Heading -> {
                    Text(
                        text = buildInlineMarkdown(
                            text = block.text,
                            color = TextPrimary,
                            linkColor = Primary,
                        ),
                        fontSize = when (block.level) {
                            1 -> (fontSize.value + 6).sp
                            2 -> (fontSize.value + 4).sp
                            else -> (fontSize.value + 2).sp
                        },
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        lineHeight = (lineHeight.value + 4).sp,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp, bottom = 4.dp),
                    )
                }
                is MdBlock.Paragraph -> {
                    InlineMarkdownText(
                        text = block.text,
                        color = color,
                        linkColor = Primary,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                is MdBlock.Bullet -> {
                    Row(modifier = Modifier.padding(bottom = 5.dp)) {
                        Text(
                            text = "•",
                            color = color,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            modifier = Modifier.width(16.dp),
                        )
                        InlineMarkdownText(
                            text = block.text,
                            color = color,
                            linkColor = Primary,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                is MdBlock.Numbered -> {
                    Row(modifier = Modifier.padding(bottom = 5.dp)) {
                        Text(
                            text = "${block.number}.",
                            color = color,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            modifier = Modifier.width(22.dp),
                        )
                        InlineMarkdownText(
                            text = block.text,
                            color = color,
                            linkColor = Primary,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                is MdBlock.Spacer -> Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun InlineMarkdownText(
    text: String,
    color: Color,
    linkColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(text, color, linkColor) {
        buildInlineMarkdown(
            text = text,
            color = color,
            linkColor = linkColor,
        )
    }

    BasicText(
        text = annotated,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
        ),
    )
}

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class Bullet(val text: String) : MdBlock()
    data class Numbered(val number: Int, val text: String) : MdBlock()
    data object Spacer : MdBlock()
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    if (raw.isBlank()) return emptyList()
    val lines = raw.replace("\r\n", "\n").lines()
    val out = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotEmpty()) out += MdBlock.Paragraph(text)
        paragraph.clear()
    }

    for (line in lines) {
        val trimmed = line.trimEnd()
        when {
            trimmed.isBlank() -> {
                flushParagraph()
                if (out.isNotEmpty() && out.last() !is MdBlock.Spacer) {
                    out += MdBlock.Spacer
                }
            }
            trimmed.matches(Regex("""^#{1,6}\s+.+""")) -> {
                flushParagraph()
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                out += MdBlock.Heading(level, trimmed.drop(level).trim())
            }
            trimmed.matches(Regex("""^[-*+]\s+.+""")) -> {
                flushParagraph()
                out += MdBlock.Bullet(trimmed.replace(Regex("""^[-*+]\s+"""), ""))
            }
            trimmed.matches(Regex("""^\d+\.\s+.+""")) -> {
                flushParagraph()
                val number = trimmed.substringBefore('.').toIntOrNull() ?: 1
                out += MdBlock.Numbered(number, trimmed.replace(Regex("""^\d+\.\s+"""), ""))
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed.trim())
            }
        }
    }
    flushParagraph()
    return out.dropLastWhile { it is MdBlock.Spacer }
}

private fun buildInlineMarkdown(
    text: String,
    color: Color,
    linkColor: Color,
) = buildAnnotatedString {
    // Markdown links, bare URLs, **bold**, *italic*, `code`
    val regex = Regex(
        """(\[[^\]]+\]\([^)]+\)|https?://[^\s<>\)\]]+|`[^`]+`|\*\*[^*]+\*\*|\*[^*]+\*)""",
    )
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Medium,
        ),
    )
    var cursor = 0
    for (match in regex.findAll(text)) {
        if (match.range.first > cursor) {
            withStyle(SpanStyle(color = color)) {
                append(text.substring(cursor, match.range.first))
            }
        }
        val token = match.value
        when {
            token.startsWith("[") && token.contains("](") -> {
                val label = token.substringAfter("[").substringBefore("]")
                val url = token.substringAfter("](").removeSuffix(")").trim()
                // Default LinkAnnotation.Url opens via LocalUriHandler on tap.
                withLink(LinkAnnotation.Url(url, linkStyles)) {
                    append(label.ifBlank { url })
                }
            }
            token.startsWith("http://") || token.startsWith("https://") -> {
                val url = token.trimEnd('.', ',', ';')
                val label = when {
                    url.contains("/pull/") -> "PR #${url.substringAfterLast('/')}"
                    url.contains("/compare/") -> "Changelog"
                    url.contains("/releases/") -> "Release"
                    else -> url.removePrefix("https://").removePrefix("http://")
                }
                withLink(LinkAnnotation.Url(url, linkStyles)) {
                    append(label)
                }
            }
            token.startsWith("**") && token.endsWith("**") -> {
                withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                    append(token.removeSurrounding("**"))
                }
            }
            token.startsWith("*") && token.endsWith("*") -> {
                withStyle(SpanStyle(color = color, fontStyle = FontStyle.Italic)) {
                    append(token.removeSurrounding("*"))
                }
            }
            token.startsWith("`") && token.endsWith("`") -> {
                withStyle(
                    SpanStyle(
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        background = color.copy(alpha = 0.12f),
                    ),
                ) {
                    append(token.removeSurrounding("`"))
                }
            }
            else -> withStyle(SpanStyle(color = color)) { append(token) }
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        withStyle(SpanStyle(color = color)) {
            append(text.substring(cursor))
        }
    }
}
