import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.sql.Timestamp

const val MENU_INDEX = "0"
const val MAIN_MENU = "Основное меню"
const val LEARN_WORDS = "Изучать слова"
const val STATISTICS = "Статистика"
const val RESET_PROGRESS = "Сбросить прогресс"
const val LEARN_WORDS_BUTTON = "learning_words_clicked"
const val STATISTICS_BUTTON = "statistics_clicked"
const val RESET_PROGRESS_BUTTON = "reset_clicked"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"
const val FAILED_GET_UPDATES_CALL_PREFIX = "Failed getUpdates call"

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
    val chat: Chat,
    val date: Long
)

@Serializable
data class CallbackQuery(
    val data: String,
    val message: Message
)

@Serializable
data class Chat(
    val id: Long,
    val username: String? = null
)

data class UserState(
    val trainer: LearnWordsTrainerDatabase,
    var lastQuestion: Question? = null
)

fun main(args: Array<String>) {
    val botToken = args[0]
    val telegramBot = TelegramBotService(botToken)

    var updateId = 0L

    val json = Json { ignoreUnknownKeys = true }
    val userStates = HashMap<Long, UserState>()
    val databaseService = DatabaseService()

    while(true) {
        Thread.sleep(2000)
        val updatesInOneString = telegramBot.getUpdates(updateId)
        println(updatesInOneString)
        if (updatesInOneString.startsWith(FAILED_GET_UPDATES_CALL_PREFIX)) continue

        val response = json.decodeFromString<Response>(updatesInOneString)
        if (response.result.isEmpty()) continue

        val sortedUpdates = response.result.sortedBy { it.updateId }
        sortedUpdates.forEach {
            telegramBot.handleUpdate(json, it, userStates, databaseService)
        }

        updateId = sortedUpdates.last().updateId + 1
    }
}

fun TelegramBotService.handleUpdate(
    json: Json, update: Update, userStates: HashMap<Long, UserState>, databaseService: DatabaseService
)
{
    val chatId = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: -1
    val userText = update.message?.text?.lowercase()
    val dataText = update.callbackQuery?.data
    val date = update.message?.date ?: update.callbackQuery?.message?.date ?: -1
    val userState = userStates.getOrPut(chatId) {
        val username = update.message?.chat?.username ?: update.callbackQuery?.message?.chat?.username
        databaseService.addNewUser(chatId, "${Timestamp(1000 * date)}", username)
        UserState(LearnWordsTrainerDatabase(chatId))
    }
    userState.trainer.updateDate(newDate = "${Timestamp(1000 * date)}")

    when {
        userText == "/start" || userText == "/menu" -> sendMenu(json, chatId)

        userText == "hello" || userText == "hi" ->
            sendMessage(chatId, "Hello! \nSend /start or /menu to switch to main menu")

        dataText == STATISTICS_BUTTON -> {
            sendMessage(chatId, userState.trainer.getStatisticsInString(databaseService))
            sendMenu(json, chatId)
        }

        dataText == LEARN_WORDS_BUTTON -> checkNextQuestionAndSend(json, userState, chatId, databaseService)

        dataText == RESET_PROGRESS_BUTTON -> {
            userState.trainer.resetProgress(databaseService)
            userState.lastQuestion = null
            sendMessage(chatId, "Прогресс сброшен")
            sendMenu(json, chatId)
        }

        dataText != null && dataText.startsWith(CALLBACK_DATA_ANSWER_PREFIX) ->
            processUserAnswerAndSendNewQuestionOrMenu(json, userState, chatId, dataText, databaseService)

        else -> sendMessage(chatId, "I don't understand you")
    }
}

fun TelegramBotService.processUserAnswerAndSendNewQuestionOrMenu(
    json: Json,
    userState: UserState,
    chatId: Long,
    userData: String,
    databaseService: DatabaseService
) {
    val userAnswerIndex = userData.substringAfter(CALLBACK_DATA_ANSWER_PREFIX)
    if (userAnswerIndex == MENU_INDEX) sendMenu(json, chatId)
    else if (userState.lastQuestion != null) {
        sendCheckingAnswerResult(userState, chatId, userAnswerIndex, databaseService)
        checkNextQuestionAndSend(json, userState, chatId, databaseService)
    }
}

fun TelegramBotService.checkNextQuestionAndSend(
    json: Json, userState: UserState, chatId: Long, databaseService: DatabaseService
) {
    val question = userState.trainer.getQuestion(databaseService)
    userState.lastQuestion = question
    if (question == null) {
        sendMessage(chatId, "Вы выучили все слова")
        sendMenu(json, chatId)
    }
    else sendQuestion(json, chatId, question)
}

fun TelegramBotService.sendCheckingAnswerResult(
    userState: UserState,
    chatId: Long,
    userAnswerIndex: String,
    databaseService: DatabaseService
) {
    val lastQuestion = userState.lastQuestion
    if (userState.trainer.checkAnswer(userAnswerIndex, databaseService)) sendMessage(chatId, "Правильно")
    else if (lastQuestion != null) {
        with(lastQuestion) {
            sendMessage(chatId, "Неправильно: \n${rightAnswer.original} - ${rightAnswer.translate}")
        }
    }
}