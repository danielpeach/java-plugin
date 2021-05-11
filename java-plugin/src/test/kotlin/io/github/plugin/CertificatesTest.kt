package io.github.plugin

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.assertions.succeeded

class CertificatesTest : JUnit5Minutests {
  fun tests() = rootContext {
    test("generate -> encode -> decode") {
      val (_, cert) = generateCert()

      val pemEncodedCert = writeCertAsPEM(cert)

      expectCatching { parseCertificate(pemEncodedCert).checkValidity() }.succeeded()
    }
  }
}
