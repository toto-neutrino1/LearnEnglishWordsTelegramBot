fun main(args: Array<String>) {
    val botToken = args[0]
    val telegramBot = TelegramBotService(botToken)

    var updateId = 0
    var chatId: Long
    val updateIdRegex = "\"update_id\":(\\d+?),".toRegex()
    val messageTextRegex = "\"text\":\"(.+?)\"".toRegex()
    val chatIdRegex = "\"chat\":\\{\"id\":(\\d+?),".toRegex()

    while(true) {
        Thread.sleep(2000)
        val updates = telegramBot.getUpdates(updateId)
        println(updates)

        val updateIdString = getValueFromJson(updateIdRegex, updates) ?: continue
        val chatIdString = getValueFromJson(chatIdRegex, updates) ?: continue
        val userText = getValueFromJson(messageTextRegex, updates) ?: continue

        updateId = updateIdString.toInt() + 1
        chatId = chatIdString.toLong()
        val message = telegramBot.sendMessage(chatId, userText)
        println(message)
    }
}

fun getValueFromJson(regex: Regex, updates: String): String? = regex.find(updates)?.groups?.get(1)?.value