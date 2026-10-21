package com.vm.vector.ui.screens.brain

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.SlateGray

/**
 * Live markdown rendering for an editable text field. Syntax characters are hidden once
 * styled, except on the lines inside [revealLines] (the cursor's lines), which show the
 * raw markdown so it stays editable.
 */
data class MarkdownVisualTransformation(private val revealLines: IntRange? = null) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = renderMarkdown(text.text, revealLines)
}

/** Character range of the whole lines touched by the selection [start]..[end] in [text]. */
fun markdownLinesAround(text: String, start: Int, end: Int): IntRange {
    val from = minOf(start, end).coerceIn(0, text.length)
    val to = maxOf(start, end).coerceIn(0, text.length)
    val lineStart = if (from == 0) 0 else text.lastIndexOf('\n', from - 1) + 1
    val lineEnd = text.indexOf('\n', to).let { if (it < 0) text.length else it }
    return lineStart..lineEnd
}

private val MarkerStyle = SpanStyle(color = IconGray)
private val ListMarkerStyle = SpanStyle(color = ElectricBlue, fontWeight = FontWeight.Bold)
private val CodeStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFEEF1F5), color = NavyDeep)
private val QuoteStyle = SpanStyle(color = SlateGray, fontStyle = FontStyle.Italic)
private val DoneStyle = SpanStyle(color = IconGray, textDecoration = TextDecoration.LineThrough)
private val LinkStyle = SpanStyle(color = ElectricBlue, textDecoration = TextDecoration.Underline)

private val Heading = Regex("""^(#{1,6})\s""")
private val Quote = Regex("""^(\s*)>\s?""")
private val Task = Regex("""^(\s*)[-*+]\s\[([ xX])]\s""")
private val Bullet = Regex("""^(\s*)([-*+]|\d+[.)])\s""")
private val Rule = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")

private val InlineCode = Regex("""`([^`]+)`""")
private val Bold = Regex("""(\*\*|__)(?=\S)(.+?)(?<=\S)\1""")
private val ItalicStar = Regex("""(?<![*\w])\*(?=[^\s*])(.+?)(?<=[^\s*])\*(?!\*)""")
private val ItalicUnderscore = Regex("""(?<![_\w])_(?=[^\s_])(.+?)(?<=[^\s_])_(?![_\w])""")
private val Strike = Regex("""~~(?=\S)(.+?)(?<=\S)~~""")
private val Link = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")

private fun headingStyle(level: Int) = SpanStyle(
    fontWeight = FontWeight.Bold,
    color = NavyDeep,
    fontSize = when (level) {
        1 -> 26.sp
        2 -> 22.sp
        3 -> 19.sp
        else -> 17.sp
    },
)

private fun renderMarkdown(source: String, revealLines: IntRange?): TransformedText {
    val pass = MarkdownPass(source)
    var lineStart = 0
    var inFence = false
    for (line in source.split('\n')) {
        val lineEnd = lineStart + line.length
        pass.revealed = revealLines != null && revealLines.first <= lineEnd && revealLines.last >= lineStart
        when {
            line.trimStart().startsWith("```") -> {
                pass.style(CodeStyle, lineStart, lineEnd)
                pass.marker(lineStart, lineEnd)
                inFence = !inFence
            }
            inFence -> pass.style(CodeStyle, lineStart, lineEnd)
            else -> pass.styleLine(line, lineStart)
        }
        lineStart = lineEnd + 1
    }
    return pass.build()
}

/** Styles are added on source offsets; hidden/replaced characters are applied in [build]. */
private class MarkdownPass(private val source: String) {
    private val styled = AnnotatedString.Builder(source)
    private val hidden = BooleanArray(source.length)
    private val replaced = HashMap<Int, Char>()
    var revealed = false

    fun style(style: SpanStyle, from: Int, to: Int) = styled.addStyle(style, from, to)

    fun marker(from: Int, to: Int, style: SpanStyle = MarkerStyle) {
        styled.addStyle(style, from, to)
        if (!revealed) for (i in from until to) hidden[i] = true
    }

    fun replace(index: Int, char: Char) {
        if (!revealed) replaced[index] = char
    }

