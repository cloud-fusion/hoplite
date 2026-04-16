package com.sksamuel.hoplite.onepassword

import com.sksamuel.hoplite.CommonMetadata
import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.PrimitiveNode
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.fp.invalid
import com.sksamuel.hoplite.fp.valid
import com.sksamuel.hoplite.preprocessor.TraversingPrimitivePreprocessor
import com.sksamuel.hoplite.withMeta

class OnePasswordPreprocessor(
  private val report: Boolean = false,
  private val createClient: () -> OnePasswordClient = { OpCliOnePasswordClient() },
) : TraversingPrimitivePreprocessor() {

  constructor(
    report: Boolean = false,
    executable: String = "op",
    environment: Map<String, String> = emptyMap(),
  ) : this(report, { OpCliOnePasswordClient(executable, environment) })

  private val client by lazy(createClient)
  private val ops by lazy { OnePasswordOps(client) }
  private val regex = "op://.+".toRegex()

  override fun handle(node: PrimitiveNode, context: DecoderContext): ConfigResult<Node> = when (node) {
    is StringNode -> {
      when {
        !regex.matches(node.value) -> node.valid()
        else -> fetchSecret(node.value, node, context)
      }
    }
    else -> node.valid()
  }

  private fun fetchSecret(reference: String, node: StringNode, context: DecoderContext): ConfigResult<Node> {
    return try {
      val secret = ops.fetchSecret(reference)

      if (report) ops.report(context, secret)

      if (secret.value.isBlank())
        ConfigFailure.PreprocessorFailure("Empty secret '$reference' in 1Password", OnePasswordException("Empty secret '$reference' in 1Password")).invalid()
      else
        node.copy(value = secret.value)
          .withMeta(CommonMetadata.Secret, true)
          .withMeta(CommonMetadata.UnprocessedValue, node.value)
          .withMeta(CommonMetadata.RemoteLookup, "1Password '$reference'")
          .valid()
    } catch (e: OnePasswordException) {
      ConfigFailure.PreprocessorFailure("Failed loading secret '$reference' from 1Password", e).invalid()
    } catch (e: Exception) {
      ConfigFailure.PreprocessorFailure("Failed loading secret '$reference' from 1Password", e).invalid()
    }
  }
}
