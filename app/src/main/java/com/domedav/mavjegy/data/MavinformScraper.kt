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
        val bodyMatch = Regex(
            """class="field-body">(.*)""",
            RegexOption.DOT_MATCHES_ALL
        ).find(html) ?: return ""

        val chunk = bodyMatch.groupValues[1].take(3000)

        val yellowMatch = Regex(
            """class="yellowbox">(.*?)</div>""",
            RegexOption.DOT_MATCHES_ALL
        ).find(chunk)
        if (yellowMatch != null) return cleanHtml(yellowMatch.groupValues[1])

        val pMatch = Regex(
            """<p>(.*?)</p>""",
            RegexOption.DOT_MATCHES_ALL
        ).find(chunk)
        if (pMatch != null) return cleanHtml(pMatch.groupValues[1])

        return cleanHtml(chunk)
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
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
