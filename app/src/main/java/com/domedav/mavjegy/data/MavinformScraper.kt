package com.domedav.mavjegy.data

object MavinformScraper {

    private const val BASE_URL = "https://www.mavcsoport.hu"

    fun parseList(html: String): List<MavinformItem> {
        val items = mutableListOf<MavinformItem>()
        val blocks = html.split("""<div class="custom-news-item">""")

        for (block in blocks.drop(1)) {
            val category = detectCategory(block)

            val titleMatch = Regex(
                """<h3 class="field-content">\s*<a href="([^"]*)"[^>]*>(.*?)</a>""",
                RegexOption.DOT_MATCHES_ALL
            ).find(block) ?: continue

            val link = titleMatch.groupValues[1].let {
                if (it.startsWith("http")) it else BASE_URL + it
            }
            val title = cleanHtml(titleMatch.groupValues[2])
            if (title.isBlank()) continue

            items.add(
                MavinformItem(
                    title = title,
                    link = link,
                    category = category
                )
            )
        }
        return items
    }

    fun parseDetail(html: String): String {
        // Minden field-body blokk kiegyensúlyozott kinyerése (a beágyazott
        // div-ek miatt a sima (.*?)</div> regex széttörne), a leghosszabb nyer
        val bodies = extractDivsByClass(html, "field-body")
        if (bodies.isEmpty()) return ""
        return bodies.maxOf { joinBlocks(it) }
    }

    /** Kiegyensúlyozott <div class="...">...</div> kinyerés: az összes találat belső HTML-je */
    private fun extractDivsByClass(html: String, cssClass: String): List<String> {
        val out = mutableListOf<String>()
        val openTag = Regex("""<div\b[^>]*class="$cssClass"[^>]*>""")
        val anyDiv = Regex("""</?div\b[^>]*>""")
        for (m in openTag.findAll(html)) {
            var depth = 0
            var innerStart = -1
            for (t in anyDiv.findAll(html, m.range.first)) {
                if (t.value.startsWith("</")) depth-- else {
                    if (depth == 0 && t.range.first == m.range.first) innerStart = t.range.last + 1
                    depth++
                }
                if (depth == 0 && innerStart >= 0) {
                    out.add(html.substring(innerStart, t.range.first))
                    break
                }
            }
        }
        return out
    }

    /** Blokk-elemek (p, li, h1-h6) sorrendben, üresek kihagyva, dupla újsorral joinolva */
    private fun joinBlocks(innerHtml: String): String {
        val block = Regex(
            """<(p|li|h[1-6])\b[^>]*>(.*?)</\1>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val parts = block.findAll(innerHtml)
            .map { cleanHtml(it.groupValues[2]) }
            .filter { it.isNotBlank() }
            .toList()
        if (parts.isNotEmpty()) return parts.joinToString("\n\n")
        // nincs <p>: nyers szöveg (pl. csak yellowbox/div-es tartalom)
        return cleanHtml(innerHtml).take(2000)
    }

    private fun detectCategory(block: String): String {
        val lower = block.lowercase()
        return when {
            "vonat_ikon" in lower -> "vonat"
            "volan-busz_ikon" in lower -> "busz"
            "helyi-busz_ikon" in lower -> "helyi_busz"
            else -> "ismeretlen"
        }
    }

    private fun cleanHtml(text: String): String {
        return text
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&ndash;", "–")
            .replace("&gt;", ">")
            .replace("&lt;", "<")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
