package com.github.kate1234567.ui.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object ChatList : Screen("chat_list")
    data object Messages : Screen("messages/{channel}") {
        fun createRoute(channel: String): String {
            val encodedChannel = URLEncoder.encode(channel, StandardCharsets.UTF_8.toString())
            return "messages/$encodedChannel"
        }
    }
    data object Image : Screen("image/{link}") {
        fun createRoute(link: String): String {
            val encodedLink = URLEncoder.encode(link, StandardCharsets.UTF_8.toString())
            return "image/$encodedLink"
        }
    }
}
