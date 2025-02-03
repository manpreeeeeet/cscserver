import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.LocalDateTime

@Serializable
data class PostsResponse(val posts: List<PostDto>)

@Serializable
data class PostRequest(val text: String)

fun Application.routePosts() {
    routing {

        route("/posts/latest/") {
            get {
                val latestPost = transaction {
                    PostEntity.all()
                        .orderBy(PostsTable.createdAt to SortOrder.DESC)
                        .limit(1)
                        .first()
                        .toPostDto()
                }
                call.respond(latestPost)
            }
        }

        route("/posts/{room}/") {
            get {
                val room = call.parameters["room"]!!
                val posts: List<PostDto> = transaction {
                    val posts =
                        RoomEntity.find { RoomTable.name eq room }.first().posts.sortedByDescending { it.createdAt }
                            .map {
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
                rateLimit(RateLimitName("post_limit")) {
                    post {
                        val id = call.parameters["id"]!!.toInt()
                        val postRequest = call.receive<PostRequest>()

                        if (postRequest.text.length == 0) {
                            return@post
                        }

                        val authSession = call.sessions.get<AuthSession>()
                        if (authSession == null) {
                            return@post
                        }

                        val reply = transaction {
                            val authorEntity = AuthorEntity.find { AuthorsTable.id eq authSession.userId }.firstOrNull()
                                ?: return@transaction null
                            val postEntity =
                                PostEntity.find { PostsTable.id eq id }.firstOrNull() ?: return@transaction null

                            val latestReply = ReplyEntity.find {
                                (ReplyTable.author eq authSession.userId) and (ReplyTable.post eq postEntity.id) and (ReplyTable.text eq postRequest.text)
                            }.orderBy(ReplyTable.createdAt to SortOrder.DESC)
                                .limit(1)
                                .firstOrNull()

                            if (latestReply != null && Duration.between(latestReply.createdAt, LocalDateTime.now())
                                    .toSeconds() <= 30
                            ) {
                                return@transaction null
                            }

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
            }

            rateLimit(RateLimitName("post_limit")) {
                post {
                    val roomName = call.parameters["room"]!!
                    val post = call.receive<PostRequest>()

                    if (post.text.length == 0) {
                        return@post
                    }

                    val authSession = call.sessions.get<AuthSession>()
                    if (authSession == null || authSession.expires < System.currentTimeMillis()) {
                        return@post
                    }

                    val postEntity = transaction {
                        val authorEntity = AuthorEntity.find { AuthorsTable.id eq authSession.userId }.first()
                        val roomEntity = RoomEntity.find { RoomTable.name eq roomName }.first()

                        val latestPost =
                            PostEntity.find { (PostsTable.author eq authorEntity.id) and (PostsTable.text eq post.text) }
                                .orderBy(PostsTable.createdAt to SortOrder.DESC)
                                .limit(1)
                                .firstOrNull()
                        if (latestPost != null && Duration.between(latestPost.createdAt, LocalDateTime.now())
                                .toSeconds() <= 30
                        ) {
                            return@transaction null
                        }

                        PostEntity.new {
                            text = post.text
                            author = authorEntity
                            room = roomEntity
                            createdAt = LocalDateTime.now()
                        }
                    }
                    if (postEntity != null) {
                        call.respond(Response("ok"))
                    }
                }


            }
        }
    }
}