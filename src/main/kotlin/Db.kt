
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object RoomTable : IntIdTable("rooms") {
    val name = varchar("name", 100).uniqueIndex()
}

object InviteTable : IntIdTable("invite") {
    val code = varchar("code", 100).uniqueIndex()
}

object PostsTable : IntIdTable("posts") {
    private const val MAX_VARCHAR_LENGTH = 200
    val text = varchar("text", MAX_VARCHAR_LENGTH)
    val author = reference("author_id", AuthorsTable)
    val room = reference("room_id", RoomTable)
    val createdAt = datetime("created_at")
}

object ReplyTable : IntIdTable("replies") {
    private const val MAX_VARCHAR_LENGTH = 200
    val text = varchar("text", MAX_VARCHAR_LENGTH)
    val author = reference("author_id", AuthorsTable)
    val post = reference("post_id", PostsTable)
    val createdAt = datetime("created_at")
}

class InviteEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<InviteEntity>(InviteTable)
    var code by InviteTable.code
}

class RoomEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RoomEntity>(RoomTable)

    var name by RoomTable.name
    val posts by PostEntity referrersOn PostsTable.room
}

class AuthorEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AuthorEntity>(AuthorsTable)

    var name by AuthorsTable.name
    var password by AuthorsTable.password
    val posts by PostEntity referrersOn PostsTable.author
    val replies by ReplyEntity referrersOn ReplyTable.author
}

class PostEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PostEntity>(PostsTable)

    var text by PostsTable.text
    var author by AuthorEntity referencedOn PostsTable.author
    var room: RoomEntity by RoomEntity referencedOn PostsTable.room
    val replies by ReplyEntity referrersOn ReplyTable.post
    var createdAt by PostsTable.createdAt
}

class ReplyEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ReplyEntity>(ReplyTable)

    var text by ReplyTable.text
    var author by AuthorEntity referencedOn ReplyTable.author
    var post by PostEntity referencedOn ReplyTable.post
    var createdAt by ReplyTable.createdAt
}

object AuthorsTable : IntIdTable("authors") {
    private const val MAX_VARCHAR_LENGTH = 200
    val name = varchar("name", MAX_VARCHAR_LENGTH).uniqueIndex()
    var password = text("password")
}


object SessionTable : IntIdTable("session") {
    val sessionId = text("session_id")
    val value = text("session_value")
}

class SessionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SessionEntity>(SessionTable)

    var sessionId by SessionTable.sessionId
    var value by SessionTable.value
}

object DBSession : SessionStorage {
    override suspend fun invalidate(id: String) {
        transaction {
            SessionTable.deleteWhere { sessionId eq id }
        }
    }

    override suspend fun read(id: String): String {
        return transaction {
            val sessionEntity = SessionEntity.find(SessionTable.sessionId eq id).firstOrNull()
                ?: throw NoSuchElementException("Session $id not found")
            sessionEntity.value
        }
    }

    override suspend fun write(id: String, sessionValue: String) {
        transaction {
            val sessionEntity = SessionEntity.find(SessionTable.sessionId eq id).firstOrNull()
            if (sessionEntity == null) {
                SessionEntity.new {
                    sessionId = id
                    value  = sessionValue
                }
                return@transaction
            }

            sessionEntity.value = sessionValue

        }
    }

}

@Serializable
data class AuthorDto(val name: String)

@Serializable
data class PostDto(val id: Int, val author: AuthorDto, val text: String, val createdAt: String, val replies: List<ReplyDto>)

@Serializable
data class ReplyDto(val id: Int, val author: AuthorDto, val createdAt: String, val text: String)

fun AuthorEntity.toAuthorDto() = AuthorDto(name = this.name)

fun PostEntity.toPostDto(): PostDto {
    val replies = this.replies.sortedBy{ it.createdAt }.map { ReplyDto(it.id.value, it.author.toAuthorDto(),it.createdAt.toIsoString(), it.text) }
    val post = PostDto(this.id.value, this.author.toAuthorDto(), this.text, this.createdAt.toIsoString(), replies)
    return post
}

fun LocalDateTime.toIsoString(): String {
    return this.format(DateTimeFormatter.ISO_DATE_TIME) + "Z"
}