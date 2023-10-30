import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.DriverManager
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
const val FAILED_SEND_MESSAGE_CALL_PREFIX = "Failed sendMessage call"
const val FAILED_SEND_MENU_CALL_PREFIX = "Failed sendMenu call"
const val FAILED_SEND_QUESTION_CALL_PREFIX = "Failed sendQuestion call"

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
    val trainer: LearnWordsTrainer,
    var lastQuestion: Question? = null
)

fun main(args: Array<String>) {
    val botToken = args[0]
    val telegramBot = TelegramBotService(botToken)

    var updateId = 0L

    val json = Json { ignoreUnknownKeys = true }
    val userStates = HashMap<Long, UserState>()

    DriverManager.getConnection(URL_DATABASE)
        .use { connection ->
            while (true) {
                Thread.sleep(2000)
                val updatesInOneString = telegramBot.getUpdates(updateId)
                println(updatesInOneString)
                if (updatesInOneString.startsWith(FAILED_GET_UPDATES_CALL_PREFIX)) continue

                val response = json.decodeFromString<Response>(updatesInOneString)
                if (response.result.isEmpty()) continue

                val sortedUpdates = response.result.sortedBy { it.updateId }
                sortedUpdates.forEach {
                    telegramBot.handleUpdate(json, it, userStates, connection)
                }

                updateId = sortedUpdates.last().updateId + 1
            }
    }
}

fun TelegramBotService.handleUpdate(
    json: Json,
    update: Update,
    userStates: HashMap<Long, UserState>,
    connection: Connection
)
{
    val chatId = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: -1
    val userText = update.message?.text?.lowercase()
    val dataText = update.callbackQuery?.data
    val date = update.message?.date ?: update.callbackQuery?.message?.date ?: -1
    val userState = userStates.getOrPut(chatId) {
        UserState(
            LearnWordsTrainer(
                DatabaseUserDictionary(
                    chatId = chatId,
                    date = "${Timestamp(1000 * date)}",
                    username = update.message?.chat?.username ?: update.callbackQuery?.message?.chat?.username,
                    connection = connection
                )
            )
        )
    }
    userState.trainer.updateDate(newDate = "${Timestamp(1000 * date)}")

    when {
        userText == "/start" || userText == "/menu" -> sendMenu(json, chatId)

        userText == "hello" || userText == "hi" ->
            sendMessage(chatId, "Hello! \nSend /start or /menu to switch to main menu")

        dataText == STATISTICS_BUTTON -> {
            sendMessage(chatId, userState.trainer.getStatisticsInString())
            sendMenu(json, chatId)
        }

        dataText == LEARN_WORDS_BUTTON -> checkNextQuestionAndSend(json, userState, chatId)

        dataText == RESET_PROGRESS_BUTTON -> {
            userState.trainer.resetProgress()
            userState.lastQuestion = null
            sendMessage(chatId, "Прогресс сброшен")
            sendMenu(json, chatId)
        }

        dataText != null && dataText.startsWith(CALLBACK_DATA_ANSWER_PREFIX) ->
            processUserAnswerAndSendNewQuestionOrMenu(json, userState, chatId, dataText)

        else -> sendMessage(chatId, "I don't understand you")
    }
}

fun TelegramBotService.processUserAnswerAndSendNewQuestionOrMenu(
    json: Json,
    userState: UserState,
    chatId: Long,
    userData: String,
) {
    val userAnswerIndex = userData.substringAfter(CALLBACK_DATA_ANSWER_PREFIX)
    if (userAnswerIndex == MENU_INDEX) sendMenu(json, chatId)
    else if (userState.lastQuestion != null) {
        sendCheckingAnswerResult(userState, chatId, userAnswerIndex)
        checkNextQuestionAndSend(json, userState, chatId)
    }
}

fun TelegramBotService.checkNextQuestionAndSend(
    json: Json, userState: UserState, chatId: Long
) {
    val question = userState.trainer.getQuestion()
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
) {
    val lastQuestion = userState.lastQuestion
    if (userState.trainer.checkAnswer(userAnswerIndex)) sendMessage(chatId, "Правильно")
    else if (lastQuestion != null) {
        with(lastQuestion) {
            sendMessage(chatId, "Неправильно: \n${rightAnswer.original} - ${rightAnswer.translate}")
        }
    }
}