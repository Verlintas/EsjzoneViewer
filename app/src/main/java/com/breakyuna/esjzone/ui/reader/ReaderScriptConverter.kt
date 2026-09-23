package com.breakyuna.esjzone.ui.reader

import android.icu.text.Transliterator

/**
 * Converts the reader's source text on demand so the original parsed chapter model
 * remains untouched. Android's ICU implementation handles phrase-independent
 * Traditional/Simplified character mappings and is available on the app's minSdk.
 */
object ReaderScriptConverter {

    private val traditionalToSimplified by lazy {
        Transliterator.getInstance("Traditional-Simplified")
    }

    private val simplifiedToTraditional by lazy {
        Transliterator.getInstance("Simplified-Traditional")
    }

    private val convertCache = object : LinkedHashMap<Pair<String, ReaderScript>, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, ReaderScript>, String>?): Boolean {
            return size > 4096
        }
    }

    private val transliteratorLock = Any()

    /** Converts on the caller's thread; callers should use a background dispatcher. */
    fun preload(texts: Iterable<String>, script: ReaderScript) {
        if (script == ReaderScript.ORIGINAL) return
        for (text in texts) {
            if (text.isNotEmpty()) {
                convert(text, script)
            }
        }
    }

    /** Pre-warms all text and ruby readings from a reader document AST in the background. */
    fun preload(document: com.breakyuna.esjzone.domain.reader.ReaderChapterDocument, script: ReaderScript) {
        if (script == ReaderScript.ORIGINAL) return
        if (document.chapter.name.isNotEmpty()) {
            convert(document.chapter.name, script)
        }
        for (block in document.blocks) {
            when (block) {
                is com.breakyuna.esjzone.domain.reader.ReaderBlock.Paragraph -> {
                    for (part in block.parts) {
                        if (part.value.isNotEmpty()) convert(part.value, script)
                        part.ruby?.reading?.let { if (it.isNotEmpty()) convert(it, script) }
                    }
                }
                is com.breakyuna.esjzone.domain.reader.ReaderBlock.Text -> {
                    if (block.value.isNotEmpty()) convert(block.value, script)
                    block.ruby?.reading?.let { if (it.isNotEmpty()) convert(it, script) }
                }
                else -> Unit
            }
        }
    }

    /** Builds a complete immutable transform table before a reader window is rendered. */
    fun snapshot(
        documents: Iterable<com.breakyuna.esjzone.domain.reader.ReaderChapterDocument>,
        script: ReaderScript
    ): Map<String, String> {
        if (script == ReaderScript.ORIGINAL) return emptyMap()
        val result = HashMap<String, String>()
        fun add(value: String?) {
            if (!value.isNullOrEmpty() && value !in result) result[value] = convert(value, script)
        }
        documents.forEach { document ->
            add(document.chapter.name)
            document.blocks.forEach { block ->
                when (block) {
                    is com.breakyuna.esjzone.domain.reader.ReaderBlock.Paragraph -> block.parts.forEach { part ->
                        add(part.value)
                        add(part.ruby?.reading)
                    }
                    is com.breakyuna.esjzone.domain.reader.ReaderBlock.Text -> {
                        add(block.value)
                        add(block.ruby?.reading)
                    }
                    else -> Unit
                }
            }
        }
        return result
    }

    fun convert(text: String, script: ReaderScript): String = when (script) {
        ReaderScript.ORIGINAL -> text
        ReaderScript.SIMPLIFIED -> {
            if (text.isEmpty()) ""
            else synchronized(convertCache) {
                convertCache[text to script]
            } ?: run {
                val converted = synchronized(transliteratorLock) {
                    traditionalToSimplified.transliterate(text)
                }
                synchronized(convertCache) {
                    convertCache[text to script] = converted
                }
                converted
            }
        }
        ReaderScript.TRADITIONAL -> {
            if (text.isEmpty()) ""
            else synchronized(convertCache) {
                convertCache[text to script]
            } ?: run {
                val converted = synchronized(transliteratorLock) {
                    simplifiedToTraditional.transliterate(text)
                }
                synchronized(convertCache) {
                    convertCache[text to script] = converted
                }
                converted
            }
        }
    }
}
