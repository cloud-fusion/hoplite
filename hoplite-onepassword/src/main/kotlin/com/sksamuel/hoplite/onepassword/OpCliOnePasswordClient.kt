package com.sksamuel.hoplite.onepassword

import java.io.IOException

data class ProcessResult(
  val exitCode: Int,
  val stdout: String,
  val stderr: String,
)

fun interface ProcessRunner {
  fun run(command: List<String>, environment: Map<String, String>): ProcessResult
}

internal object DefaultProcessRunner : ProcessRunner {
  override fun run(command: List<String>, environment: Map<String, String>): ProcessResult {
    return try {
      val process = ProcessBuilder(command).apply {
        environment().putAll(environment)
      }.start()

      val stdout = process.inputStream.bufferedReader().use { it.readText() }
      val stderr = process.errorStream.bufferedReader().use { it.readText() }
      val exitCode = process.waitFor()

      ProcessResult(exitCode, stdout, stderr)
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw OnePasswordException("Interrupted while invoking the 1Password CLI", e)
    } catch (e: IOException) {
      throw OnePasswordException("Failed invoking the 1Password CLI", e)
    }
  }
}

class OpCliOnePasswordClient(
  private val executable: String = "op",
  private val environment: Map<String, String> = emptyMap(),
  private val runner: ProcessRunner = DefaultProcessRunner,
) : OnePasswordClient {

  override fun read(reference: String): OnePasswordSecret {
    val result = runner.run(listOf(executable, "read", reference), environment)

    if (result.exitCode != 0) {
      val detail = result.stderr.ifBlank { result.stdout.ifBlank { "exit code ${result.exitCode}" } }
      throw OnePasswordException("Failed reading secret '$reference' from 1Password CLI: ${detail.trim()}")
    }

    return OnePasswordSecret(reference, result.stdout.trimEnd('\r', '\n'))
  }
}
