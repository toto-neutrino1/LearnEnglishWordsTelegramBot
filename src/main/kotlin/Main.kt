import java.io.File

const val NUM_OF_ANSWER_OPTIONS = 4
const val LEARNING_THRESHOLD = 3

fun main() {
    val wordsFile = File("words.txt")

    val fileLines = wordsFile.readLines()
    val dictionary: List<Word> = List(fileLines.size) { indexOfFileLine ->
        val stringElem = fileLines[indexOfFileLine].split("|")
        Word(
            original = stringElem[0],
            translate =  stringElem[1],
            correctAnswersCount = stringElem.getOrNull(2)?.toIntOrNull() ?: 0
        )
    }

    for (word in dictionary) {
        println(word)
    }

    startMenu(dictionary)
}

data class Word(
    val original: String,
    val translate: String,
    var correctAnswersCount: Int = 0
)

fun startMenu(dictionary: List<Word>) {
    while (true) {
        println("\nМеню: 1 – Учить слова, 2 – Статистика, 0 – Выход")
        when (readln().toIntOrNull()) {
                1 -> startLearningWords(dictionary)
                2 -> println(getStatistics(dictionary))
                0 -> break
                else -> println("Ввод данных некорректный!")
            }
    }
}

fun startLearningWords(dictionary: List<Word>) {
    while (true) {
        val unlearnedWords = dictionary.filter { it.correctAnswersCount < LEARNING_THRESHOLD }
        if (unlearnedWords.isEmpty()) {
            println("Вы выучили все слова")
            break
        } else {
            val shuffledWords = unlearnedWords.shuffled().take(NUM_OF_ANSWER_OPTIONS)
            val rightWord = shuffledWords.random()
            println("\nСлово ${rightWord.original} переводится как:")
            shuffledWords.forEachIndexed { index, word -> println("${index + 1} - ${word.translate}") }
            println("0 - Меню")

            println("\nВаш вариант ответа:")
            when (readln().toIntOrNull()) {
                0 -> break
                shuffledWords.indexOf(rightWord) + 1 -> {
                    println("Верно!")
                    rightWord.correctAnswersCount++
                    saveDictionary(dictionary)
                }
                else -> println("Ответ неверный. Правильный перевод - \"${rightWord.translate}\"")
            }
        }
    }
}

fun saveDictionary(dictionary: List<Word>) {
    val file = File("words.txt")
    val newFileContent = dictionary.map { "${it.original}|${it.translate}|${it.correctAnswersCount}" }
    file.writeText(newFileContent.joinToString(separator = "\n"))
}

fun getStatistics(dictionary: List<Word>): String {
    val numOfAllWords = dictionary.size
    val numOfLearnedWords = dictionary.filter { it.correctAnswersCount >= LEARNING_THRESHOLD }.size
    val learnedPercent = 100 * numOfLearnedWords / numOfAllWords

    return "Выучено $numOfLearnedWords из $numOfAllWords слов | $learnedPercent%"
}