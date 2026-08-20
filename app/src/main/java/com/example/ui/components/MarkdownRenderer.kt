package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Code(val language: String, val code: String) : MarkdownBlock()
    data class LaTeX(val formula: String) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class OrderedList(val items: List<String>) : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    object Divider : MarkdownBlock()
}

object MarkdownParser {
    fun parse(raw: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = raw.split("\n")
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // 1. Code block ```
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim()
                val codeBuilder = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeBuilder.append(lines[i]).append("\n")
                    i++
                }
                blocks.add(MarkdownBlock.Code(lang, codeBuilder.toString().trimEnd()))
                i++
                continue
            }

            // 2. LaTeX block $$
            if (trimmed.startsWith("$$")) {
                val formulaBuilder = StringBuilder()
                if (trimmed.length > 2 && trimmed.endsWith("$$") && trimmed.length > 4) {
                    val formula = trimmed.removePrefix("$$").removeSuffix("$$").trim()
                    blocks.add(MarkdownBlock.LaTeX(formula))
                    i++
                    continue
                } else {
                    i++
                    while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                        formulaBuilder.append(lines[i]).append("\n")
                        i++
                    }
                    if (i < lines.size && lines[i].trim().endsWith("$$")) {
                        formulaBuilder.append(lines[i].trim().removeSuffix("$$"))
                    }
                    blocks.add(MarkdownBlock.LaTeX(formulaBuilder.toString().trim()))
                    i++
                    continue
                }
            }

            // 3. Horizontal Rule
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                blocks.add(MarkdownBlock.Divider)
                i++
                continue
            }

            // 4. Heading
            if (trimmed.startsWith("#")) {
                var level = 0
                while (level < trimmed.length && trimmed[level] == '#') {
                    level++
                }
                if (level in 1..6 && trimmed.length > level && trimmed[level] == ' ') {
                    val headingText = trimmed.substring(level).trim()
                    blocks.add(MarkdownBlock.Heading(level, headingText))
                    i++
                    continue
                }
            }

            // 5. Blockquote
            if (trimmed.startsWith(">")) {
                val quoteBuilder = StringBuilder()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteBuilder.append(lines[i].trim().removePrefix(">").trim()).append(" ")
                    i++
                }
                blocks.add(MarkdownBlock.Blockquote(quoteBuilder.toString().trim()))
                continue
            }

            // 6. Table (| col1 | col2 |)
            if (trimmed.startsWith("|") && trimmed.endsWith("|") && i + 1 < lines.size && lines[i + 1].trim().contains("---")) {
                val headers = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                i += 2 // skip header and separator
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    val rowCells = lines[i].trim().split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    rows.add(rowCells)
                    i++
                }
                blocks.add(MarkdownBlock.Table(headers, rows))
                continue
            }

            // 7. Bullet List (- item or * item)
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
                val items = mutableListOf<String>()
                while (i < lines.size && (lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* ") || lines[i].trim().startsWith("• "))) {
                    val itemText = lines[i].trim().substring(2).trim()
                    items.add(itemText)
                    i++
                }
                blocks.add(MarkdownBlock.BulletList(items))
                continue
            }

            // 8. Ordered List (1. item)
            if (Regex("""^\d+\.\s+""").containsMatchIn(trimmed)) {
                val items = mutableListOf<String>()
                while (i < lines.size && Regex("""^\d+\.\s+""").containsMatchIn(lines[i].trim())) {
                    val itemText = lines[i].trim().replaceFirst(Regex("""^\d+\.\s+"""), "")
                    items.add(itemText)
                    i++
                }
                blocks.add(MarkdownBlock.OrderedList(items))
                continue
            }

            // 9. Standard Paragraph (gather non-empty lines)
            if (trimmed.isNotEmpty()) {
                val pBuilder = StringBuilder()
                while (i < lines.size && lines[i].trim().isNotEmpty() &&
                    !lines[i].trim().startsWith("```") &&
                    !lines[i].trim().startsWith("#") &&
                    !lines[i].trim().startsWith(">") &&
                    !lines[i].trim().startsWith("- ") &&
                    !lines[i].trim().startsWith("* ") &&
                    !Regex("""^\d+\.\s+""").containsMatchIn(lines[i].trim())
                ) {
                    pBuilder.append(lines[i]).append("\n")
                    i++
                }
                blocks.add(MarkdownBlock.Paragraph(pBuilder.toString().trim()))
                continue
            }

            i++
        }
        return blocks
    }

    fun parseInlineMarkdown(text: String, primaryColor: Color, onSurfaceColor: Color): AnnotatedString {
        return buildAnnotatedString {
            var idx = 0
            val len = text.length

            while (idx < len) {
                // Bold & Italic (***text***)
                if (text.startsWith("***", idx)) {
                    val end = text.indexOf("***", idx + 3)
                    if (end != -1) {
                        val content = text.substring(idx + 3, end)
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                        append(content)
                        pop()
                        idx = end + 3
                        continue
                    }
                }

                // Bold (**text**)
                if (text.startsWith("**", idx)) {
                    val end = text.indexOf("**", idx + 2)
                    if (end != -1) {
                        val content = text.substring(idx + 2, end)
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(content)
                        pop()
                        idx = end + 2
                        continue
                    }
                }

                // Italic (*text* or _text_)
                if (text[idx] == '*' || text[idx] == '_') {
                    val marker = text[idx]
                    val end = text.indexOf(marker, idx + 1)
                    if (end != -1 && end > idx + 1) {
                        val content = text.substring(idx + 1, end)
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(content)
                        pop()
                        idx = end + 1
                        continue
                    }
                }

                // Inline Code (`code`)
                if (text[idx] == '`') {
                    val end = text.indexOf('`', idx + 1)
                    if (end != -1) {
                        val content = text.substring(idx + 1, end)
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0x22888888),
                                color = primaryColor,
                                fontSize = 13.5.sp
                            )
                        )
                        append(" $content ")
                        pop()
                        idx = end + 1
                        continue
                    }
                }

                // Inline LaTeX ($formula$)
                if (text[idx] == '$' && idx + 1 < len && text[idx + 1] != ' ') {
                    val end = text.indexOf('$', idx + 1)
                    if (end != -1) {
                        val formula = text.substring(idx + 1, end)
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Cursive,
                                fontStyle = FontStyle.Italic,
                                color = primaryColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        append(formula)
                        pop()
                        idx = end + 1
                        continue
                    }
                }

                // Link ([text](url))
                if (text[idx] == '[') {
                    val textEnd = text.indexOf(']', idx + 1)
                    if (textEnd != -1 && textEnd + 1 < len && text[textEnd + 1] == '(') {
                        val urlEnd = text.indexOf(')', textEnd + 2)
                        if (urlEnd != -1) {
                            val linkText = text.substring(idx + 1, textEnd)
                            pushStyle(
                                SpanStyle(
                                    color = primaryColor,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                            append(linkText)
                            pop()
                            idx = urlEnd + 1
                            continue
                        }
                    }
                }

                // Plain character
                append(text[idx])
                idx++
            }
        }
    }
}

