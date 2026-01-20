package utils

class Sha256ServiceTest extends munit.FunSuite {

  test("hash produces 64 character hex string") {
    val result = Sha256Service.hash("test")
    assertEquals(result.length, 64)
    assert(result.forall(c => c.isDigit || ('a' to 'f').contains(c)))
  }

  test("hash is deterministic") {
    val input = "hello world"
    val hash1 = Sha256Service.hash(input)
    val hash2 = Sha256Service.hash(input)
    assertEquals(hash1, hash2)
  }

  test("different inputs produce different hashes") {
    val hash1 = Sha256Service.hash("input1")
    val hash2 = Sha256Service.hash("input2")
    assertNotEquals(hash1, hash2)
  }

  test("hash matches known SHA-256 value") {
    val result = Sha256Service.hash("test")
    val expected = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
    assertEquals(result, expected)
  }

  test("empty string produces valid hash") {
    val result = Sha256Service.hash("")
    assertEquals(result.length, 64)
    assertEquals(result, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
  }
}