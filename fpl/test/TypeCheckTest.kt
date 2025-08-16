import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class TypeCheckTest {
    fun runTest(prog:String, expected:String) {
        val lexers = listOf(Lexer(StringReader(prog),"input.fpl"))
        val output = compile(lexers, StopAt.TYPE_CHECK)
        assertEquals(expected, output)
    }

    @Test
    fun testSimpleProgram() {
        val prog = """
            fun main(a:Int, s:String, flag:Bool)
                var b = 23 + a * 2
                var d = flag and true or false
                var e = a < 10
                var f = s > "abc"
        """.trimIndent()

        val expected = """
            TctTop function=topLevel
            . TctFile
            . . TctFunctionDefStmt function=main(Int,String,Bool) name=main
            . . . TctVarDeclStmt sym=b
            . . . . TctBinaryExpr op=ADD_I type=Int
            . . . . . TctConstant type=Int value=IntValue:23
            . . . . . TctBinaryExpr op=MUL_I type=Int
            . . . . . . TctVariable sym=a type=Int
            . . . . . . TctConstant type=Int value=IntValue:2
            . . . TctVarDeclStmt sym=d
            . . . . TctBinaryExpr op=OR_B type=Bool
            . . . . . TctBinaryExpr op=AND_B type=Bool
            . . . . . . TctVariable sym=flag type=Bool
            . . . . . . TctConstant type=Bool value=IntValue:1
            . . . . . TctConstant type=Bool value=IntValue:0
            . . . TctVarDeclStmt sym=e
            . . . . TctBinaryExpr op=LT_I type=Bool
            . . . . . TctVariable sym=a type=Int
            . . . . . TctConstant type=Int value=IntValue:10
            . . . TctVarDeclStmt sym=f
            . . . . TctBinaryExpr op=GT_S type=Bool
            . . . . . TctVariable sym=s type=String
            . . . . . TctConstant type=String value=STRING0

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun testTypeErrors() {
        val prog = """
            fun main(a:Int, s:String, flag:Bool)
                val b = a + "abc"
                var f = s > a                
        """.trimIndent()

        val expected = """
            input.fpl:2.15-2.15:  Invalid operator '+' for types 'Int' and 'String'
            input.fpl:3.15-3.15:  Invalid operator '>' for types 'String' and 'Int'
        """.trimIndent()
        runTest(prog, expected)
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
            TctTop function=topLevel
            . TctFile
            . . TctFunctionDefStmt function=add(Int,Int) name=add
            . . . TctExpressionStmt
            . . . . TctReturnExpr type=Nothing
            . . . . . TctBinaryExpr op=ADD_I type=Int
            . . . . . . TctVariable sym=x type=Int
            . . . . . . TctVariable sym=y type=Int
            . . TctFunctionDefStmt function=main() name=main
            . . . TctVarDeclStmt sym=z
            . . . . TctCallExpr func=add(Int,Int) type=Int
            . . . . . TctConstant type=Int value=IntValue:1
            . . . . . TctConstant type=Int value=IntValue:2

        """.trimIndent()

        runTest(prog, expected)
    }


}