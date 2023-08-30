import java.io.File

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

class LearnWordsTrainer {
    private val dictionary = try {
        loadDictionary()
    } catch (e: Exception) {
        throw IllegalArgumentException("Некорректный файл")
    }

    fun getStatistics(): Statistics {
        val numOfAllWords = dictionary.size
        val numOfLearnedWords = dictionary.filter { it.correctAnswersCount >= LEARNING_THRESHOLD }.size
        val learnedPercent = 100 * numOfLearnedWords / numOfAllWords

        return Statistics(numOfAllWords, numOfLearnedWords, learnedPercent)
    }

    fun getQuestion(): Question? {
        val unlearnedWords = getUnlearnedWords()

        if (unlearnedWords.isEmpty()) return null

        val questionWords = getRandomQuestionWords(unlearnedWords)
        val rightWord =
            if (unlearnedWords.size < NUM_OF_ANSWER_OPTIONS) unlearnedWords.random()
            else questionWords.random()

        return Question(questionWords, rightWord)
    }

    fun checkAnswer(question: Question, userAnswer: String): Boolean {
        with(question) {
            if (userAnswer.toIntOrNull() == questionWords.indexOf(rightAnswer) + 1) {
                rightAnswer.correctAnswersCount++
                saveDictionary()
                return true
            }
        }
        return false
    }

    private fun getUnlearnedWords() = dictionary.filter { it.correctAnswersCount < LEARNING_THRESHOLD }

    private fun getRandomQuestionWords(unlearnedWords: List<Word>): List<Word> {
            return if (unlearnedWords.size < NUM_OF_ANSWER_OPTIONS) {
                val learnedWords = dictionary.filter { it.correctAnswersCount >= LEARNING_THRESHOLD }.shuffled()
                (unlearnedWords + learnedWords.take(NUM_OF_ANSWER_OPTIONS - unlearnedWords.size)).shuffled()
            } else {
                unlearnedWords.shuffled().take(NUM_OF_ANSWER_OPTIONS)
            }
        }

    private fun loadDictionary(): List<Word> {
        val wordsFile = File(FILE_NAME)

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
        val file = File(FILE_NAME)
        val newFileContent = dictionary.map { "${it.original}|${it.translate}|${it.correctAnswersCount}" }
        file.writeText(newFileContent.joinToString(separator = "\n"))
    }
}