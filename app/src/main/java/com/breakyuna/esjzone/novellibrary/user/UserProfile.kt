package com.breakyuna.esjzone.novellibrary.user

data class UserProfile(
    val name: String,
    val avatarUrl: String,
    /** Current account experience points, when exposed by the profile page. */
    val exp: Int? = null,
    /** Current account rank, for example `F級 Lv2` or `SSS級 Max`. */
    val level: String? = null
)
