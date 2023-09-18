import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

class TelegramBotService(
    private val botToken: String
) {
    private val client: HttpClient = HttpClient.newBuilder().build()
    private val sendMessageURLPrefix = "https://api.telegram.org/bot$botToken/sendMessage"
    private val getUpdatesURLPrefix = "https://api.telegram.org/bot$botToken/getUpdates"

    fun getUpdates(updateId: Int): String {
        val urlGetUpdates = "$getUpdatesURLPrefix?offset=$updateId"
        return getRequestResult(urlGetUpdates)
    }

    fun sendMessage(chatId: Long, sendText: String): String {
        val text = URLEncoder.encode(sendText, StandardCharsets.UTF_8)
        val urlSendMessage = "$sendMessageURLPrefix?chat_id=$chatId&text=$text"

        return getRequestResult(urlSendMessage)
    }

    fun sendMenu(chatId: Long): String {
        val sendMenuBody = getJSONMenuBody(chatId)
        return postRequestResult(sendMenuBody)
    }

    fun sendQuestion(chatId: Long, question: Question): String {
        val sendQuestionBody = getJSONQuestionBody(chatId, question)
        return postRequestResult(sendQuestionBody)
    }

    private fun getRequestResult(sendUrl: String): String {
        val request = HttpRequest.newBuilder().uri(URI.create(sendUrl)).build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }

    private fun postRequestResult(requestBody: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(sendMessageURLPrefix))
            .header("Content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }

    private fun getJSONMenuBody(chatId: Long) = """
            {
                "chat_id": $chatId,
                "text": "$MAIN_MENU",
                "reply_markup": {
                    "inline_keyboard": [
                        [
                            {
                                "text": "$LEARN_WORDS",
                                "callback_data": "$LEARN_WORDS_BUTTON"
                            },
                            {
                                "text": "$STATISTICS",
                                "callback_data": "$STATISTICS_BUTTON"
                            }
                        ]
                    ]
                }
            }
        """.trimIndent()

    private fun getJSONQuestionBody(chatId: Long, question: Question): String {
        val inlineKeyboardAnswersButtons = question.questionWords.mapIndexed { index, word ->
            """
               {
                   "text": "${word.translate}",
                   "callback_data": "$CALLBACK_DATA_ANSWER_PREFIX${index + 1}"
               }
            """.trimIndent()
        }.joinToString(separator = "],\n[")

        val inlineKeyboard = """
            "inline_keyboard": [
                        [
                            $inlineKeyboardAnswersButtons
                        ],
                        [
                            {
                                "text": "Вернуться в $MAIN_MENU",
                                "callback_data": "${CALLBACK_DATA_ANSWER_PREFIX}0"
                            }
                        ]
                    ]
        """.trimIndent()

        return """
            {
                "chat_id": $chatId,
                "text": "Слово ${question.rightAnswer.original} переводится как:",
                "reply_markup": {
                    $inlineKeyboard
                }
            }
        """.trimIndent()
    }
}