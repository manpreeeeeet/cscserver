import kotlinx.serialization.Serializable

@Serializable
data class AuthSession(val userId: Int, val expires: Long)