const val MAIN_MENU = "Основное меню"
const val LEARN_WORDS = "Изучать слова"
const val STATISTICS = "Статистика"
const val LEARN_WORDS_BUTTON = "learning_words_clicked"
const val STATISTICS_BUTTON = "statistics_clicked"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"

fun main(args: Array<String>) {
    val botToken = args[0]
    val telegramBot = TelegramBotService(botToken)

    var updateId = 0
    var chatId: Long
    val updateIdRegex = "\"update_id\":(\\d+?),".toRegex()
    val messageTextRegex = "\"text\":\"(.+?)\"".toRegex()
    val chatIdRegex = "\"chat\":\\{\"id\":(\\d+?),".toRegex()
    val dataRegex = "\"data\":\"(.+?)\"".toRegex()

    var question: Question? = null

    val trainer = LearnWordsTrainer()

    while(true) {
        Thread.sleep(2000)
        val updates = telegramBot.getUpdates(updateId)
        println(updates)

        val updateIdString = getValueFromJson(updateIdRegex, updates) ?: continue
        val chatIdString = getValueFromJson(chatIdRegex, updates) ?: continue
        val userText = getValueFromJson(messageTextRegex, updates)?.lowercase() ?: continue
        val dataText: String? = getValueFromJson(dataRegex, updates)

        updateId = updateIdString.toInt() + 1
        chatId = chatIdString.toLong()

        when {
            userText == "/start" || userText == "/menu" -> telegramBot.sendMenu(chatId)

            userText == "hello" || userText == "hi" ->
                telegramBot.sendMessage(chatId, "Hello! \nSend /start or /menu to switch to main menu")

            dataText == STATISTICS_BUTTON -> telegramBot.sendMessage(chatId, trainer.getStatisticsInString())

            dataText == LEARN_WORDS_BUTTON -> question = telegramBot.checkNextQuestionAndSend(trainer, chatId)

            dataText != null && dataText.startsWith(CALLBACK_DATA_ANSWER_PREFIX) ->
                question =
                    telegramBot.processUserAnswerAndSendNewQuestionOrMenu(trainer, chatId, dataText, question)

            else -> telegramBot.sendMessage(chatId, "I don't understand you")
        }
    }
}

fun getValueFromJson(regex: Regex, updates: String): String? = regex.find(updates)?.groups?.get(1)?.value

fun LearnWordsTrainer.getStatisticsInString() = with(getStatistics()) {
    "Выучено $numOfLearnedWords из $numOfAllWords слов | $learnedPercent%"
}

fun TelegramBotService.processUserAnswerAndSendNewQuestionOrMenu(
    trainer: LearnWordsTrainer,
    chatId: Long,
    userData: String,
    lastQuestion: Question?,
): Question? {
    val userAnswerIndex = userData.substringAfter(CALLBACK_DATA_ANSWER_PREFIX)
    if (userAnswerIndex == "0") {
        sendMenu(chatId)
        return null
    }
    else if (lastQuestion == null) return null

    sendCheckingAnswerResult(trainer, lastQuestion, chatId, userAnswerIndex)
    return checkNextQuestionAndSend(trainer, chatId)
}

fun TelegramBotService.checkNextQuestionAndSend(trainer: LearnWordsTrainer, chatId: Long): Question? {
    val question = trainer.getQuestion()
    if (question == null) sendMessage(chatId, "Вы выучили все слова")
    else sendQuestion(chatId, question)

    return question
}

fun TelegramBotService.sendCheckingAnswerResult(
    trainer: LearnWordsTrainer,
    question: Question,
    chatId: Long,
    userAnswerIndex: String) {

    if (trainer.checkAnswer(userAnswerIndex)) sendMessage(chatId, "Правильно")
    else sendMessage(chatId, "Неправильно: \n${question.rightAnswer.original} - ${question.rightAnswer.translate}")
}