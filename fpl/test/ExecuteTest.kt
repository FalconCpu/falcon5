import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class ExecuteTest {
    fun runTest(prog:String, expected:String) {
        val lexers = listOf(Lexer(StringReader(prog),"input.fpl"))
        val output = compile(lexers, StopAt.EXECUTE)
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
            Hello, World!
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun whileLoop() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)

            fun main()
                var i = 0
                while (i < 10)
                    print(i)
                    print('\n')
                    i = i + 1
        """.trimIndent()

        val expected = """
            0
            1
            2
            3
            4
            5
            6
            7
            8
            9

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun repeatLoop() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)

            fun main()
                var i = 0
                repeat
                    print(i)
                    print('\n')
                    i = i + 1
                until i=10
        """.trimIndent()

        val expected = """
            0
            1
            2
            3
            4
            5
            6
            7
            8
            9

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun ifTest() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)

            fun main()
                var i = 0
                repeat
                    if i=1
                        print("i is 1")
                    elsif i=2
                        print("i is 2")
                    else
                        print("i is not 1 or 2")
                    print('\n')
                    i = i + 1
                until i=5
        """.trimIndent()

        val expected = """
            i is not 1 or 2
            i is 1
            i is 2
            i is not 1 or 2
            i is not 1 or 2

        """.trimIndent()

        runTest(prog, expected)
    }



}