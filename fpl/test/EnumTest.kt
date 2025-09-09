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
            input.fpl:18.13-18.14:  Value 4 is out of range for enum 'Color'
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

    @Test
    fun enumProperties() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            enum Color(name:String) [
                RED("Red"),
                GREEN("Green"),
                BLUE("Blue") ]
           
            fun main()
                for c in Color.values
                    print(c.name)
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
    fun enumMultipleFields() {
        val prog = """
        extern fun print(s:String)
        extern fun print(i:Int)

        enum Planet(name:String, order:Int) [
            MERCURY("Mercury", 1),
            VENUS("Venus", 2),
            EARTH("Earth", 3) ]

        fun main()
            for p in Planet.values
                print(p.name)
                print(" is planet #")
                print(p.order)
                print("\n")
    """.trimIndent()

        val expected = """
        Mercury is planet #1
        Venus is planet #2
        Earth is planet #3
        
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun enumDirectAccess() {
        val prog = """
        extern fun print(s:String)

        enum Day(abbrev:String) [
            MON("Mon"), TUE("Tue"), WED("Wed") ]

        fun main()
            print(Day.MON.abbrev)
            print(Day.WED.abbrev)
    """.trimIndent()

        val expected = """
        MonWed
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun enumInIfElse() {
        val prog = """
        extern fun print(s:String)

        enum TrafficLight(msg:String) [
            RED("Stop"), YELLOW("Caution"), GREEN("Go") ]

        fun main()
            val t = TrafficLight.YELLOW
            if t = TrafficLight.RED
                print(t.msg)
            elsif t = TrafficLight.GREEN
                print(t.msg)
            else
                print("Other: ")
                print(t.msg)
    """.trimIndent()

        val expected = """
        Other: Caution
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun enumIterateAndCast() {
        val prog = """
        extern fun print(s:String)

        enum Color(name:String) [
            RED("Red"), GREEN("Green"), BLUE("Blue") ]

        fun main()
            for i in 0 ..< 3
                val c = i as Color
                print(c.name)
    """.trimIndent()

        val expected = """
        RedGreenBlue
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun enumAsParameter() {
        val prog = """
        extern fun print(s:String)

        enum Color(name:String) [
            RED("Red"), GREEN("Green"), BLUE("Blue") ]

        fun describe(c:Color)
            print("Color is ")
            print(c.name)
            print("\n")

        fun main()
            describe(Color.RED)
            describe(Color.GREEN)
            describe(Color.BLUE)
    """.trimIndent()

        val expected = """
        Color is Red
        Color is Green
        Color is Blue
        
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun enumAsReturn() {
        val prog = """
        extern fun print(s:String)

        enum Color(name:String) [
            RED("Red"), GREEN("Green"), BLUE("Blue") ]

        fun favorite() -> Color
            return Color.GREEN

        fun main()
            val f = favorite()
            print("My favorite is ")
            print(f.name)
    """.trimIndent()

        val expected = """
        My favorite is Green
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun enumEqualityInFunction() {
        val prog = """
        extern fun print(s:String)

        enum Light(name:String) [
            RED("Red"), GREEN("Green") ]

        fun isGo(l:Light) -> Bool
            return l = Light.GREEN

        fun main()
            if isGo(Light.RED)
                print("Go!")
            else
                print("Stop!")
    """.trimIndent()

        val expected = """
        Stop!
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun enumRoundTrip() {
        val prog = """
        extern fun print(s:String)

        enum Direction(symbol:String) [
            NORTH("N"), EAST("E"), SOUTH("S"), WEST("W") ]

        fun next(d:Direction) -> Direction
            if d = Direction.NORTH
                return Direction.EAST
            elsif d = Direction.EAST
                return Direction.SOUTH
            elsif d = Direction.SOUTH
                return Direction.WEST
            else
                return Direction.NORTH

        fun main()
            var d = Direction.NORTH
            for i in 0 ..< 4
                print(d.symbol)
                d = next(d)
    """.trimIndent()

        val expected = """
        NESW
    """.trimIndent()

        runTest(prog, expected)
    }


}
