import java.io.File

fun main() {
    val wordsFile = File("words.txt")
    val dictionary: MutableList<Word> = mutableListOf()

    val fileLines = wordsFile.readLines()
    for (line in fileLines) {
        val stringElem = line.split("|")
        val word = Word(
            original = stringElem[0],
            translate =  stringElem[1],
            correctAnswersCount = stringElem[2].toIntOrNull() ?: 0
        )
        dictionary.add(word)
    }

    for (word in dictionary) {
        println(word)
    }

    startMenu(dictionary = dictionary, learningThreshold = 3)
}

data class Word(
    val original: String,
    val translate: String,
    var correctAnswersCount: Int = 0
)

fun startMenu(dictionary: MutableList<Word>, learningThreshold: Int) {
    while (true) {
        println("\nМеню: 1 – Учить слова, 2 – Статистика, 0 – Выход")
        println(
            when (readln().toIntOrNull()) {
                1 -> "учим слова"
                2 -> getStatistics(dictionary, learningThreshold)
                0 -> break
                else -> "Ввод данных некорректный!"
            }
        )
    }
}

fun getStatistics(dictionary: MutableList<Word>, learningThreshold: Int): String {
    val numOfAllWords = dictionary.size
    val numOfLearnedWords = dictionary.filter { it.correctAnswersCount >= learningThreshold }.size
    val learnedPercent = 100 * numOfLearnedWords / numOfAllWords

    return "Выучено $numOfLearnedWords из $numOfAllWords слов | $learnedPercent%"
}