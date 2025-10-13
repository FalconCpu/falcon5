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
            . . AstFunctionDefStmt name=main parent=null qualifier=<End of Line> retType=null symbols={} syscall=null
            . . . AstVarDeclStmt astType=null mutable=true name=b
            . . . . AstIdentifier name=a
            . . . AstParameter kind=<End of Line> name=a
            . . . . AstTypeIdentifier name=Int

        """.trimIndent()
        runTest(prog, expected)
    }



}