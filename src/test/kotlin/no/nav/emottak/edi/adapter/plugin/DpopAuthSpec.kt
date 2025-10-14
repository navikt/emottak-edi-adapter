package no.nav.emottak.edi.adapter.plugin

import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.token.DPoPAccessToken
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.headersOf
import kotlinx.datetime.Clock
import no.nav.emottak.config
import no.nav.emottak.edi.adapter.model.DpopTokens
import no.nav.emottak.generateRsaJwk
import kotlin.time.Duration.Companion.seconds

class DpopAuthSpec : StringSpec(
    {
        val rsaJwk = generateRsaJwk()

        "should fetch token if none is cached and add headers" {
            withEnvironment("emottak-nhn-edi", rsaJwk.toString()) {
                val apiUrl = "https://api.test/Messages"

                val dummyTokens = DpopTokens(
                    accessToken = DPoPAccessToken("access-token"),
                    expiresAt = Clock.System.now().plus(60.seconds)
                )

                val mockEngine = MockEngine { request ->
                    request.headers["Authorization"] shouldBe "DPoP access-token"
                    val proof = request.headers["DPoP"]
                    proof shouldNotBe null

                    val signedJwt = SignedJWT.parse(proof)
                    val claims = signedJwt.jwtClaimsSet
                    claims.getStringClaim("htm") shouldBe Get.value
                    claims.getStringClaim("htu") shouldBe apiUrl

                    respond(
                        content = """{"ok":true}""",
                        status = OK,
                        headers = headersOf(ContentType, "application/json")
                    )
                }

                val client = HttpClient(mockEngine) {
                    install(DpopAuth) {
                        azureAuth = config().azureAuth
                        loadTokens = { dummyTokens }
                    }
                }

                val response = client.get(apiUrl)
                response.status shouldBe OK
            }
        }
    }
)
