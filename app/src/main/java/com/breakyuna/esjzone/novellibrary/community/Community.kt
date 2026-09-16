package com.breakyuna.esjzone.novellibrary.community

import java.io.Serializable

data class ForumCategory(
    val id: String,
    val groupName: String?,
    val name: String,
    val description: String?,
    val postCount: Int?,
    val url: String
) : Serializable

const val FORUM_GROUP_ESJ = "ESJ-曉朔國度"
const val FORUM_GROUP_TIANKONG = "天空大公國"

val ESJ_FORUM_CATEGORY_IDS: Set<String> = setOf(
    "1584680829",
    "1584678947",
    "1584622251",
    "1584622325",
    "1584679807"
)

val TIANKONG_FORUM_CATEGORY_IDS: Set<String> = setOf(
    "1584622376",
    "1584622613",
    "1584622628"
)

fun resolveForumGroupName(categoryId: String, rawGroupName: String? = null): String {
    val trimmed = rawGroupName?.trim().orEmpty()
    if (trimmed.contains("曉朔") || trimmed.contains("ESJ", ignoreCase = true)) {
        return FORUM_GROUP_ESJ
    }
    if (trimmed.contains("天空")) {
        return FORUM_GROUP_TIANKONG
    }
    if (categoryId in ESJ_FORUM_CATEGORY_IDS) {
        return FORUM_GROUP_ESJ
    }
    if (categoryId in TIANKONG_FORUM_CATEGORY_IDS) {
        return FORUM_GROUP_TIANKONG
    }
    return trimmed
}

fun groupForumCategories(categories: List<ForumCategory>): Map<String, List<ForumCategory>> {
    val esjList = mutableListOf<ForumCategory>()
    val tiankongList = mutableListOf<ForumCategory>()
    val otherMap = linkedMapOf<String, MutableList<ForumCategory>>()

    for (cat in categories) {
        val resolved = resolveForumGroupName(cat.id, cat.groupName)
        when (resolved) {
            FORUM_GROUP_ESJ -> esjList.add(
                if (cat.groupName == FORUM_GROUP_ESJ) cat else cat.copy(groupName = FORUM_GROUP_ESJ)
            )
            FORUM_GROUP_TIANKONG -> tiankongList.add(
                if (cat.groupName == FORUM_GROUP_TIANKONG) cat else cat.copy(groupName = FORUM_GROUP_TIANKONG)
            )
            else -> otherMap.getOrPut(resolved) { mutableListOf() }.add(cat)
        }
    }

    val unassigned = otherMap[""]
    if (esjList.isEmpty() && tiankongList.isEmpty() && unassigned != null && unassigned.size >= 8) {
        esjList.addAll(unassigned.take(5).map { it.copy(groupName = FORUM_GROUP_ESJ) })
        tiankongList.addAll(unassigned.drop(5).take(3).map { it.copy(groupName = FORUM_GROUP_TIANKONG) })
        val remaining = unassigned.drop(8)
        if (remaining.isEmpty()) {
            otherMap.remove("")
        } else {
            otherMap[""] = remaining.toMutableList()
        }
    }

    val result = linkedMapOf<String, List<ForumCategory>>()
    if (esjList.isNotEmpty()) {
        result[FORUM_GROUP_ESJ] = esjList
    }
    if (tiankongList.isNotEmpty()) {
        result[FORUM_GROUP_TIANKONG] = tiankongList
    }
    for ((group, list) in otherMap) {
        if (list.isNotEmpty()) {
            result[group] = list
        }
    }
    return result
}

data class ForumThread(
    val categoryId: String,
    val id: String,
    val title: String,
    val topicCount: Int?,
    val replyCount: Int?,
    val lastPostDate: String?,
    val url: String
) : Serializable

data class ForumTopic(
    val boardId: String,
    val id: String,
    val title: String,
    val author: String?,
    val createdAt: String?,
    val replyCount: Int?,
    val viewCount: Int?,
    val lastReplyAt: String?,
    val url: String
) : Serializable

data class ForumPost(
    val boardId: String,
    val id: String,
    val title: String,
    val author: String?,
    val createdAt: String?,
    val contentHtml: String,
    val contentText: String,
    val comments: List<com.breakyuna.esjzone.novellibrary.novel.Comment>,
    val url: String
) : Serializable
