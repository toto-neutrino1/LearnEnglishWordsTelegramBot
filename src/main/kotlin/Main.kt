import java.io.File

fun main() {
    val wordsFile = File("words.txt")

    val fileLines = wordsFile.readLines()
    for (line in fileLines) {
        println(line)
    }
}