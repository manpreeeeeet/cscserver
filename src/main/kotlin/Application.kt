import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import javax.sql.DataSource
import kotlin.time.Duration.Companion.seconds


val serverPort = System.getenv("SERVER_PORT")?.toInt() ?: 8080
var passwordPepper = requireNotNull(System.getenv("PASSWORD_PEPPER"))
val s3Url = requireNotNull(System.getenv("S3_ENDPOINT"))
val s3AccessId = requireNotNull(System.getenv("AWS_ACCESS_KEY_ID"))
val s3SecretAccessKey = requireNotNull(System.getenv("AWS_SECRET_ACCESS_KEY"))

object DB {

    val db: DataSource by lazy { connect() }

    fun connect(): DataSource {
        val config = HikariConfig()
        config.jdbcUrl = requireNotNull(System.getenv("DB_URL"))
        config.username = requireNotNull(System.getenv("DB_USER"))
        config.password = requireNotNull(System.getenv("DB_PASSWORD"))
        config.driverClassName = "org.postgresql.Driver"

        return HikariDataSource(config)

    }
}


fun main() {
    Database.connect(DB.db)
    transaction {
        SchemaUtils.create(RoomTable, PostsTable, ReplyTable, AuthorsTable, SessionTable, InviteTable, ImageTable)
        SchemaUtils.createMissingTablesAndColumns(PostsTable, ReplyTable, ImageTable)
    }

    embeddedServer(Netty, port = serverPort, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {

    install(CORS) {
        allowCredentials = true
        allowHost("localhost:5173", listOf("http", "https"))
        allowHost("cscbackalley.club", listOf("http", "https"))
        anyMethod()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.AccessControlAllowCredentials)
        allowHeader(HttpHeaders.Authorization)
        exposeHeader(HttpHeaders.SetCookie)
    }

    install(RateLimit) {
        register(RateLimitName("post_limit")) {
            rateLimiter(limit = 15, refillPeriod = 60.seconds)
            requestKey { call ->
                call.request.origin.remoteAddress
            }
        }
    }

    install(Sessions) {
        cookie<AuthSession>("auth_session", DBSession) {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 3600 * 24
            cookie.sameSite = "none"
            cookie.secure = true
        }
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    routing {
        get("/") {
            call.respondText("Hello, world!")
        }
    }
    routeAuthors()
    routePosts()
    routeImage()
}