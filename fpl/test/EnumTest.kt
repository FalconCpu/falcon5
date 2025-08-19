import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class EnumTests {
    fun runTest(prog: String, expected: String) {
        val lexers = listOf(Lexer(StringReader(prog), "input.fpl"))
        val output = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, output)
    }

    @Test
    fun basicEnum() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            enum Color [
                RED, GREEN, BLUE ]
           
            fun main()
                val c = Color.RED
                print(c) 
        """.trimIndent()

        val expected = """
            input.fpl:9.10-9.10:  No function found for print(Color) candidates are:-
            print(Int)
            print(String)
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun basicEnum2() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            enum Color [
                RED, GREEN, BLUE ]
           
            fun print(c:Color)
                if c=Color.RED
                    print("Red")
                elsif c=Color.GREEN
                    print("Green")
                elsif c=Color.BLUE
                    print("Blue")
                else
                    print("Unknown color")
           
            fun main()
                print(Color.RED) 
                print("\n")
                print(Color.GREEN)
                print("\n")
                print(Color.BLUE)
                print("\n")
                
        """.trimIndent()

        val expected = """
            Red
            Green
            Blue

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun castIntToEnum() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            enum Color [
                RED, GREEN, BLUE ]
           
            fun print(c:Color)
                if c=Color.RED
                    print("Red")
                elsif c=Color.GREEN
                    print("Green")
                elsif c=Color.BLUE
                    print("Blue")
                else
                    print("Unknown color")

            fun main()
                print(0 as Color) 
                print("\n")
                print(1 as Color)
                print("\n")
                print(2 as Color)
                print("\n")
        """.trimIndent()

        val expected = """
            Red
            Green
            Blue
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun castEnumToInt() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            enum Color [
                RED, GREEN, BLUE ]
           
            fun main()
                print(Color.RED as Int)
                print("\n")
                print(Color.GREEN as Int)
                print("\n")
                print(Color.BLUE as Int)
                print("\n")                
        """.trimIndent()

        val expected = """
            0
            1
            2
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun outOfRangeCastIntToEnum() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            enum Color [
                RED, GREEN, BLUE ]

            fun print(c:Color)
                if c=Color.RED
                    print("Red")
                elsif c=Color.GREEN
                    print("Green")
                elsif c=Color.BLUE
                    print("Blue")
                else
                    print("Unknown color")
           
            fun main()
                print(4 as Color)
        """.trimIndent()

        val expected = """
            EXCEPTION Index out of range: pc=ffff02bc: data=00000004
            $ 1=00000004 $ 2=00000003 $ 3=00000000 $ 4=00000000 $ 5=00000000 $ 6=00000000 
            $ 7=00000000 $ 8=00000000 $ 9=00000000 $10=00000000 $11=00000000 $12=00000000 
            $13=00000000 $14=00000000 $15=00000000 $16=00000000 $17=00000000 $18=00000000 
            $19=00000000 $20=00000000 $21=00000000 $22=00000000 $23=00000000 $24=00000000 
            $25=00000000 $26=00000000 $27=00000000 $28=00000000 $29=00000000 $30=ffff0010 
            $31=03fffffc 
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun forLoopEnum() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            enum Color [
                RED, GREEN, BLUE ]
           
            fun print(c:Color)
                if c=Color.RED
                    print("Red")
                elsif c=Color.GREEN
                    print("Green")
                elsif c=Color.BLUE
                    print("Blue")
                else
                    print("Unknown color")

            fun main()
                for c in Color.values
                    print(c)
                    print("\n") 
        """.trimIndent()

        val expected = """
            Red
            Green
            Blue
            
        """.trimIndent()

        runTest(prog, expected)
    }

}
