package acceptance

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.kos.auth.TokenMode
import com.kos.common.JWTConfig
import java.time.OffsetDateTime
import java.util.*

object JwtHelper {

    fun validJwt(jwtConfig: JWTConfig, username: String, vararg activities: String): String =
        JWT.create()
            .withIssuer(jwtConfig.issuer)
            .withClaim("username", username)
            .withClaim("mode", TokenMode.ACCESS.toString())
            .withClaim("activities", activities.toList())
            .withExpiresAt(Date.from(OffsetDateTime.now().plusHours(1).toInstant()))
            .sign(Algorithm.HMAC256(jwtConfig.secret))

    fun expiredJwt(jwtConfig: JWTConfig, username: String, vararg activities: String): String =
        JWT.create()
            .withIssuer(jwtConfig.issuer)
            .withClaim("username", username)
            .withClaim("mode", TokenMode.ACCESS.toString())
            .withClaim("activities", activities.toList())
            .withExpiresAt(Date.from(OffsetDateTime.now().minusHours(1).toInstant()))
            .sign(Algorithm.HMAC256(jwtConfig.secret))

    fun refreshJwt(jwtConfig: JWTConfig, username: String): String =
        JWT.create()
            .withIssuer(jwtConfig.issuer)
            .withClaim("username", username)
            .withClaim("mode", TokenMode.REFRESH.toString())
            .withClaim("activities", emptyList<String>())
            .withExpiresAt(Date.from(OffsetDateTime.now().plusDays(30).toInstant()))
            .sign(Algorithm.HMAC256(jwtConfig.secret))
}
