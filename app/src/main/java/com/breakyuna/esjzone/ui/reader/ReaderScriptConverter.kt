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

    private val convertCache = object : LinkedHashMap<Pair<String, ReaderScript>, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, ReaderScript>, String>?): Boolean {
            return size > 512
        }
    }

    private val transliteratorLock = Any()

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