@Composable
fun MarkdownRenderer(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Text(
                        text = MarkdownParser.parseInlineMarkdown(block.text, primaryColor, onSurface),
                        style = style,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = MarkdownParser.parseInlineMarkdown(block.text, primaryColor, onSurface),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                            fontSize = 14.5.sp
                        )
                    )
                }
                is MarkdownBlock.Code -> {
                    CodeBlockView(code = block.code, language = block.language)
                }
                is MarkdownBlock.LaTeX -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = block.formula,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 16.sp,
                            color = primaryColor
                        )
                    }
                }
                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .background(primaryColor, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = MarkdownParser.parseInlineMarkdown(block.text, primaryColor, onSurface),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                is MarkdownBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 7.dp, start = 4.dp, end = 8.dp)
                                        .size(5.dp)
                                        .background(primaryColor, CircleShape)
                                )
                                Text(
                                    text = MarkdownParser.parseInlineMarkdown(item, primaryColor, onSurface),
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp)
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.OrderedList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "${idx + 1}.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor
                                    ),
                                    modifier = Modifier.width(22.dp)
                                )
                                Text(
                                    text = MarkdownParser.parseInlineMarkdown(item, primaryColor, onSurface),
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp)
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.Table -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(8.dp)
                    ) {
                        Column {
                            // Header row
                            Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                                block.headers.forEach { header ->
                                    Text(
                                        text = header,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .width(110.dp)
                                            .padding(6.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            // Data rows
                            block.rows.forEach { rowCells ->
                                Row {
                                    rowCells.forEachIndexed { cIdx, cell ->
                                        Text(
                                            text = cell,
                                            fontSize = 13.sp,
                                            modifier = Modifier
                                                .width(110.dp)
                                                .padding(6.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}
