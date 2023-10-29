import java.io.File
import java.sql.Connection
import java.sql.ResultSet

const val URL_DATABASE = "jdbc:sqlite:data.db"
const val WORDS_TABLE = "words"
const val USERS_TABLE = "users"
const val USER_ANSWERS_TABLE = "user_answers"
const val MAX = "mx"
const val FIRST_WORDS_INDEX = 1

class DatabaseService() {
    private var dictionarySize: Int = 0

    fun getDictionarySize(connection: Connection) =
        connection.prepareStatement(
            """
                select count(id) 
                from $WORDS_TABLE
            """.trimIndent()
        ).executeQuery()
            .getInt("count(id)")

    fun getNumOfLearnedWords(connection: Connection, chatId: Long, learningThreshold: Int) =
        connection.prepareStatement(
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
        ).executeQuery()
            .getInt("count(word_id)")

    fun getLearnedWords(connection: Connection, chatId: Long, learningThreshold: Int): List<Word> {
        val numOfLearnedWords = getNumOfLearnedWords(connection, chatId, learningThreshold)
        if (numOfLearnedWords != 0) {
            val userId = connection.prepareStatement(
                """
                    select id 
                    from $USERS_TABLE 
                    where chat_id = $chatId
                """.trimIndent()
            ).executeQuery()
                .getInt("id")

            val resultSetLearnedWords = connection.prepareStatement(
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
            ).executeQuery()

            return getWordsFromTable(resultSetLearnedWords)
        }

        return listOf()
    }

    fun getUnlearnedWords(connection: Connection, chatId: Long, learningThreshold: Int): List<Word> {
        val numOfUnlearnedWords =
            getDictionarySize(connection) - getNumOfLearnedWords(connection, chatId, learningThreshold)
        if (numOfUnlearnedWords != 0) {
            val userId = connection.prepareStatement(
                """
                    select id 
                    from $USERS_TABLE 
                    where chat_id = $chatId
                """.trimIndent()
            ).executeQuery()
                .getInt("id")

            val resultSetUnlearnedWords = connection.prepareStatement(
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
            ).executeQuery()

            return getWordsFromTable(resultSetUnlearnedWords)
        }

        return listOf()
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
        val userId = connection.prepareStatement(
            """
                select id 
                from $USERS_TABLE 
                where chat_id = $chatId
            """.trimIndent()
        ).executeQuery()
            .getInt("id")

        val wordId = connection.prepareStatement(
            """
                select id 
                from $WORDS_TABLE
                where text = ?
            """.trimIndent()
        ).apply {
            setString(1, word.original)
        }.executeQuery()
            .getInt("id")

        val wordIdFromAnswersResultSet = connection.prepareStatement(
            """
                select word_id 
                from $USER_ANSWERS_TABLE 
                where word_id = $wordId 
                    and user_id = $userId
            """.trimIndent()
        ).executeQuery()

        if (wordIdFromAnswersResultSet.getInt("word_id") == 0) {
            connection.prepareStatement(
                """
                    insert into $USER_ANSWERS_TABLE 
                    values($userId, $wordId, $correctAnswersCount, '$date')
                """.trimIndent()
            ).executeUpdate()
        } else {
            connection.prepareStatement(
                """
                    update $USER_ANSWERS_TABLE
                    set correct_answer_count = $correctAnswersCount, updated_at = '$date' 
                    where user_id = $userId 
                        and word_id = $wordId
                """.trimIndent()
            ).executeUpdate()
        }
    }

    fun resetAllUserAnswers(connection: Connection, chatId: Long, date: String) {
        connection.prepareStatement(
            """
                update $USER_ANSWERS_TABLE 
                set correct_answer_count = 0, updated_at = '$date' 
                where user_id in (
                    select id 
                    from $USERS_TABLE 
                    where chat_id = $chatId
                )
            """.trimIndent()
        ).executeUpdate()
    }

    fun addNewUser(connection: Connection, chatId: Long, date: String, username: String? = null) {
        if (isNewUser(connection, chatId)) {
            val lastId = getLastPrimaryKey(connection, USERS_TABLE).getInt(MAX)
            if (username != null) {
                connection.prepareStatement(
                    """
                        insert into $USERS_TABLE 
                        values(?, ?, ?, ?)
                    """.trimIndent()
                ).apply {
                    setInt(1, lastId + 1)
                    setString(2, username)
                    setString(3, date)
                    setLong(4, chatId)
                    executeUpdate()
                }
            } else {
                connection.prepareStatement(
                    """
                        insert into $USERS_TABLE(id, created_at, chat_id) 
                        values(${lastId + 1}, '$date', $chatId)
                    """.trimIndent()
                ).executeUpdate()
            }
        }
    }

    fun updateDictionary(connection: Connection, wordsFile: File) {
        val fileLines = wordsFile.readLines()
        val lastId = getLastPrimaryKey(connection, WORDS_TABLE).getInt(MAX)
        fileLines.forEachIndexed { indexOfLine, fileLine ->
            val lineElements = fileLine.split("|")
            connection.prepareStatement(
                """
                    insert into $WORDS_TABLE 
                    values(?, ?, ?)
                    on conflict do nothing
                """.trimIndent()
            ).apply {
                setInt(1, indexOfLine + FIRST_WORDS_INDEX + lastId)
                setString(2, lineElements[0])
                setString(3, lineElements[1])
                executeUpdate()
            }
        }

        dictionarySize = connection.prepareStatement(
            """
                select count(id) 
                from $WORDS_TABLE
            """.trimIndent()
        ).executeQuery()
            .getInt("count(id)")
    }

    private fun getLastPrimaryKey(connection: Connection, tableName: String): ResultSet =
        connection.prepareStatement(
            """
                select max(id) as $MAX 
                from $tableName
            """.trimIndent()
        ).executeQuery()

    private fun isNewUser(connection: Connection, chatId: Long): Boolean {
        return connection.prepareStatement(
            """
                select chat_id 
                from $USERS_TABLE 
                where chat_id = $chatId
            """.trimIndent()
        ).executeQuery()
            .getLong("chat_id") != chatId
    }

    fun createDatabaseTables(connection: Connection) {
        connection.prepareStatement(
            """
                create table 
                if not exists "$WORDS_TABLE" (
                    "id" integer primary key,
                    "text" varchar unique,
                    "translate" varchar
                );
            """.trimIndent()
        ).executeUpdate()

        connection.prepareStatement(
            """
                create table 
                if not exists "$USERS_TABLE" (
                    "id" integer primary key,
                    "username" varchar,
                    "created_at" timestamp,
                    "chat_id" integer
                );
            """.trimIndent()
        ).executeUpdate()

        connection.prepareStatement(
            """
                create table 
                if not exists "$USER_ANSWERS_TABLE" (
                    "user_id" integer,
                    "word_id" integer,
                    "correct_answer_count" integer,
                    "updated_at" timestamp
                );
            """.trimIndent()
        ).executeUpdate()
    }
}