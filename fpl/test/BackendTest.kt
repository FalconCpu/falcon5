import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class BackendTest {
    fun runTest(prog:String, expected:String) {
        val lexers = listOf(Lexer(StringReader(prog),"input.fpl"))
        val output = compile(lexers, StopAt.BACKEND)
        assertEquals(expected, output)
    }

    @Test
    fun helloWorld() {
        val prog = """
            extern fun print(s:String)

            fun main()
                print("Hello, World!")
        """.trimIndent()

        val expected = """
            Function topLevel:
            Function main():
            START
            LEA R1, STRING0
            CALL print(String)
            END

        """.trimIndent()

        runTest(prog, expected)
    }


}