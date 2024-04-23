package com.wakaztahir.codeeditor.prettify.lang

import com.wakaztahir.codeeditor.prettify.parser.Prettify
import com.wakaztahir.codeeditor.prettify.parser.StylePattern
import com.wakaztahir.codeeditor.utils.new

class LangXML : Lang() {

    companion object {
        val fileExtensions = listOf("xml", "XML", "svg")
    }

    override fun getFileExtensions(): List<String> = fileExtensions

    override val fallthroughStylePatterns = ArrayList<StylePattern>()
    override val shortcutStylePatterns = ArrayList<StylePattern>()

    init {
        shortcutStylePatterns.new(
            Prettify.PR_STRING,
            Regex("(?<!!)\\\\[.*?]\\\\(.*?\\\\)"),
            null
        )
        shortcutStylePatterns.new(
            Prettify.PR_PUNCTUATION,
            Regex("(~{2})([^~]+?)\\\\1"),
            null
        )
        fallthroughStylePatterns.new(
            Prettify.PR_DECLARATION,
            Regex("^#.*?[\\n\\r]")
        )
        fallthroughStylePatterns.new(
            Prettify.PR_STRING,
            Regex("^```[\\s\\S]*?(?:```|$)")
        )
    }
}