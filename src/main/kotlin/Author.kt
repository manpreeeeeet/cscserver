
import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class RegisterRequest(
    val name: String,
    val password: String,
    val code: String
)

@Serializable
data class LoginRequest(
    val name: String,
    val password: String,
)

@Serializable
data class Response(val msg: String)

fun Application.routeAuthors() {
    routing {
        route("/author") {

            rateLimit(RateLimitName("login_limit")) {
                get("/invite") {
                    val codeProvided = call.request.queryParameters["code"] ?: return@get

                    val existingSession = call.sessions.get<AuthSession>()
                    if (existingSession == null || existingSession.expires < System.currentTimeMillis()) {
                        call.respond(HttpStatusCode.Forbidden, "Session timed out")
                        return@get
                    }

                    var errorMessage = ""
                    val inviteEntity = transaction {
                        val authorEntity = AuthorEntity.find { AuthorsTable.id eq existingSession.userId }.first()
                        val inviteLimit = InviteLimitEntity.find { InviteLimitTable.author eq existingSession.userId }.first()
                        if (inviteLimit.limit <= 0 ) {
                            errorMessage = "out of invites"
                            return@transaction null
                        }
                        inviteLimit.limit -= 1
                        InviteEntity.new {
                            code = codeProvided
                            author = authorEntity
                        }
                    }
                    if (inviteEntity == null) {
                        call.respond(mapOf("message" to errorMessage))
                        return@get
                    }
                    call.respond(mapOf("message" to "success"))

                }
            }

            get("/status") {
                val existingSession = call.sessions.get<AuthSession>()
                if (existingSession == null || existingSession.expires < System.currentTimeMillis()) {
                    call.respond(HttpStatusCode.Forbidden, "Session timed out")
                    return@get
                }
                val author = transaction {
                    AuthorEntity.find(AuthorsTable.id eq existingSession.userId).firstOrNull()
                }
                call.respond(author!!.toAuthorDto())
            }

            post("/register") {
                val request = call.receive<RegisterRequest>()

                val author = transaction {
                    val invite =
                        InviteEntity.find { (InviteTable.code eq request.code) and (InviteTable.usedBy eq null) }.firstOrNull() ?: return@transaction null

                    val authorEntity = AuthorEntity.new {
                        name = request.name
                        password = hashPassword(request.password)
                    }

                    invite.usedBy = authorEntity
                    authorEntity
                }
                if (author == null) {
                    call.respond(HttpStatusCode.Forbidden, "failed")
                    return@post
                }

                call.sessions.set(
                    AuthSession(
                        userId = author.id.value,
                        expires = System.currentTimeMillis() + 3600 * 1000 * 24
                    )
                )

                call.respond(Response(msg = "ok"))
            }

            rateLimit(RateLimitName("login_limit")) {
                post("/login") {
                    val request = call.receive<LoginRequest>()

                    val author = transaction {
                        AuthorEntity.find(AuthorsTable.name eq request.name).firstOrNull()
                    }
                    if (author == null) {
                        call.respond("author not found")
                        return@post
                    }

                    if (!verifyPassword(request.password, author.password)) {
                        call.respond("wrong password")
                        return@post
                    }
                    val existingSession = call.sessions.get<AuthSession>()
                    if (existingSession != null) {
                        call.respond(Response(msg = "ok"))
                        return@post
                    }

                    call.sessions.set(
                        AuthSession(
                            userId = author.id.value,
                            expires = System.currentTimeMillis() + 3600 * 1000 * 24
                        )
                    )
                    call.respond(Response(msg = "ok"))
                }
            }
        }
    }
}

fun hashPassword(password: String): String {
    val pepperedPassword = password + passwordPepper
    return BCrypt.withDefaults().hashToString(12, pepperedPassword.toCharArray())
}

fun verifyPassword(password: String, passwordHash: String): Boolean {
    val pepperedPassword = password + passwordPepper
    return BCrypt.verifyer().verify(pepperedPassword.toCharArray(), passwordHash).verified
}