    fun styleLine(line: String, start: Int) {
        val end = start + line.length
        Rule.find(line)?.let {
            style(MarkerStyle, start, end)
            line.forEachIndexed { i, c -> if (!c.isWhitespace()) replace(start + i, '─') }
            return
        }
        Heading.find(line)?.let { match ->
            style(headingStyle(match.groupValues[1].length), start, end)
            styleInline(line, start)
            marker(start, start + match.range.last + 1)
            return
        }
        Quote.find(line)?.let { match ->
            val arrow = start + match.groupValues[1].length
            style(QuoteStyle, start, end)
            styleInline(line, start)
            style(ListMarkerStyle, arrow, arrow + 1)
            replace(arrow, '▎')
            return
        }
        Task.find(line)?.let { match ->
            val dash = start + match.groupValues[1].length
            val markerEnd = start + match.range.last + 1
            val checked = match.groupValues[2].isNotBlank()
            styleInline(line, start)
            if (checked) style(DoneStyle, markerEnd, end)
            style(ListMarkerStyle, dash, markerEnd)
            marker(dash, dash + 2, ListMarkerStyle)
            replace(dash + 2, if (checked) '☑' else '☐')
            marker(dash + 3, dash + 5, ListMarkerStyle)
            return
        }
        Bullet.find(line)?.let { match ->
            val bullet = start + match.groupValues[1].length
            styleInline(line, start)
            style(ListMarkerStyle, start, start + match.range.last + 1)
            if (match.groupValues[2].length == 1 && !match.groupValues[2][0].isDigit()) replace(bullet, '•')
            return
        }
        styleInline(line, start)
    }

    private fun styleInline(line: String, start: Int) {
        val codeRanges = InlineCode.findAll(line).map { it.range }.toList()
        fun inCode(range: IntRange) = codeRanges.any { range.first <= it.last && it.first <= range.last }

        fun wrapped(regex: Regex, markerLength: Int, spanStyle: SpanStyle) {
            regex.findAll(line).filterNot { inCode(it.range) }.forEach { match ->
                val from = start + match.range.first
                val to = start + match.range.last + 1
                style(spanStyle, from, to)
                marker(from, from + markerLength)
                marker(to - markerLength, to)
            }
        }

        wrapped(Bold, 2, SpanStyle(fontWeight = FontWeight.Bold))
        wrapped(ItalicStar, 1, SpanStyle(fontStyle = FontStyle.Italic))
        wrapped(ItalicUnderscore, 1, SpanStyle(fontStyle = FontStyle.Italic))
        wrapped(Strike, 2, SpanStyle(textDecoration = TextDecoration.LineThrough))

        Link.findAll(line).filterNot { inCode(it.range) }.forEach { match ->
            val from = start + match.range.first
            val to = start + match.range.last + 1
            val label = match.groups[1]!!.range
            val labelFrom = start + label.first
            val labelTo = start + label.last + 1
            style(LinkStyle, labelFrom, labelTo)
            marker(from, labelFrom)
            marker(labelTo, to)
        }

        codeRanges.forEach { range ->
            val from = start + range.first
            val to = start + range.last + 1
            style(CodeStyle, from, to)
            marker(from, from + 1)
            marker(to - 1, to)
        }
    }

    fun build(): TransformedText {
        val n = source.length
        val toTransformed = IntArray(n + 1)
        val toOriginal = ArrayList<Int>(n + 1)
        val visible = StringBuilder(n)
        for (i in 0 until n) {
            toTransformed[i] = visible.length
            if (!hidden[i]) {
                toOriginal.add(i)
                visible.append(replaced[i] ?: source[i])
            }
        }
        toTransformed[n] = visible.length
        toOriginal.add(n)

        val sourceStyled = styled.toAnnotatedString()
        val text = buildAnnotatedString {
            append(visible.toString())
            sourceStyled.spanStyles.forEach { range ->
                val from = toTransformed[range.start]
                val to = toTransformed[range.end]
                if (to > from) addStyle(range.item, from, to)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = toTransformed[offset.coerceIn(0, n)]
            override fun transformedToOriginal(offset: Int): Int = toOriginal[offset.coerceIn(0, toOriginal.lastIndex)]
        }
        return TransformedText(text, mapping)
    }
}
