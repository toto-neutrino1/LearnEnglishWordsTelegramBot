import java.io.File
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

const val URL_DATABASE = "jdbc:sqlite:data.db"
const val WORDS_TABLE = "words"
const val USERS_TABLE = "users"
const val USER_ANSWERS_TABLE = "user_answers"
const val MAX = "mx"
const val FIRST_WORDS_INDEX = 1

class DatabaseService() {
    private var dictionarySize: Int = 0
    fun getDictionarySize(connection: Connection) =
        connection.createStatement()
            .executeQuery(
                """
                    select count(id) 
                    from $WORDS_TABLE
                """.trimIndent()
        ).getInt("count(id)")

    fun getNumOfLearnedWords(connection: Connection, chatId: Long, learningThreshold: Int): Int {
        val statement = connection.createStatement()
        val rs = statement.executeQuery(
            """
                select count(word_id) 
                from $USER_ANSWERS_TABLE 
                where user_id in (
                    select id 
                    from $USERS_TABLE 
                    where chat_id = $chatId
                )
                and correct_answer_count >= $learningThreshold
            """.trimIndent()
        )

        return rs.getInt("count(word_id)")
    }

    fun getLearnedWords(connection: Connection, chatId: Long, learningThreshold: Int): List<Word> {
        var learnedWords = listOf<Word>()
        val numOfLearnedWords = getNumOfLearnedWords(connection, chatId, learningThreshold)
        if (numOfLearnedWords != 0) {
            val statement = connection.createStatement()
            val userId = statement.executeQuery(
                """
                    select id 
                    from $USERS_TABLE 
                    where chat_id = $chatId
                """.trimIndent()
            ).getInt("id")

            val resultSetLearnedWords = statement.executeQuery(
                """
                    select text, translate, $USER_ANSWERS_TABLE.correct_answer_count 
                    from (
                        select * 
                        from $WORDS_TABLE 
                        where id in (
                            select word_id 
                            from $USER_ANSWERS_TABLE 
                            where user_id = $userId 
                            and correct_answer_count >= $learningThreshold
                        )
                    ) 
                    left join $USER_ANSWERS_TABLE on (
                        $USER_ANSWERS_TABLE.user_id = $userId and id = $USER_ANSWERS_TABLE.word_id
                    )
                """.trimIndent()
            )

            learnedWords = getWordsFromTable(resultSetLearnedWords)

        }
        return learnedWords
    }

    fun getUnlearnedWords(connection: Connection, chatId: Long, learningThreshold: Int): List<Word> {
        var unLearnedWords = listOf<Word>()
        val numOfUnlearnedWords =
            getDictionarySize(connection) - getNumOfLearnedWords(connection, chatId, learningThreshold)
        if (numOfUnlearnedWords != 0) {
            val statement = connection.createStatement()
            val userId = statement.executeQuery(
                """
                    select id 
                    from $USERS_TABLE 
                    where chat_id = $chatId
                """.trimIndent()
            ).getInt("id")

            val resultSetUnlearnedWords = statement.executeQuery(
                """
                    select text, translate, $USER_ANSWERS_TABLE.correct_answer_count
                    from (
                        select * 
                        from $WORDS_TABLE 
                        where id not in (
                            select word_id 
                            from $USER_ANSWERS_TABLE 
                            where user_id = $userId 
                                and correct_answer_count >= $learningThreshold
                        )
                    ) 
                    left join $USER_ANSWERS_TABLE on (
                        $USER_ANSWERS_TABLE.user_id = $userId and id = $USER_ANSWERS_TABLE.word_id
                    )
                """.trimIndent()
            )

            unLearnedWords = getWordsFromTable(resultSetUnlearnedWords)
        }

        return unLearnedWords
    }

    private fun getWordsFromTable(rs: ResultSet): List<Word> {
        val wordsList = mutableListOf<Word>()
        while (rs.next()) {
            val original = rs.getString("text")
            val translate = rs.getString("translate")
            val correctAnswersCount = rs.getInt("correct_answer_count")
            wordsList.add(
                Word(
                    original = original,
                    translate = translate,
                    correctAnswersCount = correctAnswersCount
                )
            )
        }

        return wordsList.toList()
    }

