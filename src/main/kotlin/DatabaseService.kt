import java.io.File
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

const val URL_DATABASE = "jdbc:sqlite:data.db"
const val WORDS_TABLE = "words"
const val USERS_TABLE = "users"
const val USER_ANSWERS_TABLE = "user_answers"
const val MAX = "mx"
const val FIRST_WORDS_INDEX = 1

class DatabaseService {
    private var dictionarySize: Int = 0

    init {
        DriverManager.getConnection(URL_DATABASE)
            .use { connection ->
                dictionarySize = connection.createStatement().executeQuery("select count(id) from $WORDS_TABLE")
                    .getInt("count(id)")
            }
    }

    fun getDictionarySize() = dictionarySize

    fun deleteAllUserAnswers(chatId: Long) {
        DriverManager.getConnection(URL_DATABASE)
            .use { connection ->
                connection.createStatement().executeUpdate(
                    "delete from $USER_ANSWERS_TABLE where user_id in " +
                            "(select id from $USERS_TABLE where chat_id = $chatId)"
                )
            }
    }

    fun addNewUser(chatId: Long, date: String, username: String? = null) {
        DriverManager.getConnection(URL_DATABASE)
            .use { connection ->
                val statement = connection.createStatement()
                if (isNewUser(statement, chatId)) {
                    val lastId = getLastPrimaryKey(statement, USERS_TABLE).getInt(MAX)
                    if (username != null) {
                        statement.executeUpdate(
                            "insert into $USERS_TABLE values(${lastId + 1}, '$username', '$date', $chatId)"
                        )
                    } else {
                        statement.executeUpdate(
                            "insert into $USERS_TABLE(id, created_at, chat_id) values(${lastId + 1}, '$date', $chatId)"
                        )
                    }
                }
            }
    }

    fun addRightAnswerAndCheckIsLearnedWord(
        chatId: Long, wordOriginal: String, date: String, learningThreshold: Int
    ): Boolean {
        var isLearned = false
        DriverManager.getConnection(URL_DATABASE)
            .use { connection ->
                val statement = connection.createStatement()
                val wordId = statement.executeQuery("select id from $WORDS_TABLE where text = '$wordOriginal'")
                    .getInt("id")
                val userId = statement.executeQuery("select id from $USERS_TABLE where chat_id = $chatId")
                    .getInt("id")
                val rs = statement.executeQuery(
                    "select * from $USER_ANSWERS_TABLE WHERE word_id = $wordId and user_id = $userId"
                )
                val sqlRequestString =
                    if (rs.getInt("user_id") == userId && rs.getInt("word_id") == wordId) {
                        val correctAnswerCount = rs.getInt("correct_answer_count") + 1
                        if (correctAnswerCount >= learningThreshold) isLearned = true

                        "update $USER_ANSWERS_TABLE set correct_answer_count = $correctAnswerCount, " +
                                "updated_at = '$date' where word_id = $wordId and user_id = $userId"
                    } else "insert into $USER_ANSWERS_TABLE values($userId, $wordId, 1, '$date')"

                statement.executeUpdate(sqlRequestString)
            }

        return isLearned
    }

    fun updateDictionary(wordsFile: File) {
        DriverManager.getConnection(URL_DATABASE)
            .use { connection ->
                val statement = connection.createStatement()
                val fileLines = wordsFile.readLines()
                val lastId = getLastPrimaryKey(statement, WORDS_TABLE).getInt(MAX)
                fileLines.forEachIndexed { indexOfLine, fileLine ->
                    val lineElements = fileLine.split("|")
                    statement.executeUpdate(
                        """
                            insert into $WORDS_TABLE values(
                            ${indexOfLine + FIRST_WORDS_INDEX + lastId},
                            '${lineElements[0]}',
                            '${lineElements[1]}'
                            )
                            on conflict do nothing                            
                        """.trimIndent()
                    )
                }

                dictionarySize = statement.executeQuery("select count(id) from $WORDS_TABLE")
                    .getInt("count(id)")
            }
    }

    private fun getLastPrimaryKey(statement: Statement, tableName: String): ResultSet =
        statement.executeQuery("select max(id) as $MAX from $tableName")

    private fun isNewUser(statement: Statement, chatId: Long): Boolean {
        return statement.executeQuery("select chat_id from $USERS_TABLE WHERE chat_id = $chatId")
            .getLong("chat_id") != chatId
    }

    fun createDatabaseTables() {
        DriverManager.getConnection(URL_DATABASE)
            .use { connection ->
                val statement = connection.createStatement()
                statement.executeUpdate(
                    """
                      create table if not exists "$WORDS_TABLE" (
                          "id" integer primary key,
                          "text" varchar unique,
                          "translate" varchar
                      );
              """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    create table if not exists "$USERS_TABLE" (
                        "id" integer primary key,
                        "username" varchar,
                        "created_at" timestamp,
                        "chat_id" integer
                    );
                """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    create table if not exists "$USER_ANSWERS_TABLE" (
                      "user_id" integer,
                      "word_id" integer,
                      "correct_answer_count" integer,
                      "updated_at" timestamp
                    );
                """.trimIndent()
                )
            }
    }
}