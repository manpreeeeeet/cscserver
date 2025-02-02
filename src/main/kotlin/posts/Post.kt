package posts

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object PostsTable : IntIdTable("posts") {
    private const val MAX_VARCHAR_LENGTH = 200
    val text = varchar("text", MAX_VARCHAR_LENGTH)
    val author = reference("author_id", AuthorsTable)
}

class PostEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PostEntity>(PostsTable)

    var text by PostsTable.text
    var author by AuthorEntity referencedOn PostsTable.author
}

object AuthorsTable : IntIdTable("authors") {
    private const val MAX_VARCHAR_LENGTH = 200
    val name = varchar("name", MAX_VARCHAR_LENGTH).uniqueIndex()
    val password = text("password")
}

class AuthorEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AuthorEntity>(AuthorsTable)

    var name by AuthorsTable.name
    var password by AuthorsTable.password
    val posts by PostEntity referrersOn PostsTable.author
}