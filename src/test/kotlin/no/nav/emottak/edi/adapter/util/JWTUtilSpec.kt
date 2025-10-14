package no.nav.emottak.edi.adapter.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm.RSA256
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.token.DPoPAccessToken
import com.nimbusds.openid.connect.sdk.Nonce
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import no.nav.emottak.base64Encoded
import no.nav.emottak.config
import no.nav.emottak.generateRsaJwk
import java.net.URI
import java.util.Base64.getUrlDecoder
import kotlin.uuid.Uuid

class JWTUtilSpec : StringSpec(
    {
        val rsaJwk = generateRsaJwk()
        val rsaJwkBase64 = rsaJwk.base64Encoded()

        "should generate valid DPoP proof and required claims without nonce" {
            withEnvironment("emottak-nhn-edi", rsaJwkBase64) {
                val config = config()

                println("KEY from env: ${config.nhn.keyPair.value}")

                val signedJwtJson = dpopProofWithoutNonce()

                val signedJwt = SignedJWT.parse(signedJwtJson)
                signedJwt.verify(RSASSAVerifier(rsaJwk.toRSAPublicKey())) shouldBe true

                val claims = signedJwt.jwtClaimsSet
                claims.getStringClaim("htm") shouldBe "POST"
                claims.getStringClaim("htu") shouldBe config.azureAuth.tokenEndpoint.toString()
                claims.getStringClaim("jti") shouldNotBe null
                claims.getDateClaim("iat") shouldNotBe null
            }
        }

        "should generate valid DPoP proof and required claims with nonce" {
            withEnvironment("emottak-nhn-edi", rsaJwkBase64) {
                val config = config().azureAuth

                val random = Uuid.random()
                val signedJwtJson = dpopProofWithNonce(Nonce(random.toString()))

                val signedJwt = SignedJWT.parse(signedJwtJson)
                signedJwt.verify(RSASSAVerifier(rsaJwk.toRSAPublicKey())) shouldBe true

                val claims = signedJwt.jwtClaimsSet
                claims.getStringClaim("htm") shouldBe Post.value
                claims.getStringClaim("htu") shouldBe config.tokenEndpoint.toString()
                claims.getStringClaim("nonce") shouldBe random.toString()
                claims.getStringClaim("jti") shouldNotBe null
                claims.getDateClaim("iat") shouldNotBe null
            }
        }

        "should generate valid DPoP proof and required claims with access token" {
            withEnvironment("emottak-nhn-edi", rsaJwkBase64) {
                val uri = URI("https://my.uri.com")
                val accessToken = DPoPAccessToken("my access token")

                val signedJwtJson = dpopProofWithTokenInfo(uri, Get, accessToken)

                val signedJwt = SignedJWT.parse(signedJwtJson)
                signedJwt.verify(RSASSAVerifier(rsaJwk.toRSAPublicKey())) shouldBe true

                val claims = signedJwt.jwtClaimsSet
                claims.getStringClaim("htm") shouldBe Get.value
                claims.getStringClaim("htu") shouldBe uri.toString()
                claims.getStringClaim("ath") shouldBe accessTokenHash(accessToken.value)
                claims.getStringClaim("jti") shouldNotBe null
                claims.getDateClaim("iat") shouldNotBe null
            }
        }

        "should generate a valid signed JWT client assertion" {
            val config = config().azureAuth
            val assertion = clientAssertion(config)

            val decoded = JWT.decode(assertion)

            val verification = JWT.require(RSA256(rsaJwk.toRSAPublicKey(), null))
                .withIssuer(config.clientId.value)
                .withSubject(config.clientId.value)
                .withAudience(config.audience.value)
                .build()

            verification.verify(assertion)

            decoded.issuer shouldBe config.clientId.value
            decoded.subject shouldBe config.clientId.value
            decoded.audience shouldContain config.audience.value
            decoded.getClaim("jti").asString() shouldNotBe null

            val headerJson = String(getUrlDecoder().decode(decoded.header))
            headerJson shouldContain "\"kid\":\"${config.keyId.value}\""
            headerJson shouldContain "\"alg\":\"RS256\""
            headerJson shouldContain "\"typ\":\"client-authentication+jwt\""
        }
    }
)
