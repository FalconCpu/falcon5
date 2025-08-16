import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class LexerTest {

    fun runTest(input: String, expected:String) {
        val lexer = Lexer(StringReader(input),"test.fpl")
        Log.clear()
        val sb = StringBuilder()
        while(true) {
            val token = lexer.nextToken()
            if (token.kind==TokenKind.EOF)
                break
            sb.append("${token.kind.name}:${token.value}\n")
        }
        assertEquals(expected, sb.toString())
    }


    @Test
    fun testLexer() {
        val prog = """
            fun main()   # this is a comment
                println(1 + 2)
        """.trimIndent()

        val expected = """
            FUN:fun
            IDENTIFIER:main
            OPENB:(
            CLOSEB:)
            EOL:<End of Line>
            INDENT:<Indent>
            IDENTIFIER:println
            OPENB:(
            INTLITERAL:1
            PLUS:+
            INTLITERAL:2
            CLOSEB:)
            EOL:<End of Line>
            DEDENT:<Dedent>
            
        """.trimIndent()
        runTest(prog, expected)
    }
}