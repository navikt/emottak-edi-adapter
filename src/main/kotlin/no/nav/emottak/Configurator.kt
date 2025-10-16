package no.nav.emottak

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addResourceSource
import no.nav.emottak.edi.adapter.config.Config

@OptIn(ExperimentalHoplite::class)
fun config(): Config = ConfigLoader.builder()
    .addResourceSource("/application-personal.conf", optional = true)
    .addResourceSource("/application.conf")
    .withExplicitSealedTypes()
    .build()
    .loadConfigOrThrow<Config>()
