package be.vanmechelen.vrtnws.model

enum class BlockType { HEADING, PARAGRAPH, QUOTE }

/** One renderable block of article body text. */
data class ContentBlock(val type: BlockType, val text: String)

/**
 * The extracted, reader-friendly body of an article: an ordered list of text blocks.
 * [blocks] is empty when extraction could not find usable content (the UI then falls
 * back to the summary + "open on phone").
 */
data class ArticleContent(val blocks: List<ContentBlock>) {
    val isEmpty: Boolean get() = blocks.isEmpty()
    val plainText: String get() = blocks.joinToString("\n\n") { it.text }
}
