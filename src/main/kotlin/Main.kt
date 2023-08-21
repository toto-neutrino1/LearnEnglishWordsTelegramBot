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
            correctAnswersCount = stringElem[2].toIntOrNull() ?: 0)
        dictionary.add(word)
    }

    for (word in dictionary) {
        println(word)
    }
}

data class Word(
    val original: String,
    val translate: String,
    var correctAnswersCount: Int? = 0
)