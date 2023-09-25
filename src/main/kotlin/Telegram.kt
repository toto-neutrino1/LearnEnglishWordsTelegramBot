import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val MAIN_MENU = "Основное меню"
const val LEARN_WORDS = "Изучать слова"
const val STATISTICS = "Статистика"
const val RESET_PROGRESS = "Сбросить прогресс"
const val LEARN_WORDS_BUTTON = "learning_words_clicked"
const val STATISTICS_BUTTON = "statistics_clicked"
const val RESET_PROGRESS_BUTTON = "reset_clicked"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"

@Serializable
data class Response(val result: List<Update>)

@Serializable
data class Update(
    @SerialName("update_id")
    val updateId: Long,
    @SerialName("message")
    val message: Message? = null,
    @SerialName("callback_query")
    val callbackQuery: CallbackQuery? = null
)

@Serializable
data class Message(
    val text: String,
    val chat: Chat
)

@Serializable
data class CallbackQuery(
    val data: String,
    val message: Message
)

@Serializable
data class Chat(val id: Long)

fun main(args: Array<String>) {
    val botToken = args[0]
    val telegramBot = TelegramBotService(botToken)

    var updateId = 0L

    val json = Json { ignoreUnknownKeys = true }
    val trainers = HashMap<Long, LearnWordsTrainer>()
    val lastQuestions = HashMap<Long, Question?>()

    while(true) {
        Thread.sleep(2000)
        val updatesInOneString = telegramBot.getUpdates(updateId)
        println(updatesInOneString)

        val response = json.decodeFromString<Response>(updatesInOneString)
        if (response.result.isEmpty()) continue

        val sortedUpdates = response.result.sortedBy { it.updateId }
        sortedUpdates.forEach {
            telegramBot.handleUpdate(json, it, trainers, lastQuestions)
        }

        updateId = sortedUpdates.last().updateId + 1
    }
}

fun TelegramBotService.handleUpdate(
    json: Json, update: Update, trainers: HashMap<Long, LearnWordsTrainer>, lastQuestions: HashMap<Long, Question?>
)
{
    val chatId = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: -1
    val userText = update.message?.text?.lowercase()
    val dataText = update.callbackQuery?.data
    val trainer = trainers.getOrPut(chatId) { LearnWordsTrainer("$chatId.txt") }
    if (!lastQuestions.containsKey(chatId)) lastQuestions[chatId] = null

    when {
        userText == "/start" || userText == "/menu" -> sendMenu(json, chatId)

        userText == "hello" || userText == "hi" ->
            sendMessage(chatId, "Hello! \nSend /start or /menu to switch to main menu")

        dataText == STATISTICS_BUTTON -> sendMessage(chatId, trainer.getStatisticsInString())

        dataText == LEARN_WORDS_BUTTON -> checkNextQuestionAndSend(json, trainer, chatId, lastQuestions)

        dataText == RESET_PROGRESS_BUTTON -> {
            trainer.resetProgress()
            lastQuestions[chatId] = null
            sendMessage(chatId, "Прогресс сброшен")
        }

        dataText != null && dataText.startsWith(CALLBACK_DATA_ANSWER_PREFIX) ->
            processUserAnswerAndSendNewQuestionOrMenu(json, trainer, lastQuestions, chatId, dataText)

        else -> sendMessage(chatId, "I don't understand you")
    }
}

fun TelegramBotService.processUserAnswerAndSendNewQuestionOrMenu(
    json: Json,
    trainer: LearnWordsTrainer,
    lastQuestions: HashMap<Long, Question?>,
    chatId: Long,
    userData: String,
) {
    val userAnswerIndex = userData.substringAfter(CALLBACK_DATA_ANSWER_PREFIX)
    val lastQuestion = lastQuestions[chatId]
    if (userAnswerIndex == "0") sendMenu(json, chatId)
    else if (lastQuestion != null) {
        sendCheckingAnswerResult(trainer, lastQuestion, chatId, userAnswerIndex)
        checkNextQuestionAndSend(json, trainer, chatId, lastQuestions)
    }
}

fun TelegramBotService.checkNextQuestionAndSend(
    json: Json, trainer: LearnWordsTrainer, chatId: Long, lastQuestions: HashMap<Long, Question?>
) {
    val question = trainer.getQuestion()
    lastQuestions[chatId] = question
    if (question == null) sendMessage(chatId, "Вы выучили все слова")
    else sendQuestion(json, chatId, question)
}

fun TelegramBotService.sendCheckingAnswerResult(
    trainer: LearnWordsTrainer,
    question: Question,
    chatId: Long,
    userAnswerIndex: String
) {
    if (trainer.checkAnswer(userAnswerIndex)) sendMessage(chatId, "Правильно")
    else sendMessage(
        chatId, "Неправильно: \n${question.rightAnswer.original} - ${question.rightAnswer.translate}"
    )
}