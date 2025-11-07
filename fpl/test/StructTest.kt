import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

class StructTest {
    fun runTest(prog: String, expected: String) {
        val lexers = listOf(
            Lexer(StringReader(prog), "input.fpl"),
            Lexer(FileReader("stdlib/printFloat.fpl"), "stdlib/printFloat.fpl")
        )
        val output = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, output)
    }

    @Test
    fun basicStruct() {
        val prog = """
            extern fun print(x:String)
            struct Point(x: Float, y: Float, name:String)
                        
            fun main()
                val a = Point(1.0, 2.0, "A")
                print(a.name)

        """.trimIndent()

        val expected = """
            A
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun structFieldsAreImmutable() {
        val prog = """
            extern fun print(x:String)
            struct Point(x: Float, y: Float, name:String)
                        
            fun main()
                val a = Point(1.0, 2.0, "A")
                a.name = "B"  # Error: cannot assign to immutable field
                print(a.name)

        """.trimIndent()

        val expected = """
            input.fpl:6.7-6.10:  Field name is not mutable
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun assignToStructVar() {
        val prog = """
            extern fun print(x:String)
            struct Point(x: Float, y: Float, name:String)
                        
            fun main()
                var r = Point(1.0, 2.0, "A")
                r = Point(3.0, 4.0, "B")
                print(r.name)

        """.trimIndent()

        val expected = """
            B
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun copyStructVar() {
        val prog = """
            extern fun print(x:String)
            struct Point(x: Float, y: Float, name:String)
                        
            fun main()
                var r = Point(1.0, 2.0, "A")
                var s = r
                print(s.name)

        """.trimIndent()

        val expected = """
            A
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arrayOfStruct() {
        val prog = """
            extern fun print(x:String)
            struct Point(x: Float, y: Float, name:String)
                        
            fun main()
                val a = new Array<Point>(3)
                a[0] = Point(1.0, 2.0, "A")
                a[1] = Point(3.0, 4.0, "B")
                a[2] = Point(5.0, 6.0, "C")
                print(a[0].name)

        """.trimIndent()

        val expected = """
            A
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun structAsParameter() {
        val prog = """
            extern fun print(x:String)
            struct Point(x: Float, y: Float, name:String)
                        
            fun printPoint(p: Point)
                print(p.x)
                print(" ")
                print(p.y)
                print(" ")
                print(p.name)
                print("\n")
                        
            fun main()
                val a = Point(1.0, 2.0, "A")
                printPoint(a)

        """.trimIndent()

        val expected = """
            1 2 A

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun structAsReturn() {
        val prog = """
            extern fun print(x:String)
            struct Point(x: Int, y: Int, name:String)
                        
            fun makePoint(x: Int, y: Int, name:String) -> Point
                return Point(x, y, name)
                        
            fun main()
                val a = makePoint(1, 2, "A")
                print(a.name)

        """.trimIndent()

        val expected = """
            A
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun classContainingStruct() {
        val prog = """
            extern fun print(x:String)
            struct Point(x: Float, y: Float, name:String)
            class Line(val start: Point, val ending: Point)
                        
            fun printLine(l:Line)
                print("Start: ")
                print(l.start.x)
                print(" ")
                print(l.start.y)
                print(" ")
                print(l.start.name)
                print("\n")
                print("End: ")
                print(l.ending.x)
                print(" ")
                print(l.ending.y)
                print(" ")
                print(l.ending.name)
                print("\n")
                        
            fun main()
                val a = new Line(Point(1.0, 2.0, "A"), Point(3.0, 4.0, "B"))
                printLine(a)

        """.trimIndent()

        val expected = """
            Start: 1 2 A
            End: 3 4 B

        """.trimIndent()

        runTest(prog, expected)
    }




}
