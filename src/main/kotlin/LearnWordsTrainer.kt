import java.io.File

const val DEFAULT_FILE_NAME = "words.txt"
const val DEFAULT_NUM_OF_ANSWER_OPTIONS = 4
const val DEFAULT_LEARNING_THRESHOLD = 3

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
    private val fileName: String = DEFAULT_FILE_NAME,
    private val numOfAnswerOptions: Int = DEFAULT_NUM_OF_ANSWER_OPTIONS,
    private val learningThreshold: Int = DEFAULT_LEARNING_THRESHOLD
) {
    private val dictionary = try {
        loadDictionary()
    } catch (e: Exception) {
        throw IllegalArgumentException("Некорректный файл")
    }

    private lateinit var question: Question

    fun getStatistics(): Statistics {
        val numOfAllWords = dictionary.size
        val numOfLearnedWords = dictionary.filter { it.correctAnswersCount >= learningThreshold }.size
        val learnedPercent = 100 * numOfLearnedWords / numOfAllWords

        return Statistics(numOfAllWords, numOfLearnedWords, learnedPercent)
    }

    fun getStatisticsInString() = with(getStatistics()) {
        "Выучено $numOfLearnedWords из $numOfAllWords слов | $learnedPercent%"
    }

    fun getQuestion(): Question? {
        val unlearnedWords = getUnlearnedWords()

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
                rightAnswer.correctAnswersCount++
                saveDictionary()
                return true
            }
        }
        return false
    }

    fun resetProgress() {
        dictionary.forEach { it.correctAnswersCount = 0 }
        saveDictionary()
    }

    private fun getUnlearnedWords() = dictionary.filter { it.correctAnswersCount < learningThreshold }

    private fun getRandomQuestionWords(unlearnedWords: List<Word>): List<Word> {
            return if (unlearnedWords.size < numOfAnswerOptions) {
                val learnedWords = dictionary.filter { it.correctAnswersCount >= learningThreshold }.shuffled()
                (unlearnedWords + learnedWords.take(numOfAnswerOptions - unlearnedWords.size)).shuffled()
            } else {
                unlearnedWords.shuffled().take(numOfAnswerOptions)
            }
        }

    private fun loadDictionary(): List<Word> {
        val wordsFile = File(fileName)
        if (!wordsFile.exists()) {
            File(DEFAULT_FILE_NAME).copyTo(wordsFile)
        }

        val fileLines = wordsFile.readLines()
        val dictionary: List<Word> = List(fileLines.size) { indexOfFileLine ->
            val stringElem = fileLines[indexOfFileLine].split("|")
            Word(
                original = stringElem[0],
                translate =  stringElem[1],
                correctAnswersCount = stringElem.getOrNull(2)?.toIntOrNull() ?: 0
            )
        }
        return dictionary
    }

    private fun saveDictionary() {
        val file = File(fileName)
        val newFileContent = dictionary.map { "${it.original}|${it.translate}|${it.correctAnswersCount}" }
        file.writeText(newFileContent.joinToString(separator = "\n"))
    }
}