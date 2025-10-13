import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class VarargTests {
    fun runTest(prog: String, expected: String) {
        val lexers = listOf(Lexer(StringReader(prog), "input.fpl"))
        val output = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, output)
    }

    @Test
    fun basicVararg() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            fun printAll(vararg args:String)
                for a in args
                    print(a)
                    print(" ")
                print("\n")
            
            fun main()
                printAll("Hello", "world!", "This", "is", "a", "test.")

        """.trimIndent()

        val expected = """
            Hello world! This is a test. 
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun mixedVarargs() {
        val prog = """
        extern fun print(i:Int)
        extern fun print(s:String)
        
        fun log(level:Int, vararg messages:String)
            print(level)
            for msg in messages
                print(" ")
                print(msg)
            print("\n")
        
        fun main()
            log(1)
            log(2, "Hello")
            log(3, "Multiple", "words", "here")
    """.trimIndent()

        val expected = """
        1
        2 Hello
        3 Multiple words here
        
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun emptyVarargs() {
        val prog = """
        extern fun print(i:Int)
        extern fun print(s:String)
        
        fun showAll(vararg items:Int)
            for x in items
                print(x)
                print(" ")
            print("\n")
        
        fun main()
            showAll()
            showAll(42)
            showAll(1,2,3)
    """.trimIndent()

        val expected = """
        
        42 
        1 2 3 
        
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun expressionVarargs() {
        val prog = """
        extern fun print(i:Int)
        extern fun print(s:String)
        
        fun sum(vararg nums:Int) -> Int
            var total = 0
            for n in nums
                total = total + n
            return total
        
        fun main()
            print(sum(1,2,3))
            print("\n")
            print(sum(4+1, 2*2, 3))
    """.trimIndent()

        val expected = """
        6
        12
    """.trimIndent()

        runTest(prog, expected)
    }
}