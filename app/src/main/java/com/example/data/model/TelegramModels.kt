package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TelegramResponse<T>(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "result") val result: T?,
    @Json(name = "description") val description: String? = null,
    @Json(name = "error_code") val errorCode: Int? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUser(
    @Json(name = "id") val id: Long,
    @Json(name = "is_bot") val isBot: Boolean = false,
    @Json(name = "first_name") val firstName: String = "",
    @Json(name = "last_name") val lastName: String? = null,
    @Json(name = "username") val username: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramChat(
    @Json(name = "id") val id: Long,
    @Json(name = "type") val type: String = "private",
    @Json(name = "title") val title: String? = null,
    @Json(name = "username") val username: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramMessage(
    @Json(name = "message_id") val messageId: Long,
    @Json(name = "from") val from: TelegramUser? = null,
    @Json(name = "chat") val chat: TelegramChat,
    @Json(name = "date") val date: Long = 0,
    @Json(name = "text") val text: String? = null,
    @Json(name = "caption") val caption: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramCallbackQuery(
    @Json(name = "id") val id: String,
    @Json(name = "from") val from: TelegramUser,
    @Json(name = "message") val message: TelegramMessage? = null,
    @Json(name = "data") val data: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUpdate(
    @Json(name = "update_id") val updateId: Long,
    @Json(name = "message") val message: TelegramMessage? = null,
    @Json(name = "callback_query") val callbackQuery: TelegramCallbackQuery? = null
)

@JsonClass(generateAdapter = true)
data class InlineKeyboardButton(
    @Json(name = "text") val text: String,
    @Json(name = "callback_data") val callbackData: String? = null
)

@JsonClass(generateAdapter = true)
data class InlineKeyboardMarkup(
    @Json(name = "inline_keyboard") val inlineKeyboard: List<List<InlineKeyboardButton>>
)

@JsonClass(generateAdapter = true)
data class SendMessagePayload(
    @Json(name = "chat_id") val chatId: Long,
    @Json(name = "text") val text: String,
    @Json(name = "parse_mode") val parseMode: String? = "HTML",
    @Json(name = "reply_markup") val replyMarkup: InlineKeyboardMarkup? = null
)
