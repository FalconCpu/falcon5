import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class ParserTest {
    fun runTest(prog:String, expected:String) {
        val lexers = listOf(Lexer(StringReader(prog),"input.fpl"))
        val output = compile(lexers, StopAt.PARSE)
        assertEquals(expected, output)
    }

    @Test
    fun testSimpleProgram() {
        val prog = """
            fun main(a:Int)
                var b = a
        """.trimIndent()

        val expected = """
            AstTop parent=null symbols={}
            . AstFile parent=null symbols={}
            . . AstFunctionDefStmt isExtern=false name=main parent=null retType=null symbols={}
            . . . AstVarDeclStmt astType=null mutable=true name=b
            . . . . AstIdentifier name=a
            . . . AstParameter name=a
            . . . . AstTypeIdentifier name=Int

        """.trimIndent()
        runTest(prog, expected)
    }



}