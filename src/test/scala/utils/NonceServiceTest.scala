package utils

class NonceServiceTest extends munit.FunSuite {

  test("generateNonce produces base64url encoded string") {
    val nonce = NonceService.generateNonce()
    assert(nonce.nonEmpty)
    assert(nonce.forall(c => c.isLetterOrDigit || c == '-' || c == '_'))
  }

  test("generateNonce with default length produces expected size") {
    val nonce = NonceService.generateNonce()
    assertEquals(nonce.length, 22)
  }

  test("generateNonce produces unique values") {
    val nonces = (1 to 100).map(_ => NonceService.generateNonce()).toSet
    assertEquals(nonces.size, 100)
  }

  test("generatePin produces digits only") {
    val pin = NonceService.generatePin()
    assert(pin.forall(_.isDigit))
  }

  test("generatePin respects length parameter") {
    assertEquals(NonceService.generatePin(4).length, 4)
    assertEquals(NonceService.generatePin(6).length, 6)
    assertEquals(NonceService.generatePin(8).length, 8)
  }

  test("generatePin produces unique values") {
    val pins = (1 to 50).map(_ => NonceService.generatePin()).toSet
    assert(pins.size > 40)
  }
}