package com.sksamuel.hoplite.onepassword

import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.fp.invalid
import com.sksamuel.hoplite.fp.valid
import com.sksamuel.hoplite.resolver.context.ContextResolver

class OnePasswordContextResolver(
  private val report: Boolean = false,
  private val createClient: () -> OnePasswordClient = { OpCliOnePasswordClient() },
) : ContextResolver() {

  constructor(
    report: Boolean = false,
    executable: String = "op",
    environment: Map<String, String> = emptyMap(),
  ) : this(report, { OpCliOnePasswordClient(executable, environment) })

  override val contextKey: String = "op"
  override val default: Boolean = false

  private val client by lazy(createClient)
  private val ops by lazy { OnePasswordOps(client) }

  override fun lookup(path: String, node: StringNode, root: Node, context: DecoderContext): ConfigResult<String?> {
    return try {
      val reference = "op://$path"
      val secret = ops.fetchSecret(reference)

      if (report) ops.report(context, secret)

      if (secret.value.isBlank())
        ConfigFailure.ResolverFailure("Empty secret '$reference' in 1Password").invalid()
      else
        secret.value.valid()
    } catch (e: OnePasswordException) {
      ConfigFailure.ResolverFailure(e.message ?: "Failed loading secret 'op://$path' from 1Password").invalid()
    } catch (e: Exception) {
      ConfigFailure.ResolverException("Failed loading secret 'op://$path' from 1Password", e).invalid()
    }
  }
}
