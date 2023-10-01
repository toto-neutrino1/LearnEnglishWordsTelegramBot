import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

class LearnWordsTrainerDatabase(
    private val chatId: Long = 0,
    private val numOfAnswerOptions: Int = DEFAULT_NUM_OF_ANSWER_OPTIONS,
    private val learningThreshold: Int = DEFAULT_LEARNING_THRESHOLD
) {
    private lateinit var question: Question
    private var numOfLearnedWords: Int = 0
    private var lastRecordDate: String = ""

    fun updateDate(newDate: String) {
        lastRecordDate = newDate
    }

    fun getStatisticsInString(databaseService: DatabaseService) = with(getStatistics(databaseService)) {
        "Выучено $numOfLearnedWords из $numOfAllWords слов | $learnedPercent%"
    }

    private fun getStatistics(databaseService: DatabaseService): Statistics {
        val numOfAllWords = databaseService.getDictionarySize()
        val learnedPercent = 100 * numOfLearnedWords / numOfAllWords

        return Statistics(numOfAllWords, numOfLearnedWords, learnedPercent)
    }

    fun getQuestion(databaseService: DatabaseService): Question? {
        DriverManager.getConnection(URL_DATABASE)
            .use { connection ->
                val statement = connection.createStatement()
                val unlearnedWordsTable: ResultSet = getUnlearnedWordsTable(chatId, learningThreshold, statement)
                if (unlearnedWordsTable.getInt("id") == 0) return null

                val unlearnedWordsSize = databaseService.getDictionarySize() - numOfLearnedWords
                question = getRandomQuestionWords(unlearnedWordsTable, unlearnedWordsSize, databaseService, statement)
            }

        return question
    }

    fun checkAnswer(userAnswer: String, databaseService: DatabaseService): Boolean {
        with(question) {
            if (userAnswer.toIntOrNull() == questionWords.indexOf(rightAnswer) + 1) {
                if (databaseService.addRightAnswerAndCheckIsLearnedWord(
                        chatId, rightAnswer.original, lastRecordDate, learningThreshold
                )
                    ) numOfLearnedWords++

                return true
            }
        }

        return false
    }

    fun resetProgress(databaseService: DatabaseService) {
        databaseService.deleteAllUserAnswers(chatId)
        numOfLearnedWords = 0
    }

    private fun getRandomQuestionWords(
        unlearnedWordsTable: ResultSet, unlearnedWordsSize: Int, databaseService: DatabaseService, statement: Statement
    ): Question {
        val unlearnedWordsIds = List(unlearnedWordsSize) {
            if(unlearnedWordsTable.next()) unlearnedWordsTable.getInt("id")
            else 0
        }

        val answerIds =  if (unlearnedWordsSize < numOfAnswerOptions) {
            val learnedWordsIds =
                (FIRST_WORDS_INDEX..databaseService.getDictionarySize()).filter { it !in unlearnedWordsIds }
            (unlearnedWordsIds + learnedWordsIds.shuffled().take(numOfAnswerOptions - unlearnedWordsSize)).shuffled()
        } else {
            unlearnedWordsIds.shuffled().take(numOfAnswerOptions)
        }

        val rightAnswerId =
            if (unlearnedWordsSize < numOfAnswerOptions) unlearnedWordsIds.random()
            else answerIds.random()

        return takeAnswerWordsFromTable(answerIds, rightAnswerId, statement)
    }

    private fun getUnlearnedWordsTable(chatId: Long, learningThreshold: Int, statement: Statement): ResultSet {
        val userId = statement.executeQuery("select id from $USERS_TABLE where chat_id = $chatId")
            .getInt("id")
        return statement.executeQuery(
            "select * from $WORDS_TABLE where id not in (select word_id from $USER_ANSWERS_TABLE where " +
                    "user_id = $userId and correct_answer_count >= $learningThreshold)"
        )
    }

    private fun takeAnswerWordsFromTable(answerIds: List<Int>, rightAnswerId: Int, statement: Statement): Question {
        val randomWords = mutableListOf<Word>()
        var rightAnswer = Word("", "")
        answerIds.forEach {
            val rs = statement.executeQuery("select * from $WORDS_TABLE where id = $it")
            randomWords.add(Word(rs.getString("text"), rs.getString("translate")))
            if (it == rightAnswerId) {
                rightAnswer = Word(rs.getString("text"), rs.getString("translate"))
            }
        }
        return Question(randomWords.toList(), rightAnswer)
    }
}