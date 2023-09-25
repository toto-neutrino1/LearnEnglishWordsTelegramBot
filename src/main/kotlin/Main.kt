const val NUM_OF_ANSWER_OPTIONS = 4
const val LEARNING_THRESHOLD = 3
const val DEFAULT_FILE_NAME = "words.txt"

fun main() {
    val trainer = LearnWordsTrainer()
    startMenu(trainer)
}

fun startMenu(trainer: LearnWordsTrainer) {
    while (true) {
        println("\nМеню: 1 – Учить слова, 2 – Статистика, 0 – Выход")
        when (readln().toIntOrNull()) {
            1 -> startLearningWords(trainer)
            2 -> printStatistics(trainer)
            0 -> break
            else -> println("Ввод данных некорректный!")
        }
    }
}

fun printStatistics(trainer: LearnWordsTrainer) = println(trainer.getStatisticsInString())

fun startLearningWords(trainer: LearnWordsTrainer) {
    while (true) {
        val question = trainer.getQuestion()

        if (question == null) {
            println("Вы выучили все слова")
            break
        } else {
            printQuestion(question)

            val checkAnswerResult = getCheckAnswerResult(trainer, question)
            if (checkAnswerResult == null) break else println(checkAnswerResult)
        }
    }
}

fun printQuestion(question: Question) {
    println("\nСлово ${question.rightAnswer.original} переводится как:")
    question.questionWords.forEachIndexed { index, word -> println("${index + 1} - ${word.translate}") }
    println("0 - Меню")
}

fun getCheckAnswerResult(trainer: LearnWordsTrainer, question: Question): String? {
    println("\nВаш вариант ответа:")
    val userAnswer = readln()
    return when {
        userAnswer == "0" -> null
        trainer.checkAnswer(userAnswer) -> "Верно"
        else -> "Ответ неверный. Правильный перевод - \"${question.rightAnswer.translate}\""
    }
}