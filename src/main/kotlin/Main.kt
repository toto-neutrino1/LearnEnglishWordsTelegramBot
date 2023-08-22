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
            correctAnswersCount = stringElem.getOrNull(2)?.toIntOrNull() ?: 0
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
        when (readln().toIntOrNull()) {
                1 -> startLearningWords(dictionary, learningThreshold)
                2 -> println(getStatistics(dictionary, learningThreshold))
                0 -> break
                else -> println("Ввод данных некорректный!")
            }
    }
}

fun startLearningWords(dictionary: MutableList<Word>, learningThreshold: Int) {
    while (true) {
        val unlearnedWords = dictionary.filter { it.correctAnswersCount < learningThreshold }.toMutableList()
        if (unlearnedWords.isEmpty()) {
            println("Вы выучили все слова")
            break
        } else {
            val shuffledWords = unlearnedWords.shuffled().take(4)
            val rightWord = shuffledWords.random()
            println("\nСлово ${rightWord.original} переводится как:")
            shuffledWords.forEachIndexed { index, word -> println("${index + 1} - ${word.translate}") }

            println("\nВаш вариант ответа:")
            var answer = readln()
            while (answer !in listOf("1", "2", "3", "4")) {
                println("Введён некорректный ответ! \nВведите цифру от 1 до 4:")
                answer = readln()
            }
            if (answer.toInt() == shuffledWords.indexOf(rightWord) + 1) {
                println("Верно!")
                dictionary.find { it == rightWord }?.correctAnswersCount?.inc()
            } else println("Ответ неверный. Правильный перевод - \"${rightWord.translate}\"")

            println("\nВаши дальнейшие действия: \n1 - продолжить изучение слов, 2 - вернуться в главное меню")
            if (readln() == "2") break
        }
    }
}

fun getStatistics(dictionary: MutableList<Word>, learningThreshold: Int): String {
    val numOfAllWords = dictionary.size
    val numOfLearnedWords = dictionary.filter { it.correctAnswersCount >= learningThreshold }.size
    val learnedPercent = 100 * numOfLearnedWords / numOfAllWords

    return "Выучено $numOfLearnedWords из $numOfAllWords слов | $learnedPercent%"
}