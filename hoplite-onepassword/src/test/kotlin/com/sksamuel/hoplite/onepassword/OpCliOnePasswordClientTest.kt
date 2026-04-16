package com.sksamuel.hoplite.onepassword

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class OpCliOnePasswordClientTest : FunSpec() {
  init {

    test("should invoke the op cli with the provided environment and trim trailing newlines") {
      lateinit var command: List<String>
      lateinit var environment: Map<String, String>

      val client = OpCliOnePasswordClient(
        executable = "/usr/local/bin/op",
        environment = mapOf("OP_SERVICE_ACCOUNT_TOKEN" to "token"),
        runner = ProcessRunner { cmd, env ->
          command = cmd
          environment = env
          ProcessResult(0, "supersecret\n", "")
        }
      )

      client.read("op://Engineering/Database/password").let {
        it.reference shouldBe "op://Engineering/Database/password"
        it.value shouldBe "supersecret"
      }

      command.shouldContainExactly("/usr/local/bin/op", "read", "op://Engineering/Database/password")
      environment["OP_SERVICE_ACCOUNT_TOKEN"] shouldBe "token"
    }

    test("should surface cli failures") {
      val client = OpCliOnePasswordClient(
        runner = ProcessRunner { _, _ ->
          ProcessResult(1, "", "item not found")
        }
      )

      shouldThrow<OnePasswordException> {
        client.read("op://Engineering/Missing/password")
      }.message.shouldContain("op://Engineering/Missing/password")
        .shouldContain("item not found")
    }
  }
}
