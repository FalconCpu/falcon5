import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.StringReader

class InlineArrayTest {
    fun runTest(prog: String, expected: String) {
        val lexers = listOf(Lexer(StringReader(prog), "input.fpl"))
        val output = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, output)
    }

    @Test
    fun inlineArray() {
        val prog = """
            extern fun print(s:String)

            fun main()
                val t = local InlineArray<String>(5)
                t[0] = "Hello"
                t[1] = "World"
                print(t[0])
                print(" ")
                print(t[1])
        """.trimIndent()

        val expected = """
            Hello World
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun inlineArrayOutOfBounds() {
        val prog = """
            extern fun print(s:String)
            fun main()
                val t = local InlineArray<String>(5)
                t[0] = "Hello"
                t[6] = "World"
                print(t[0])
                print(" ")
                print(t[1])
        """.trimIndent()

        val expected = """
            input.fpl:5.6-5.6:  Index 6 out of bounds for InlineArray of size 5
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun inlineArrayAsParameter() {
        val prog = """
            extern fun print(s:String)
            fun printArray(arr: InlineArray<String>(5))
                for i in arr
                    print(i)
                    print("\n")
        
            fun main()
                val t = local InlineArray<String>(5)
                t[0] = "Hello"
                t[1] = "World"
                t[2] = "from"
                t[3] = "Inline"
                t[4] = "Array"
                printArray(t)
        """.trimIndent()

        val expected = """
            Hello
            World
            from
            Inline
            Array

        """.trimIndent()

        runTest(prog, expected)

    }

    @Test
    fun inlineArrayMismatchedSize() {
        val prog = """
            extern fun print(s:String)
            fun printArray(arr: InlineArray<String>(4))
                for i in arr
                    print(i)
                    print("\n")
        
            fun main()
                val t = local InlineArray<String>(5)
                t[0] = "Hello"
                t[1] = "World"
                t[2] = "from"
                t[3] = "Inline"
                t[4] = "Array"
                printArray(t)
        """.trimIndent()

        val expected = """
            input.fpl:14.15-14.15:  No function found for printArray(InlineArray<String>(5)) candidates are:-
            printArray(InlineArray<String>(4))
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun inlineArrayField() {
        val prog = """
            extern fun print(s:String)
            extern fun print(i:Int)

            class Container
                val arr : InlineArray<String>(5)
                var x = 0
                
                fun printAll()
                    for i in arr
                        print(i)
                        print("\n")
                    print("x = ")
                    print(x)
                    print("\n")
                
            fun main()
                val t = new Container()
                t.arr[0] = "Hello"
                t.arr[1] = "World"
                t.arr[2] = "from"
                t.arr[3] = "Inline"
                t.arr[4] = "Array"
                t.x = 42
                t.printAll()
        """.trimIndent()

        val expected = """
            Hello
            World
            from
            Inline
            Array
            x = 42

        """.trimIndent()

        runTest(prog, expected)

    }


}