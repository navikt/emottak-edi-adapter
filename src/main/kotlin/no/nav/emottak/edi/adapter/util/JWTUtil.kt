package no.nav.emottak.edi.adapter.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.nimbusds.jose.JWSAlgorithm.RS256
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.JWTClaimsSet.Builder
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.dpop.DPoPProofFactory.TYPE
import com.nimbusds.oauth2.sdk.token.DPoPAccessToken
import com.nimbusds.openid.connect.sdk.Nonce
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet.NONCE_CLAIM_NAME
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Post
import no.nav.emottak.config
import no.nav.emottak.edi.adapter.config.AzureAuth
import java.net.URI
import java.security.MessageDigest.getInstance
import java.time.Instant.now
import java.util.Base64.getUrlEncoder
import java.util.Date.from
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

private val keyPair = RSAKey.parse(decodeKeypair(config().nhn.keyPair.value))

fun dpopProofWithoutNonce(): String =
    dpopProof(
        config().azureAuth.tokenEndpoint,
        Post,
        null,
        null
    )

fun dpopProofWithNonce(nonce: Nonce): String =
    dpopProof(
        config().azureAuth.tokenEndpoint,
        Post,
        nonce,
        null
    )

fun dpopProofWithTokenInfo(
    uri: URI,
    method: HttpMethod,
    accessToken: DPoPAccessToken
): String =
    dpopProof(
        uri,
        method,
        null,
        accessToken
    )

fun clientAssertion(
    config: AzureAuth
): String {
    val now = now()
    return JWT.create()
        .withHeader(
            mapOf(
                "typ" to "client-authentication+jwt",
                "alg" to "RS256",
                "kid" to config.keyId.value
            )
        )
        .withIssuer(config.clientId.value)
        .withSubject(config.clientId.value)
        .withAudience(config.audience.value)
        .withJWTId(Uuid.random().toString())
        .withIssuedAt(from(now))
        .withExpiresAt(from(now.plusSeconds(60)))
        .sign(Algorithm.RSA256(keyPair.toRSAPrivateKey()))
}

private fun dpopProof(
    uri: URI,
    method: HttpMethod,
    nonce: Nonce? = null,
    accessToken: DPoPAccessToken? = null
): String = SignedJWT(
    jwsHeader(publicRSAKey()),
    claims(uri, method, nonce, accessToken)
)
    .apply { sign(RSASSASigner(keyPair.toRSAPrivateKey())) }
    .serialize()

private fun claims(
    uri: URI,
    method: HttpMethod,
    nonce: Nonce?,
    accessToken: DPoPAccessToken?
): JWTClaimsSet =
    Builder()
        .jwtID(Uuid.random().toString())
        .issueTime(from(now()))
        .htu(uri)
        .htm(method)
        .nonce(nonce)
        .ath(accessToken)
        .build()

private fun jwsHeader(publicRSAJwk: RSAKey): JWSHeader =
    JWSHeader
        .Builder(RS256)
        .type(TYPE)
        .jwk(publicRSAJwk.toPublicJWK())
        .build()

private fun publicRSAKey(): RSAKey =
    RSAKey
        .Builder(keyPair.toRSAPublicKey())
        .algorithm(RS256)
        .build()

internal fun accessTokenHash(accessToken: String): String =
    getUrlEncoder()
        .withoutPadding()
        .encodeToString(
            getInstance("SHA-256")
                .digest(accessToken.toByteArray())
        )

private const val HTM_CLAIM_NAME = "htm"
private const val HTU_CLAIM_NAME = "htu"
private const val ATH_CLAIM_NAME = "ath"

private fun Builder.htm(method: HttpMethod) =
    claim(HTM_CLAIM_NAME, method.value)

private fun Builder.htu(uri: URI) =
    claim(HTU_CLAIM_NAME, uri.toString())

private fun Builder.ath(accessToken: DPoPAccessToken?) =
    apply { accessToken?.let { claim(ATH_CLAIM_NAME, accessTokenHash(accessToken.value)) } }

private fun Builder.nonce(nonce: Nonce?) =
    apply { nonce?.let { claim(NONCE_CLAIM_NAME, it.value) } }

@OptIn(ExperimentalEncodingApi::class)
private fun decodeKeypair(keypair: String): String = Base64.decode(keypair).decodeToString()
