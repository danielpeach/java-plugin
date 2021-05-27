package io.github.danielpeach.plugin

import java.security.cert.X509Certificate

internal data class Handshake(
  val core: Int,
  val pluginVersion: Int,
  val networkType: String,
  val networkAddress: String,
  val protocol: String,
  val serverCertificate: X509Certificate? = null,
)
