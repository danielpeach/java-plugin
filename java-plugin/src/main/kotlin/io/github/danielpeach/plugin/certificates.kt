package io.github.danielpeach.plugin

import com.google.common.io.BaseEncoding
import io.netty.handler.ssl.util.FingerprintTrustManagerFactory
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.InputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.TrustManagerFactory

private const val SHA_256 = "SHA-256"
private const val BEGIN_CERT = "-----BEGIN CERTIFICATE-----"
private const val END_CERT = "-----END CERTIFICATE-----"
private val LINE_SEPARATOR: String = System.getProperty("line.separator")

internal fun parseBase64EncodedCertificate(cert: String): X509Certificate {
  return parseCertificate(Base64.getDecoder().decode(cert).inputStream())
}

internal fun parseCertificate(cert: String): X509Certificate {
  return parseCertificate(cert.byteInputStream())
}

internal fun parseCertificate(cert: InputStream): X509Certificate {
  val factory = CertificateFactory.getInstance("X.509")
  return factory.generateCertificate(cert) as X509Certificate
}

internal fun fingerprintCertificate(certificate: ByteArray, algorithm: String): String {
  val md = MessageDigest.getInstance(algorithm)
  return BaseEncoding.base16().encode(md.digest(certificate))
}

internal fun fingerprintTrustManagerFactoryForCerts(certs: List<X509Certificate>): TrustManagerFactory {
  val fingerprints = certs.map { cert ->
    fingerprintCertificate(cert.encoded, SHA_256)
  }
  return FingerprintTrustManagerFactory.builder(SHA_256).fingerprints(fingerprints).build()
}

internal fun generateCert(): Pair<PrivateKey, X509Certificate> {
  val generator = KeyPairGenerator.getInstance("EC")
  val keyPair = generator.genKeyPair()
  val owner = X500Name("CN=localhost")
  val notBefore = Date()
  val notAfter = Calendar.getInstance()
    .apply {
      time = notBefore
      add(Calendar.YEAR, 5)
    }
    .time

  val random = SecureRandom()

  val builder = JcaX509v3CertificateBuilder(
    owner, BigInteger(64, random), notBefore, notAfter, owner, keyPair.public
  )

  val signer = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)

  val holder = builder.build(signer)
  val cert = JcaX509CertificateConverter()
    .apply { setProvider(BouncyCastleProvider()) }
    .getCertificate(holder)
  cert.verify(keyPair.public)

  return keyPair.private to cert
}

internal fun writeCertAsPEM(cert: X509Certificate): String {
  val encoder = Base64.getMimeEncoder(64, LINE_SEPARATOR.toByteArray())
  val encoded = String(encoder.encode(cert.encoded))

  return BEGIN_CERT + LINE_SEPARATOR + encoded + LINE_SEPARATOR + END_CERT
}
