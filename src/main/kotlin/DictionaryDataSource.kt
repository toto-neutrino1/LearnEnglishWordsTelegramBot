import java.io.File
import java.sql.*

const val DEFAULT_FILE_NAME = "words.txt"
const val DEFAULT_LEARNING_THRESHOLD = 3

interface IUserDictionary {
    fun getNumOfLearnedWords(): Int
    fun getDictionarySize(): Int
    fun getLearnedWords(): List<Word>
    fun getUnlearnedWords(): List<Word>
    fun setCorrectAnswersCount(word: Word, correctAnswersCount: Int, date: String = "")
    fun resetProgress(date: String = "")
}

class FileUserDictionary(
    private val fileName: String = DEFAULT_FILE_NAME,
    private val learningThreshold: Int = DEFAULT_LEARNING_THRESHOLD
) : IUserDictionary {
    private val dictionary = try {
        loadDictionary()
    } catch (e: Exception) {
        throw IllegalArgumentException("Некорректный файл")
    }

    override fun getNumOfLearnedWords() = getLearnedWords().size
    override fun getDictionarySize() = dictionary.size
    override fun getLearnedWords() = dictionary.filter { it.correctAnswersCount >= learningThreshold }
    override fun getUnlearnedWords() = dictionary.filter { it.correctAnswersCount < learningThreshold }

    override fun setCorrectAnswersCount(word: Word, correctAnswersCount: Int, date: String) {
        word.correctAnswersCount = correctAnswersCount
        saveDictionary()
    }

    override fun resetProgress(date: String) {
        dictionary.forEach { it.correctAnswersCount = 0 }
        saveDictionary()
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

class DatabaseUserDictionary(
    private val chatId: Long = 0,
    private val date: String = "",
    private val username: String? = "",
    private val connection: Connection,
    private val learningThreshold: Int = DEFAULT_LEARNING_THRESHOLD
) : IUserDictionary {
    companion object {
        private val databaseService = DatabaseService()
    }

    init {
        databaseService.addNewUser(connection, chatId, date, username)
    }

    override fun getNumOfLearnedWords() = databaseService.getNumOfLearnedWords(connection, chatId, learningThreshold)
    override fun getDictionarySize() = databaseService.getDictionarySize(connection)
    override fun getLearnedWords() = databaseService.getLearnedWords(connection, chatId, learningThreshold)
    override fun getUnlearnedWords() = databaseService.getUnlearnedWords(connection, chatId, learningThreshold)

    override fun setCorrectAnswersCount(word: Word, correctAnswersCount: Int, date: String) =
        databaseService.setCorrectAnswersCount(connection, chatId, word, correctAnswersCount, date)

    override fun resetProgress(date: String) = databaseService.resetAllUserAnswers(connection, chatId, date)
}