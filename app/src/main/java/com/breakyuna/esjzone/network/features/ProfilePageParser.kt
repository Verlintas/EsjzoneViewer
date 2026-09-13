package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.HtmlSelector
import com.breakyuna.esjzone.network.JsoupHtmlSelector
import org.jsoup.nodes.Document

private val profileSelector: HtmlSelector = JsoupHtmlSelector

private const val PROFILE_NAME_SELECTORS =
    "aside h4, form.form-edit h4, .profile-username, [data-profile-username]"

private const val PROFILE_AVATAR_SELECTORS =
    "aside img, .profile-avatar, [data-profile-avatar], img[data-avatar]"

private val PROFILE_EXPERIENCE_PATTERN = Regex(
    "(?i)(?<![\\d\\w])([\\d,]+)\\s*(?:exp|經驗值|经验值)"
)
private val PROFILE_LEVEL_PATTERN = Regex(
    "(?i)(?<![\\w])([A-Z]{1,4}\\s*[級级]\\s*(?:Lv\\.?\\s*\\d+|Max))(?![\\w])"
)

/**
 * The account card currently renders the value as `7264 exp`. Keep the
 * selector broad enough for the site's template variants, but search the
 * member card/sidebar before the whole page so unrelated post text cannot be
 * mistaken for account metadata.
 */
private fun profileCardText(document: Document): Sequence<String> = sequence {
    document.select("aside, .user-info, .profile-card, .profile-summary, [data-profile-card]")
        .map { it.text() }
        .filter { it.isNotBlank() }
        .forEach { yield(it) }
    document.body()?.text()?.takeIf { it.isNotBlank() }?.let { yield(it) }
}

/** Shared CSS profile marker used by both profile loading and auth probing. */
internal fun profileName(document: Document): String =
    profileSelector.first(document, PROFILE_NAME_SELECTORS)
        ?.text()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "User"

internal fun hasProfileMarker(document: Document): Boolean =
    profileSelector.first(document, PROFILE_NAME_SELECTORS)
        ?.text()
        ?.trim()
        ?.isNullOrBlank() == false

/** Keeps the legacy relative avatar URL contract while accepting lazy markup. */
internal fun profileAvatarUrl(document: Document): String =
    profileSelector.first(document, PROFILE_AVATAR_SELECTORS)?.let { image ->
        image.attr("src").ifBlank { image.attr("data-src") }
    }.orEmpty()

internal fun profileExperience(document: Document): Int? =
    profileCardText(document)
        .mapNotNull { PROFILE_EXPERIENCE_PATTERN.find(it)?.groupValues?.getOrNull(1) }
        .mapNotNull { it.replace(",", "").toIntOrNull() }
        .firstOrNull()

internal fun profileLevel(document: Document): String? =
    profileCardText(document)
        .mapNotNull { PROFILE_LEVEL_PATTERN.find(it)?.groupValues?.getOrNull(1) }
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .firstOrNull()
