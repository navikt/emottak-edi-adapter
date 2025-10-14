package no.nav.emottak

import arrow.core.memoize
import com.nimbusds.jose.Algorithm.parse
import com.nimbusds.jose.jwk.KeyUse.SIGNATURE
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import java.util.Date
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.text.Charsets.UTF_8

val generateRsaJwk: () -> RSAKey = {
    RSAKeyGenerator(2048)
        .keyUse(SIGNATURE)
        .algorithm(parse("RS256"))
        .issueTime(Date())
        .generate()
}
    .memoize()

@OptIn(ExperimentalEncodingApi::class)
fun RSAKey.base64Encoded(): String = Base64.encode(toJSONString().toByteArray(UTF_8))
