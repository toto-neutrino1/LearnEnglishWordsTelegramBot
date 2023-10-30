const val DEFAULT_NUM_OF_ANSWER_OPTIONS = 4

data class Word(
    val original: String,
    val translate: String,
    var correctAnswersCount: Int = 0
)

data class Statistics(
    val numOfAllWords: Int,
    val numOfLearnedWords: Int,
    val learnedPercent: Int
)

data class Question(
    val questionWords: List<Word>,
    val rightAnswer: Word
)

// https://studynow.ru/dicta/allwords - english words were parsed from here

class LearnWordsTrainer(
    private val dictionary: IUserDictionary,
    private val numOfAnswerOptions: Int = DEFAULT_NUM_OF_ANSWER_OPTIONS,
) {

    private lateinit var question: Question
    private var lastRecordDate: String = ""

    fun updateDate(newDate: String) {
        lastRecordDate = newDate
    }

    fun getStatistics(): Statistics {
        val numOfAllWords = dictionary.getDictionarySize()
        val numOfLearnedWords = dictionary.getNumOfLearnedWords()
        val learnedPercent = 100 * numOfLearnedWords / numOfAllWords

        return Statistics(numOfAllWords, numOfLearnedWords, learnedPercent)
    }

    fun getStatisticsInString() = with(getStatistics()) {
        "Выучено $numOfLearnedWords из $numOfAllWords слов | $learnedPercent%"
    }

    fun getQuestion(): Question? {
        val unlearnedWords = dictionary.getUnlearnedWords()

        if (unlearnedWords.isEmpty()) return null

        val questionWords = getRandomQuestionWords(unlearnedWords)
        val rightWord =
            if (unlearnedWords.size < numOfAnswerOptions) unlearnedWords.random()
            else questionWords.random()

        question = Question(questionWords, rightWord)

        return question
    }

    fun checkAnswer(userAnswer: String): Boolean {
        with(question) {
            if (userAnswer.toIntOrNull() == questionWords.indexOf(rightAnswer) + 1) {
                dictionary.setCorrectAnswersCount(
                    rightAnswer, rightAnswer.correctAnswersCount + 1, lastRecordDate
                )
                return true
            }
        }
        return false
    }

    fun resetProgress() = dictionary.resetProgress(lastRecordDate)

    private fun getRandomQuestionWords(unlearnedWords: List<Word>): List<Word> {
            return if (unlearnedWords.size < numOfAnswerOptions) {
                val learnedWords = dictionary.getLearnedWords().shuffled()
                (unlearnedWords + learnedWords.take(numOfAnswerOptions - unlearnedWords.size)).shuffled()
            } else {
                unlearnedWords.shuffled().take(numOfAnswerOptions)
            }
    }
}