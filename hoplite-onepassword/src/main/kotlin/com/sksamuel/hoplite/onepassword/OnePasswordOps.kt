package com.sksamuel.hoplite.onepassword

import com.sksamuel.hoplite.DecoderContext

class OnePasswordOps(private val client: OnePasswordClient) {

  fun fetchSecret(reference: String): OnePasswordSecret = client.read(reference)

  fun report(context: DecoderContext, secret: OnePasswordSecret) {
    context.reporter.report(Section, mapOf("Reference" to secret.reference))
  }

  companion object {
    const val Section = "1Password Lookups"
  }
}
