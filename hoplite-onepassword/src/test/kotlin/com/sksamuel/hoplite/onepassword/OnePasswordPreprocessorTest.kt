package com.sksamuel.hoplite.onepassword

import com.sksamuel.hoplite.CommonMetadata
import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.Pos
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.decoder.DotPath
import com.sksamuel.hoplite.fp.Validated
import com.sksamuel.hoplite.parsers.PropsPropertySource
import com.sksamuel.hoplite.traverse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.Properties

class OnePasswordPreprocessorTest : FunSpec() {
  init {

    val goodReference = "op://Engineering/Database/password"
    val fakeClient = OnePasswordClient { reference ->
      when (reference) {
        goodReference -> OnePasswordSecret(reference, "supersecret")
        "op://Engineering/Blank/password" -> OnePasswordSecret(reference, " ")
        else -> throw OnePasswordException("Could not read secret '$reference' from 1Password")
      }
    }

    test("placeholder should be detected and used") {
      val props = Properties()
      props["a"] = goodReference

      ConfigLoaderBuilder.default()
        .addPreprocessor(OnePasswordPreprocessor { fakeClient })
        .addPropertySource(PropsPropertySource(props))
        .build()
        .loadConfigOrThrow<ConfigHolder>()
        .a.shouldBe("supersecret")
    }

    test("non matching values should pass through unchanged") {
      OnePasswordPreprocessor { fakeClient }.process(
        StringNode("plain-text", Pos.NoPos, DotPath.root, emptyMap()),
        DecoderContext.zero
      ).shouldBeInstanceOf<Validated.Valid<com.sksamuel.hoplite.Node>>().value shouldBe
        StringNode("plain-text", Pos.NoPos, DotPath.root, emptyMap())
    }

    test("node should be annotated with secret metadata") {
      val props = Properties()
      props["a"] = goodReference

      val node = ConfigLoaderBuilder.default()
        .addPreprocessor(OnePasswordPreprocessor { fakeClient })
        .addPropertySource(PropsPropertySource(props))
        .build()
        .loadNodeOrThrow()
        .traverse()
        .find { it.path == DotPath("a") }
        .shouldNotBeNull()

      node.meta[CommonMetadata.Secret] shouldBe true
      node.meta[CommonMetadata.UnprocessedValue] shouldBe goodReference
      node.meta[CommonMetadata.RemoteLookup] shouldBe "1Password '$goodReference'"
    }

    test("unknown secret should return error and include reference") {
      OnePasswordPreprocessor { fakeClient }.process(
        StringNode("op://Engineering/Missing/password", Pos.NoPos, DotPath.root, emptyMap()),
        DecoderContext.zero
      ).shouldBeInstanceOf<Validated.Invalid<ConfigFailure>>().error.description()
        .shouldContain("op://Engineering/Missing/password")
    }

    test("blank secret should return error and include empty secret message") {
      OnePasswordPreprocessor { fakeClient }.process(
        StringNode("op://Engineering/Blank/password", Pos.NoPos, DotPath.root, emptyMap()),
        DecoderContext.zero
      ).shouldBeInstanceOf<Validated.Invalid<ConfigFailure>>().error.description()
        .shouldContain("Empty secret")
    }
  }

  data class ConfigHolder(val a: String)
}
