import at.favre.lib.crypto.bcrypt.BCrypt


fun setPassword(password: String) {
    val pepperedPassword = password + passwordPepper
    var passwordHash = BCrypt.withDefaults().hashToString(12, pepperedPassword.toCharArray())
}

fun verifyPassword(password: String, passwordHash: String): Boolean {
    val pepperedPassword = password + passwordPepper
    return BCrypt.verifyer().verify(pepperedPassword.toCharArray(), passwordHash).verified
}