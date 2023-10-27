import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

const val HOST = "https://api.telegram.org"

@Serializable
data class SendMessageRequest(
    @SerialName("chat_id")
    val chatId: Long,
    @SerialName("text")
    val text: String,
    @SerialName("reply_markup")
    val replyMarkup: ReplyMarkup
)

@Serializable
data class ReplyMarkup(
    @SerialName("inline_keyboard")
    val inlineKeyboard: List<List<InlineButton>>
)

@Serializable
data class InlineButton(
    @SerialName("text")
    val text: String,
    @SerialName("callback_data")
    val callbackData: String
)

class TelegramBotService(
    private val botToken: String
) {
    private val client: HttpClient = HttpClient.newBuilder().build()
    private val sendMessageURLPrefix = "$HOST/bot$botToken/sendMessage"
    private val getUpdatesURLPrefix = "$HOST/bot$botToken/getUpdates"

    fun getUpdates(updateId: Long): String {
        val urlGetUpdates = "$getUpdatesURLPrefix?offset=$updateId"
        val requestUpdateResult = kotlin.runCatching {
            getRequestResult(urlGetUpdates)
        }

        requestUpdateResult.onFailure {
            return "$FAILED_GET_UPDATES_CALL_PREFIX: $it"
        }

        return requestUpdateResult.getOrDefault("DEFAULT_UPDATE_STRING")
    }

    fun sendMessage(chatId: Long, sendText: String): String {
        val text = URLEncoder.encode(sendText, StandardCharsets.UTF_8)
        val urlSendMessage = "$sendMessageURLPrefix?chat_id=$chatId&text=$text"
        return getRequestResult(urlSendMessage)
    }

    fun sendMenu(json: Json, chatId: Long): String {
        val sendMenuBody = getJSONMenuBody(json, chatId)
        return postRequestResult(sendMenuBody)
    }

    fun sendQuestion(json: Json, chatId: Long, question: Question): String {
        val sendQuestionBody = getJSONQuestionBody(json, chatId, question)
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

    private fun getJSONMenuBody(json: Json, chatId: Long): String {
        val menuBody = SendMessageRequest(
            chatId = chatId,
            text = MAIN_MENU,
            replyMarkup = ReplyMarkup(
                listOf(
                    listOf(
                        InlineButton(
                            text = LEARN_WORDS,
                            callbackData = LEARN_WORDS_BUTTON
                        ),
                        InlineButton(
                            text = STATISTICS,
                            callbackData = STATISTICS_BUTTON
                        )
                    ),
                    listOf(
                        InlineButton(
                            text = RESET_PROGRESS,
                            callbackData = RESET_PROGRESS_BUTTON
                        )
                    )
                )
            )
        )

        return json.encodeToString(menuBody)
    }

    private fun getJSONQuestionBody(json: Json, chatId: Long, question: Question): String {
        val questionBody = SendMessageRequest(
            chatId = chatId,
            text = "Слово ${question.rightAnswer.original} переводится как:",
            replyMarkup = ReplyMarkup(
                    question.questionWords.mapIndexed { index, word ->
                            listOf(
                                InlineButton(
                                    text = word.translate,
                                    callbackData = "$CALLBACK_DATA_ANSWER_PREFIX${index + 1}"
                                )
                            )
                    }.plus(
                        listOf(
                            listOf(
                                InlineButton(
                                    text = "Вернуться в $MAIN_MENU",
                                    callbackData = "${CALLBACK_DATA_ANSWER_PREFIX}0"
                                )
                            )
                        )
                    )
            )
        )

        return json.encodeToString(questionBody)
    }
}