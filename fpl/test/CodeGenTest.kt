import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class CodeGenTest {
    fun runTest(prog:String, expected:String) {
        val lexers = listOf(Lexer(StringReader(prog),"input.fpl"))
        val output = compile(lexers, StopAt.CODE_GEN)
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
            Function topLevel:
            Function add(Int,Int):
            START
            MOV x, R1
            MOV y, R2
            ADD_I t0, x, y
            MOV R8, t0
            JMP L0
            L0:
            END
            Function main():
            START
            MOV t0, 1
            MOV t1, 2
            MOV R1, t0
            MOV R2, t1
            CALL add(Int,Int)
            MOV t2, R8
            MOV z, t2
            L0:
            END

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
            Function topLevel:
            Function main():
            START
            LEA t0, STRING0
            MOV R1, t0
            CALL print(String)
            L0:
            END

        """.trimIndent()

        runTest(prog, expected)
    }


}