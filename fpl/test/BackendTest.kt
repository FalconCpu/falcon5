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
    fun functionCallTest() {
        val prog = """
            fun add(x:Int, y:Int) -> Int
                return x + y
                
            fun main()
                var z = add(1, 2)
        """.trimIndent()

        val expected = """
        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun helloWorld() {
        val prog = """
            extern fun print(s:String)

            fun main()
                print("Hello, World!")
        """.trimIndent()

        val expected = """
        """.trimIndent()

        runTest(prog, expected)
    }


}