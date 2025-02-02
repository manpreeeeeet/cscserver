
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

@Serializable
data class PostsResponse(val posts: List<PostDto>)

@Serializable
data class PostRequest(val text: String)

fun Application.routePosts() {
    routing {
        route("/posts/{room}/") {
            get {
                val room = call.parameters["room"]!!
                val posts: List<PostDto> = transaction {
                    val posts = RoomEntity.find { RoomTable.name eq room }.first().posts.sortedByDescending { it.createdAt }.map {
                        it.toPostDto()
                    }
                    posts
                }
                call.respond(posts)
            }

            route("{id}/") {

                get {
                    val id = call.parameters["id"]!!.toInt()
                    val post: PostDto = transaction {
                        PostEntity.find { PostsTable.id eq id }.first().toPostDto()
                    }
                    call.respond(post)
                }

                post {
                    val id = call.parameters["id"]!!.toInt()
                    val postRequest = call.receive<PostRequest>()

                    val authSession = call.sessions.get<AuthSession>()
                    if (authSession == null) {
                        return@post
                    }

                    val reply = transaction {
                        val authorEntity = AuthorEntity.find { AuthorsTable.id eq authSession.userId }.firstOrNull()
                            ?: return@transaction null
                        val postEntity =
                            PostEntity.find { PostsTable.id eq id }.firstOrNull() ?: return@transaction null

                        ReplyEntity.new {
                            text = postRequest.text
                            author = authorEntity
                            post = postEntity
                            createdAt = LocalDateTime.now()
                        }

                    }
                    if (reply == null) {
                        return@post
                    }
                    call.respond(Response("ok"))
                }
            }


            post {
                val roomName = call.parameters["room"]!!
                val post = call.receive<PostRequest>()

                val authSession = call.sessions.get<AuthSession>()
                if (authSession == null) {
                    return@post
                }

                transaction {
                    val authorEntity = AuthorEntity.find { AuthorsTable.id eq authSession.userId }.first()
                    val roomEntity = RoomEntity.find { RoomTable.name eq roomName }.first()
                    PostEntity.new {
                        text = post.text
                        author = authorEntity
                        room = roomEntity
                        createdAt = LocalDateTime.now()
                    }
                }
                call.respond(Response("ok"))
            }


        }
    }
}