import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI
import java.net.URL
import java.time.Duration
import java.time.LocalDateTime
import java.util.*


fun generatePresignedUrl(bucketName: String, objectKey: String, contentType: String, fileSize: Long): URL {
    if (contentType !in listOf("image/jpeg", "image/png", "image/gif")) {
        throw IllegalArgumentException("Invalid content type: $contentType")
    }

    val credentials = AwsBasicCredentials.create(
        s3AccessId,
        s3SecretAccessKey
    )

    S3Presigner.builder()
        .endpointOverride(URI.create(s3Url))
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(credentials))
        .build().use { presigner ->
            val putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(contentType)
                .contentLength(fileSize)
                .build()

            val presignedRequest = presigner.presignPutObject { presignRequest ->
                presignRequest.signatureDuration(Duration.ofMinutes(10))
                presignRequest.putObjectRequest(putObjectRequest)
            }

            return presignedRequest.url()
        }
}

@Serializable
data class ImageUpload(val url: String, val objectUrl: String)

fun Application.routeImage() {
    routing {
        route("/image/upload") {
            get {
                val authSession = call.sessions.get<AuthSession>()
                if (authSession == null || authSession.expires < System.currentTimeMillis()) {
                    return@get
                }

                val contentType = call.request.queryParameters["contentType"] ?: return@get
                val fileSize = call.request.queryParameters["fileSize"]?.toLongOrNull() ?: return@get

                if (fileSize > 5 * 1024 * 1024) {
                    return@get
                }

                val objectKey = UUID.randomUUID().toString()
                val canUpload = transaction {
                    val imageCount = ImageEntity.count(ImageTable.author eq authSession.userId)
                    if (imageCount >= 100) {
                        return@transaction false
                    }
                    return@transaction true
                }

                if (!canUpload) {
                    call.respond(HttpStatusCode.Forbidden, "image upload limit reached")
                    return@get
                }

                val urlCreated = generatePresignedUrl("csc", objectKey, contentType, fileSize).toString()
                val publicUrl = "https://pub-c8f9a2777eff439eb65d361e6c47d26b.r2.dev/${objectKey}"

                transaction {
                    val authorEntity = AuthorEntity.find { AuthorsTable.id eq authSession.userId }.first()
                    ImageEntity.new {
                        url = urlCreated
                        author = authorEntity
                        size = fileSize
                        createdAt = LocalDateTime.now()
                    }
                }

                call.respond(ImageUpload(urlCreated, publicUrl))
            }
        }
    }
}