package com.sksamuel.hoplite.onepassword

import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.fp.Validated
import com.sksamuel.hoplite.parsers.PropsPropertySource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.Properties

@OptIn(ExperimentalHoplite::class)
class OnePasswordContextResolverTest : FunSpec() {
  init {

    val secretPath = "Engineering/Database/password"
    val secretReference = "op://$secretPath"
    val fakeClient = OnePasswordClient { reference ->
      when (reference) {
        secretReference -> OnePasswordSecret(reference, "supersecret")
        else -> throw OnePasswordException("Could not read secret '$reference' from 1Password")
      }
    }

    test("context pattern should be detected and used inside surrounding text") {
      val props = Properties()
      props["a"] = "hello \${{ op:$secretPath }} world"

      ConfigLoaderBuilder.newBuilder()
        .addResolver(OnePasswordContextResolver { fakeClient })
        .addPropertySource(PropsPropertySource(props))
        .build()
        .loadConfigOrThrow<ConfigHolder>()
        .a.shouldBe("hello supersecret world")
    }

    test("prefix pattern should be detected and used") {
      val props = Properties()
      props["a"] = secretReference

      ConfigLoaderBuilder.newBuilder()
        .addResolver(OnePasswordContextResolver { fakeClient })
        .addPropertySource(PropsPropertySource(props))
        .build()
        .loadConfigOrThrow<ConfigHolder>()
        .a.shouldBe("supersecret")
    }

    test("unknown secret should return error and include reference") {
      val props = Properties()
      props["a"] = "\${{ op:Engineering/Missing/password }}"

      ConfigLoaderBuilder.newBuilder()
        .addResolver(OnePasswordContextResolver { fakeClient })
        .addPropertySource(PropsPropertySource(props))
        .build()
        .loadConfig<ConfigHolder>()
        .shouldBeInstanceOf<Validated.Invalid<ConfigFailure>>().error.description()
        .shouldContain("op://Engineering/Missing/password")
    }
  }

  data class ConfigHolder(val a: String)
}
