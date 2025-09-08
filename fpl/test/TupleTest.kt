import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.StringReader

class TupleTest {
    fun runTest(prog: String, expected: String) {
        val lexers = listOf(Lexer(StringReader(prog), "input.fpl"))
        val output = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, output)
    }

    @Test
    fun inferTupleTypes() {
        val prog = """
            extern fun print(s:String)

            fun main()
                val t = (1, "Hello, World!")
                print(t)
        """.trimIndent()

        val expected = """
            input.fpl:5.10-5.10:  No function found for print((Int,String)) candidates are:-
            print(String)
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun tupleTypeInVarDecl() {
        val prog = """
            fun main()
                val t:(Int,String) = (1,"x")
        """.trimIndent()
        runTest(prog, "")
    }

    @Test
    fun tupleTypeMismatch() {
        val prog = """
            fun main()
                val t:(Int,String) = (1,2)
        """.trimIndent()
        val expected = """
            input.fpl:2.31-2.31:  Type mismatch got '(Int,Int)' when expecting '(Int,String)'
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun tupleTypeInFunctionParam() {
        val prog = """
            fun f(t:(Int,String))
                val dummy = t
                
            fun main()
                f((1,"x"))
        """.trimIndent()
        runTest(prog, "")
    }

    @Test
    fun tupleIndexing() {
        val prog = """
            extern fun print(s:String)
            extern fun print(i:Int)
                
            fun main()
                val t = (1,"x")
                print(t.0)
                print("\n")
                print(t.1)
        """.trimIndent()
        val expected = """
            1
            x
        """.trimIndent()
        runTest(prog, expected)
    }


    @Test
    fun tupleDestructuring() {
        val prog = """
            extern fun print(s:String)
            extern fun print(i:Int)
                
            fun main()
                val t = (1,"x")
                var a : Int
                var b : String
                (a,b) = t
                print(a)
                print("\n")
                print(b)
        """.trimIndent()
        val expected = """
            1
            x
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun destructuringDeclaration() {
        val prog = """
            extern fun print(s:String)
            extern fun print(i:Int)
                
            fun main()
                val t = (1,"x")
                val (a,b) = t
                print(a)
                print("\n")
                print(b)
        """.trimIndent()
        val expected = """
            1
            x
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun destructuringWithDifferentTypes() {
        val prog = """
        extern fun print(i:Int)
        extern fun print(s:String)
        extern fun print(b:Bool)

        fun makeTuple() -> (Int,String,Bool)
            return (42, "answer", true)

        fun main()
            val (i,s,b) = makeTuple()
            print(i)
            print("\n")
            print(s)
            print("\n")
            print(b)
    """.trimIndent()

        val expected = """
            42
            answer
            true
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun destructuringTooFewVariables() {
        val prog = """
            fun main()
                val t = (1,2)
                val (x) = t
        """.trimIndent()

        val expected = """
            input.fpl:3.5-3.7:  Destructuring expects 1 elements, got 2
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun destructuringTooManyVariables() {
        val prog = """
            fun main()
                val t = (1,2)
                val (x,y,z) = t
        """.trimIndent()

        val expected = """
            input.fpl:3.5-3.7:  Destructuring expects 3 elements, got 2
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun destructuringNonTuple() {
        val prog = """
            fun main()
                val (x,y) = 123
        """.trimIndent()

        val expected = """
            input.fpl:2.5-2.7:  Destructuring requires a tuple, got Int
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun destructuringRedeclarationError() {
        val prog = """
            fun main()
                val t = (1,2)
                val (x,y) = t
                val (x,z) = t
        """.trimIndent()

        val expected = """
            input.fpl:4.10-4.10:  Duplicate symbol: x
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun destructuringOptionalTypes() {
        val prog = """
        extern fun print(s:String)
        extern fun print(i:Int)

        fun main()
            # Simple inference
            val t1 = (1,"hello")
            val (a,b) = t1
            print(a)
            print("\n")
            print(b)
            print("\n")

            # Explicit types matching
            val t2 = (42,"world")
            val (x:Int, y:String) = t2
            print(x)
            print("\n")
            print(y)
            print("\n")

            # Mixed explicit and inferred
            val t3 = (99,"mix")
            val (m, n:String) = t3
            print(m)
            print("\n")
            print(n)
            print("\n")
    """.trimIndent()

        val expected = """
        1
        hello
        42
        world
        99
        mix
        
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun destructuringTypeMismatch() {
        val prog = """
        extern fun print(s:String)

        fun main()
            val t = (1,"oops")
            val (a:String, b:String) = t
            print(a)
    """.trimIndent()

        val expected = """
        input.fpl:5.10-5.10:  Cannot assign tuple element of type 'Int' to variable of type 'String'
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nestedTupleDisallowed() {
        val prog = """
            extern fun print(i:Int)
    
            fun main()
                val t = ((1,2), 3)
                val (a,b) = t  # should fail, nested tuple on lhs
                print(a)
        """.trimIndent()

        val expected = """
            input.fpl:4.19-4.19:  Cannot include type '(Int,Int)' in a tuple
            input.fpl:6.10-6.10:  No function found for print((Int,Int)) candidates are:-
            print(Int)
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun errableValueInTupleDisallowed() {
        val prog = """
            extern fun print(i:Int)
        
            enum Error(desc:String) [
                FILE_NOT_FOUND("File not found") ]
        
            fun openFile(name:String) -> Int!
                if (name = "missing.txt")
                    return Error.FILE_NOT_FOUND
                else
                    return 1
        
            fun main()
                val t = (openFile("missing.txt"), 42)
                val (a,b) = t  # should fail, errable value inside tuple
                print(a)
        """.trimIndent()

        val expected = """
            input.fpl:13.22-13.22:  Cannot include type 'Int!' in a tuple
            input.fpl:15.10-15.10:  No function found for print(Int!) candidates are:-
            print(Int)
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun lhsNestedTupleDisallowed() {
        val prog = """
        extern fun print(i:Int)

        fun main()
            val t = (1,2,3)
            val ((a,b),c) = t  # should fail, nested tuple on lhs
            print(a)
    """.trimIndent()

        val expected = """
        input.fpl:5.10-5.10:  input.fpl:5.10-5.10:  Got '(' when expecting '<Identifier>'
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun mismatchTupleSize() {
        val prog = """
            extern fun print(i:Int)
    
            fun main()
                val t = (1,2)
                val (a,b,c) = t  # should fail, lhs has more variables than tuple elements
                print(a)
        """.trimIndent()

        val expected = """
            input.fpl:5.5-5.7:  Destructuring expects 3 elements, got 2
            input.fpl:6.11-6.11:  'a' is not defined
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun tupleReturnValue() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
    
            fun make() -> (Int,String)
                return (123,"abc")
    
            fun main()
                val t = make()
                print(t.0)
                print("\n")
                print(t.1)
        """.trimIndent()

        val expected = """
            123
            abc
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun tupleParamDestructured() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
    
            fun consume(t:(Int,String))
                val (x,y) = t
                print(x)
                print("\n")
                print(y)
    
            fun main()
                consume((7,"seven"))
        """.trimIndent()

        val expected = """
            7
            seven
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun tupleAssignment() {
        val prog = """
        extern fun print(i:Int)
        extern fun print(s:String)

        fun main()
            var t = (1,"x")
            t = (2,"y")
            print(t.0)
            print("\n")
            print(t.1)
    """.trimIndent()

        val expected = """
        2
        y
    """.trimIndent()

        runTest(prog, expected)
    }



}