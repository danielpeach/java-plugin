package io.github.danielpeach.plugin

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.assertions.isSuccess
import java.nio.charset.Charset
import java.util.*

class CertificatesTest : JUnit5Minutests {
  fun tests() = rootContext {
    test("generate -> encode -> decode") {
      val (_, cert) = generateCert()

      val pemEncodedCert = writeCertAsPEM(cert)

      expectCatching { parseCertificate(pemEncodedCert).checkValidity() }.isSuccess()
    }

    test("generate -> base64 encode -> decode") {
      val (_, cert) = generateCert()

      val base64PemEncodedCert = writeCertAsPEM(cert).let { pem ->
        Base64.getEncoder().encode(pem.toByteArray()).toString(Charset.defaultCharset())
      }

      expectCatching {
        parseBase64EncodedCertificate(base64PemEncodedCert).checkValidity()
      }.isSuccess()
    }
  }
}
