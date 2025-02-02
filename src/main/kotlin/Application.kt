import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import posts.AuthorsTable
import posts.PostsTable
import javax.sql.DataSource


val serverPort = System.getenv("SERVER_PORT")?.toInt() ?: 8080
var passwordPepper = requireNotNull(System.getenv("PASSWORD_PEPPER"))

object DB {

    var db: DataSource = connect();

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
    transaction(Database.connect(DB.db)) {
        SchemaUtils.create(PostsTable, AuthorsTable)
    }

    embeddedServer(Netty, port = serverPort, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(Sessions) {
    }
    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/") {
            call.respondText("Hello, world!")
        }
    }

}