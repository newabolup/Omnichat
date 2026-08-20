package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SyntaxHighlighter {
    private val keywords = setOf(
        "abstract", "as", "break", "case", "catch", "class", "const", "continue", "def", "default",
        "do", "else", "enum", "export", "extends", "false", "final", "finally", "fn", "for", "from",
        "func", "function", "if", "implements", "import", "in", "inline", "instanceof", "interface",
        "internal", "is", "let", "match", "mut", "new", "null", "open", "override", "package",
        "private", "protected", "pub", "public", "return", "sealed", "self", "struct", "super",
        "switch", "sync", "this", "throw", "true", "try", "type", "typeof", "val", "var", "void",
        "while", "yield", "async", "await", "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE",
        "CREATE", "TABLE", "JOIN", "ORDER", "BY", "GROUP", "AND", "OR", "NOT"
    )

    private val types = setOf(
        "String", "Int", "Boolean", "Float", "Double", "Long", "Short", "Byte", "Char", "Unit", "Any",
        "List", "Map", "Set", "Array", "number", "string", "boolean", "void", "any", "object", "Promise"
    )

    private val keywordColor = Color(0xFFFF7B72) // Coral / Pink
    private val stringColor = Color(0xFFA5D6FF)  // Light blue
    private val commentColor = Color(0xFF8B949E) // Gray
    private val numberColor = Color(0xFF79C0FF)  // Cyan
    private val typeColor = Color(0xFFFFA657)    // Orange
    private val functionColor = Color(0xFFD2A8FF)// Purple
    private val defaultTextColor = Color(0xFFE6EDF3)

    fun highlight(code: String, language: String): AnnotatedString {
        return buildAnnotatedString {
            val lines = code.split("\n")
            lines.forEachIndexed { lineIdx, line ->
                var i = 0
                while (i < line.length) {
                    // Check for single line comments (// or #)
                    if ((line.startsWith("//", i) && language.lowercase() != "python" && language.lowercase() != "bash") ||
                        (line.startsWith("#", i) && (language.lowercase() == "python" || language.lowercase() == "bash" || language.lowercase() == "shell" || language.lowercase() == "yaml" || language.lowercase() == "yml"))
                    ) {
                        val comment = line.substring(i)
                        pushStyle(SpanStyle(color = commentColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                        append(comment)
                        pop()
                        i = line.length
                        break
                    }

                    // Check for string literal (quotes)
                    if (line[i] == '"' || line[i] == '\'') {
                        val quoteChar = line[i]
                        val endIdx = line.indexOf(quoteChar, i + 1)
                        if (endIdx != -1) {
                            val strVal = line.substring(i, endIdx + 1)
                            pushStyle(SpanStyle(color = stringColor))
                            append(strVal)
                            pop()
                            i = endIdx + 1
                            continue
                        }
                    }

                    // Check for word tokens
                    if (line[i].isLetter() || line[i] == '_') {
                        var end = i
                        while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_')) {
                            end++
                        }
                        val word = line.substring(i, end)
                        when {
                            keywords.contains(word) -> {
                                pushStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold))
                                append(word)
                                pop()
                            }
                            types.contains(word) -> {
                                pushStyle(SpanStyle(color = typeColor))
                                append(word)
                                pop()
                            }
                            end < line.length && line[end] == '(' -> {
                                pushStyle(SpanStyle(color = functionColor))
                                append(word)
                                pop()
                            }
                            else -> {
                                pushStyle(SpanStyle(color = defaultTextColor))
                                append(word)
                                pop()
                            }
                        }
                        i = end
                        continue
                    }

                    // Check for numbers
                    if (line[i].isDigit()) {
                        var end = i
                        while (end < line.length && (line[end].isDigit() || line[end] == '.')) {
                            end++
                        }
                        val num = line.substring(i, end)
                        pushStyle(SpanStyle(color = numberColor))
                        append(num)
                        pop()
                        i = end
                        continue
                    }

                    // Punctuation / other
                    pushStyle(SpanStyle(color = defaultTextColor))
                    append(line[i].toString())
                    pop()
                    i++
                }

                if (lineIdx < lines.size - 1) {
                    append("\n")
                }
            }
        }
    }
}

@Composable
fun CodeBlockView(
    code: String,
    language: String = "",
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    val highlightedCode = remember(code, language) {
        SyntaxHighlighter.highlight(code, language)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF16161E))
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF20212B))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifEmpty { "code" }.lowercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFA1A1AA)
                )
            )

            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(code))
                    isCopied = true
                    Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        delay(2000)
                        isCopied = false
                    }
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = if (isCopied) Color(0xFF10A37F) else Color(0xFFA1A1AA),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Code body with horizontal scroll
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = highlightedCode,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}
