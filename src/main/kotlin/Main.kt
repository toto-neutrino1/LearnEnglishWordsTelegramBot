const val NUM_OF_ANSWER_OPTIONS = 4
const val LEARNING_THRESHOLD = 3
const val FILE_NAME = "words.txt"

fun main() {
    val trainer = LearnWordsTrainer()
    startMenu(trainer)
}

fun startMenu(trainer: LearnWordsTrainer) {
    while (true) {
        println("\nМеню: 1 – Учить слова, 2 – Статистика, 0 – Выход")
        when (readln().toIntOrNull()) {
            1 -> trainer.startLearningWords()
            2 -> trainer.printStatistics()
            0 -> break
            else -> println("Ввод данных некорректный!")
        }
    }
}

data class Word(
    val original: String,
    val translate: String,
    var correctAnswersCount: Int = 0
)