import java.io.File

class LearnWordsTrainer {
    private val dictionary = try {
        loadDictionary()
    } catch (e: Exception) {
        throw IllegalArgumentException("Некорректный файл")
    }

    fun printStatistics() {
        val numOfAllWords = dictionary.size
        val numOfLearnedWords = dictionary.filter { it.correctAnswersCount >= LEARNING_THRESHOLD }.size
        val learnedPercent = 100 * numOfLearnedWords / numOfAllWords

        println("Выучено $numOfLearnedWords из $numOfAllWords слов | $learnedPercent%")
    }

    fun startLearningWords() {
        while (true) {
            val unlearnedWords = dictionary.filter { it.correctAnswersCount < LEARNING_THRESHOLD }

            if (unlearnedWords.isEmpty()) {
                println("Вы выучили все слова")
                break
            } else {
                val shuffledWords = getRandomUnlearnedWords(unlearnedWords)

                val rightWord =
                    if (unlearnedWords.size < NUM_OF_ANSWER_OPTIONS) unlearnedWords.random()
                    else shuffledWords.random()

                printQuestion(shuffledWords, rightWord)

                val checkAnswerResult = getCheckAnswerResult(shuffledWords, rightWord)
                if (checkAnswerResult.isEmpty()) break else println(checkAnswerResult)
            }
        }
    }

    private fun getRandomUnlearnedWords(unlearnedWords: List<Word>): List<Word> {
            return if (unlearnedWords.size < NUM_OF_ANSWER_OPTIONS) {
                val learnedWords = dictionary.filter { it.correctAnswersCount >= LEARNING_THRESHOLD }.shuffled()
                (unlearnedWords + learnedWords.take(NUM_OF_ANSWER_OPTIONS - unlearnedWords.size)).shuffled()
            } else {
                unlearnedWords.shuffled().take(NUM_OF_ANSWER_OPTIONS)
            }
        }

    private fun printQuestion(shuffledWords: List<Word>, rightWord: Word) {
        println("\nСлово ${rightWord.original} переводится как:")
        shuffledWords.forEachIndexed { index, word -> println("${index + 1} - ${word.translate}") }
        println("0 - Меню")
    }

    private fun getCheckAnswerResult(shuffledWords: List<Word>, rightWord: Word): String {
        println("\nВаш вариант ответа:")
        return when (readln().toIntOrNull()) {
            0 -> ""
            shuffledWords.indexOf(rightWord) + 1 -> {
                rightWord.correctAnswersCount++
                saveDictionary()
                "Верно!"
            }
            else -> "Ответ неверный. Правильный перевод - \"${rightWord.translate}\""
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