import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class TelegramBotService(
    private val botToken: String
) {
    private val client: HttpClient = HttpClient.newBuilder().build()
    private val greetings = listOf("hello", "hi")

    fun getUpdates(updateId: Int): String {
        val urlGetUpdates = "https://api.telegram.org/bot$botToken/getUpdates?offset=$updateId"
        return getResponse(urlGetUpdates)
    }

    fun sendMessage(chatId: Long, userText: String): String {
        val text =
            if (userText.lowercase() in greetings) "Hello"
            else "I+don't+understand+you"
        val urlSendMessage = "https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&text=$text"

        return getResponse(urlSendMessage)
    }

    private fun getResponse(sendUrl: String): String {
        val request = HttpRequest.newBuilder().uri(URI.create(sendUrl)).build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }
}