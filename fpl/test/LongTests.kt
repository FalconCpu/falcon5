import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

// Tests for Long type (64-bit integers)

class LongTests {

    fun runTest(prog: String, expected: String) {
        val lexers = listOf(Lexer(StringReader(prog), "input.fpl"), Lexer(FileReader("stdlib/printFloat.fpl"),"stdlib/printFloat.fpl"))
        val output = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, output)
    }

    @Test
    fun basicLong() {
        val prog = """         
            fun foo(a:Long) -> Long
                return a+1234567890123456789L
            end fun
               
            fun main()
                val a = foo(1L)
        """.trimIndent()

        val expected = """
        """.trimIndent()

        runTest(prog, expected)
    }



}