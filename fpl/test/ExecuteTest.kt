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

    @Test
    fun arraysTest() {
        val prog = """
            extern fun print(i:Int)
            
            fun sum(a:Array<Int>) -> Int
                var total = 0
                var index = 0
                while index < a.length
                    total = total + a[index]
                    index = index + 1
                return total
                
            fun main()
                val arr = new Array<Int>(5)
                arr[0] = 1
                arr[1] = 2
                arr[2] = 3
                arr[3] = 4
                arr[4] = 5
                var result = sum(arr)
                print(result)
        """.trimIndent()

        val expected = """
            15
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arrayInitializerTest() {
        val prog = """
            extern fun print(i:Int)
            
            fun sum(a:Array<Int>) -> Int
                var total = 0
                var index = 0
                while index < a.length
                    total = total + a[index]
                    index = index + 1
                return total
                
            fun main()
                val arr = new [1,2,3,4,5]
                var result = sum(arr)
                print(result)
        """.trimIndent()

        val expected = """
            15
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arrayInitializerLambda() {
        val prog = """
            extern fun print(i:Int)
            
            fun sum(a:Array<Int>) -> Int
                var total = 0
                var index = 0
                while index < a.length
                    total = total + a[index]
                    index = index + 1
                return total
                
            fun main()
                val arr = new Array<Int>(5){it*2}
                var result = sum(arr)
                print(result)
        """.trimIndent()

        val expected = """
            20
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun localArrayLambda() {
        val prog = """
            extern fun print(i:Int)
            
            fun sum(a:Array<Int>) -> Int
                var total = 0
                var index = 0
                while index < a.length
                    total = total + a[index]
                    index = index + 1
                return total
                
            fun main()
                val arr = local Array<Int>(5){it*2}
                var result = sum(arr)
                print(result)
        """.trimIndent()

        val expected = """
            20
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun forLoop() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            
            fun main()
                for i in 1..5
                    print(i)
                    print('\n')
        """.trimIndent()

        val expected = """
            1
            2
            3
            4
            5
            
        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun forLoopDownto() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            
            fun main()
                for i in 10..>5
                    print(i)
                    print('\n')
        """.trimIndent()

        val expected = """
            10
            9
            8
            7
            6

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun forLoopChars() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            
            fun main()
                for i in 'a'..'z'
                    print(i)
                print('\n')
        """.trimIndent()

        val expected = """
            abcdefghijklmnopqrstuvwxyz

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun forLoopEmptyRange() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)
            
            fun main()
                for i in 'z'..'a'
                    print(i)
                print('\n')
                print("Done")
        """.trimIndent()

        val expected = """
            
            Done
        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun forLoopArray() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)
            
            fun main()
                val arr = new ['a', 'b', 'c', 'd', 'e']
                for i in arr
                    print(i)
                print('\n')
                print("Done")
        """.trimIndent()

        val expected = """
            abcde
            Done
        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun andOrTest() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)
            
            fun main()
                for x in 0.. 10
                    print(x)
                    print(' ')
                    if x >= 5 and x <= 8
                        print("middle\n")
                    elsif x=4 or x=9
                        print("edge\n")
                    else
                        print("out\n")
        """.trimIndent()

        val expected = """
            0 out
            1 out
            2 out
            3 out
            4 edge
            5 middle
            6 middle
            7 middle
            8 middle
            9 edge
            10 out

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun breakContinueTest() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)
            
            fun main()
                for i in 0..10
                    if i = 2
                        continue
                    if i = 8
                        break
                    print(i)
                    print(' ')            
        """.trimIndent()

        val expected = """
            0 1 3 4 5 6 7 
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun whileBreakContinue() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
    
            fun main()
                var x = 0
                while x < 10
                    x = x + 1
                    if x = 3
                        continue
                    if x = 5
                        break
                    print(x)
                    print('\n')
        """.trimIndent()

        val expected = """
            1
            2
            4
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun repeatBreakContinue() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
    
            fun main()
                var x = 0
                repeat
                    x = x + 1
                    if x = 2
                        continue
                    if x = 4
                        break
                    print(x)
                    print('\n')
                until x >= 5
        """.trimIndent()

        val expected = """
            1
            3
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun forArrayBreakContinue() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
    
            fun main()
                val arr = new [1,2,3,4,5]
                for x in arr
                    if x = 2
                        continue
                    if x = 4
                        break
                    print(x)
                    print('\n')
        """.trimIndent()

        val expected = """
            1
            3
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nestedLoopBreakContinue() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
    
            fun main()
                for i in 1..3
                    for j in 1..3
                        if j = 2
                            continue
                        if j = 3
                            break
                        print(i)
                        print(' ')
                        print(j)
                        print('\n')
        """.trimIndent()

        val expected = """
            1 1
            2 1
            3 1
            
        """.trimIndent()

        runTest(prog, expected)
    }


}