package com.sksamuel.hoplite.onepassword

data class OnePasswordSecret(
  val reference: String,
  val value: String,
)

fun interface OnePasswordClient {
  fun read(reference: String): OnePasswordSecret
}

class OnePasswordException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