    fun setCorrectAnswersCount(
        connection: Connection, chatId: Long, word: Word, correctAnswersCount: Int, date: String
    ) {
        val statement = connection.createStatement()
        val userId = statement.executeQuery(
            """
                select id 
                from $USERS_TABLE 
                where chat_id = $chatId
            """.trimIndent()
        ).getInt("id")

        val wordIdStatement = connection.prepareStatement(
            """
                select id 
                from $WORDS_TABLE
                where text = ?
            """.trimIndent())
        wordIdStatement.setString(1, word.original)
        val wordId = wordIdStatement.executeQuery().getInt("id")

        val wordIdFromAnswersResultSet = statement.executeQuery(
            """
                select word_id 
                from $USER_ANSWERS_TABLE 
                where word_id = $wordId 
                    and user_id = $userId
            """.trimIndent()
        )

        if (wordIdFromAnswersResultSet.getInt("word_id") == 0) {
            statement.executeUpdate(
                """
                    insert into $USER_ANSWERS_TABLE 
                    values($userId, $wordId, $correctAnswersCount, '$date')
                """.trimIndent()
            )
        } else {
            statement.executeUpdate(
                """
                    update $USER_ANSWERS_TABLE
                    set correct_answer_count = $correctAnswersCount, updated_at = '$date' 
                    where user_id = $userId 
                        and word_id = $wordId
                """.trimIndent()
            )
        }
    }

    fun resetAllUserAnswers(connection: Connection, chatId: Long, date: String) {
        connection.createStatement().executeUpdate(
            """
                update $USER_ANSWERS_TABLE 
                set correct_answer_count = 0, updated_at = '$date' 
                where user_id in (
                    select id 
                    from $USERS_TABLE 
                    where chat_id = $chatId
                )
            """.trimIndent()
        )
    }


    fun addNewUser(connection: Connection, chatId: Long, date: String, username: String? = null) {
        val statement = connection.createStatement()
        if (isNewUser(statement, chatId)) {
            val lastId = getLastPrimaryKey(statement, USERS_TABLE).getInt(MAX)
            if (username != null) {
                val injectionStatement = connection.prepareStatement(
                    """
                        insert into $USERS_TABLE 
                        values(?, ?, ?, ?)
                    """.trimIndent())
                injectionStatement.setInt(1, lastId + 1)
                injectionStatement.setString(2, username)
                injectionStatement.setString(3, date)
                injectionStatement.setLong(4, chatId)
                injectionStatement.executeUpdate()
            } else {
                statement.executeUpdate(
                    """
                        insert into $USERS_TABLE(id, created_at, chat_id) 
                        values(${lastId + 1}, '$date', $chatId)
                    """.trimIndent()
                )
            }
        }
    }

    fun updateDictionary(connection: Connection, wordsFile: File) {
        val statement = connection.createStatement()
        val fileLines = wordsFile.readLines()
        val lastId = getLastPrimaryKey(statement, WORDS_TABLE).getInt(MAX)
        fileLines.forEachIndexed { indexOfLine, fileLine ->
            val lineElements = fileLine.split("|")
            val injectionStatement = connection.prepareStatement(
                """
                    insert into $WORDS_TABLE 
                    values(?, ?, ?)
                    on conflict do nothing
                """.trimIndent())
            injectionStatement.setInt(1, indexOfLine + FIRST_WORDS_INDEX + lastId)
            injectionStatement.setString(2, lineElements[0])
            injectionStatement.setString(3, lineElements[1])
            injectionStatement.executeUpdate()
        }

        dictionarySize = statement.executeQuery(
            """
                select count(id) 
                from $WORDS_TABLE
            """.trimIndent()
        ).getInt("count(id)")
    }

    private fun getLastPrimaryKey(statement: Statement, tableName: String): ResultSet =
        statement.executeQuery(
            """
                select max(id) as $MAX 
                from $tableName
            """.trimIndent()
        )

    private fun isNewUser(statement: Statement, chatId: Long): Boolean {
        return statement.executeQuery(
            """
                select chat_id 
                from $USERS_TABLE 
                where chat_id = $chatId
            """.trimIndent()
        ).getLong("chat_id") != chatId
    }

    fun createDatabaseTables(connection: Connection) {
        val statement = connection.createStatement()
        statement.executeUpdate(
            """
                create table 
                if not exists "$WORDS_TABLE" (
                    "id" integer primary key,
                    "text" varchar unique,
                    "translate" varchar
                );
            """.trimIndent()
        )

        statement.executeUpdate(
            """
                create table 
                if not exists "$USERS_TABLE" (
                    "id" integer primary key,
                    "username" varchar,
                    "created_at" timestamp,
                    "chat_id" integer
                );
            """.trimIndent()
        )

        statement.executeUpdate(
            """
                create table 
                if not exists "$USER_ANSWERS_TABLE" (
                    "user_id" integer,
                    "word_id" integer,
                    "correct_answer_count" integer,
                    "updated_at" timestamp
                );
            """.trimIndent()
        )
    }
}