package no.nav.emottak

import arrow.core.memoize
import com.nimbusds.jose.Algorithm.parse
import com.nimbusds.jose.jwk.KeyUse.SIGNATURE
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import java.util.Date

val generateRsaJwk: () -> RSAKey = {
    RSAKeyGenerator(2048)
        .keyUse(SIGNATURE)
        .algorithm(parse("RS256"))
        .issueTime(Date())
        .generate()
}
    .memoize()
