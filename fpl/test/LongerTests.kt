import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

class LongerTests {
    fun runTest(prog: String, expected: String) {
        val files = listOf("stdlib/list.fpl")
        val lexers1 = files.map { Lexer(FileReader(it), it) }
        val lexers = lexers1 + Lexer(StringReader(prog), "input.fpl")
        val output = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, output)
    }

    @Test
    fun listTest() {
        val prog = """
            extern fun print(s:String)

            fun main()
                val a = new List<String>()
                a.add("Hello, ")
                a.add("World!")
                for s in a
                    print(s)
                end
        """.trimIndent()

        val expected = """
            Hello, World!
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arrayOfListTest() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)

            fun main()
                val a = InlineArray<List<Int>>(4){ new List<Int>() }
                a[0].add(10)
                a[1].add(20)
                a[2].add(30)
                a[3].add(40)
                a[3].add(50)
                for lst in a
                    for i in lst
                        print(i)
                        print(" ")
                    end
                    print("\n")
                end
                
        """.trimIndent()

        val expected = """
            10 
            20 
            30 
            40 50 

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arrayOfPoints() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)

            struct Point3d(x:Int, y:Int, z:Int)

            fun main() 
                val points = new Array<Point3d>[
                    Point3d(1,2,3),
                    Point3d(4,5,6),
                    Point3d(7,8,9) ]
                
                for p in points
                    print(p.x)
                    print(",")
                    print(p.y)
                    print(",")
                    print(p.z)
                    print("\n")
                    
                end
            
        """.trimIndent()

        val expected = """
            1,2,3
            4,5,6
            7,8,9
            
        """.trimIndent()

        runTest(prog, expected)
    }

}