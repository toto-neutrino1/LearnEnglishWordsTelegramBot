import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

fun main(args: Array<String>) {
    val botToken = args[0]
    var updateId = 0
    val client: HttpClient = HttpClient.newBuilder().build()

    while(true) {
        Thread.sleep(2000)
        val updates = getUpdates(botToken, updateId, client)
        println(updates)

        val startUpdateId = updates.lastIndexOf("update_id")
        if (startUpdateId == -1) continue
        val endUpdateId = updates.indexOf(",", startIndex = startUpdateId)

        val updateIdString = updates.substring(startUpdateId + 11, endUpdateId)
        updateId = updateIdString.toInt() + 1
    }
}

fun getUpdates(botToken: String, updateId: Int, client: HttpClient): String {
    val urlGetUpdates = "https://api.telegram.org/bot$botToken/getUpdates?offset=$updateId"

    val request = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())

    return response.body()
}