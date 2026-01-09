package no.nav.helsemelding.ediadapter.client

import arrow.core.memoize
import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addResourceSource

@OptIn(ExperimentalHoplite::class)
internal val config: () -> Config = {
    ConfigLoader.builder()
        .addResourceSource("/edi-adapter-client.conf")
        .withExplicitSealedTypes()
        .build()
        .loadConfigOrThrow<Config>()
}
    .memoize()
