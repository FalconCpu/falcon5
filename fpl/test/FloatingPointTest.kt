import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

class FloatingPointTest {
    fun runTest(prog: String, expected: String) {
        val lexers = listOf(Lexer(StringReader(prog), "input.fpl"), Lexer(FileReader("stdlib/printFloat.fpl"),"stdlib/printFloat.fpl"))
        val output = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, output)
    }

    @Test
    fun basicFloat() {
        val prog = """            
            fun main()
                val a = 1.0
                val b = 2.0
                val c = a + b
                print(c)
                print('\n')
                print(3.14159)
                print('\n')
                print(-12.75)
                print('\n')
                print(0.125)
                print('\n')
                print(123.0)
                print('\n')

        """.trimIndent()

        val expected = """
            3
            3.14159
            -12.75
            0.125
            123

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun chainedExpressions() {
        val prog = """
            fun main()
                val a = 1.5
                val b = 2.25
                val c = 0.5
                val d = a + b * c - 1.0
                print(d)
                print('\n')
            end fun

        """.trimIndent()

        val expected = """
            1.625

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun associativityAndRounding() {
        val prog = """
            fun main()
                val a = 0.1
                val b = 0.2
                val c = 0.3
                print(a + b)
                print('\n')
                print((a + b) - c)
                print('\n')
            end fun
        """.trimIndent()

        val expected = """
            0.3
            0

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun divideByZero() {
        val prog = """            
            fun main()
                print(0.0)
                print('\n')
                print(-0.0)
                print('\n')
                print(1.0/0.0)
                print('\n')
                print(-1.0/0.0)
                print('\n')
            end fun

        """.trimIndent()

        val expected = """
            0
            0.
            inf
            -inf

        """.trimIndent()

        runTest(prog, expected)
    }
}