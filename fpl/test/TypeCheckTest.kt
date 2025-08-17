import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class TypeCheckTest {
    fun runTest(prog:String, expected:String) {
        val lexers = listOf(Lexer(StringReader(prog),"input.fpl"))
        val output = compile(lexers, StopAt.TYPE_CHECK)
        assertEquals(expected, output)
    }

    @Test
    fun testSimpleProgram() {
        val prog = """
            fun main(a:Int, s:String, flag:Bool)
                var b = 23 + a * 2
                var d = flag and true or false
                var e = a < 10
                var f = s > "abc"
        """.trimIndent()

        val expected = """
            TctTop function=topLevel
            . TctFile
            . . TctFunctionDefStmt function=main(Int,String,Bool) name=main
            . . . TctVarDeclStmt sym=b
            . . . . TctBinaryExpr op=ADD_I type=Int
            . . . . . TctConstant type=Int value=IntValue:23
            . . . . . TctBinaryExpr op=MUL_I type=Int
            . . . . . . TctVariable sym=a type=Int
            . . . . . . TctConstant type=Int value=IntValue:2
            . . . TctVarDeclStmt sym=d
            . . . . TctOrExpr type=Bool
            . . . . . TctAndExpr type=Bool
            . . . . . . TctVariable sym=flag type=Bool
            . . . . . . TctConstant type=Bool value=IntValue:1
            . . . . . TctConstant type=Bool value=IntValue:0
            . . . TctVarDeclStmt sym=e
            . . . . TctIntCompareExpr op=LT_I type=Bool
            . . . . . TctVariable sym=a type=Int
            . . . . . TctConstant type=Int value=IntValue:10
            . . . TctVarDeclStmt sym=f
            . . . . TctStringCompareExpr op=GT_I type=Bool
            . . . . . TctVariable sym=s type=String
            . . . . . TctConstant type=String value=STRING0

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun testTypeErrors() {
        val prog = """
            fun main(a:Int, s:String, flag:Bool)
                val b = a + "abc"
                var f = s > a                
        """.trimIndent()

        val expected = """
            input.fpl:2.15-2.15:  Invalid operator '+' for types 'Int' and 'String'
            input.fpl:3.15-3.15:  Cannot compare types 'String' and 'Int'
        """.trimIndent()
        runTest(prog, expected)
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
            TctTop function=topLevel
            . TctFile
            . . TctFunctionDefStmt function=add(Int,Int) name=add
            . . . TctExpressionStmt
            . . . . TctReturnExpr type=Nothing
            . . . . . TctBinaryExpr op=ADD_I type=Int
            . . . . . . TctVariable sym=x type=Int
            . . . . . . TctVariable sym=y type=Int
            . . TctFunctionDefStmt function=main() name=main
            . . . TctVarDeclStmt sym=z
            . . . . TctCallExpr func=add(Int,Int) type=Int
            . . . . . TctConstant type=Int value=IntValue:1
            . . . . . TctConstant type=Int value=IntValue:2

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arraysTest() {
        val prog = """
            fun sum(a:Array<Int>) -> Int
                var total = 0
                var index = 0
                while index < a.length
                    total = total + a[index]
                    index = index + 1
                return total
        """.trimIndent()

        val expected = """
            TctTop function=topLevel
            . TctFile
            . . TctFunctionDefStmt function=sum(Array<Int>) name=sum
            . . . TctVarDeclStmt sym=total
            . . . . TctConstant type=Int value=IntValue:0
            . . . TctVarDeclStmt sym=index
            . . . . TctConstant type=Int value=IntValue:0
            . . . TctWhileStmt
            . . . . TctAssignStmt op==
            . . . . . TctVariable sym=total type=Int
            . . . . . TctBinaryExpr op=ADD_I type=Int
            . . . . . . TctVariable sym=total type=Int
            . . . . . . TctIndexExpr type=Int
            . . . . . . . TctVariable sym=a type=Array<Int>
            . . . . . . . TctVariable sym=index type=Int
            . . . . TctAssignStmt op==
            . . . . . TctVariable sym=index type=Int
            . . . . . TctBinaryExpr op=ADD_I type=Int
            . . . . . . TctVariable sym=index type=Int
            . . . . . . TctConstant type=Int value=IntValue:1
            . . . . TctIntCompareExpr op=LT_I type=Bool
            . . . . . TctVariable sym=index type=Int
            . . . . . TctMemberExpr member=length type=Int
            . . . . . . TctVariable sym=a type=Array<Int>
            . . . TctExpressionStmt
            . . . . TctReturnExpr type=Nothing
            . . . . . TctVariable sym=total type=Int

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arrayTypeError1() {
        val prog = """
            fun main()
                val a = new [1, 2, "three", 4]   # ERROR: String mixed with Int
        """.trimIndent()

        val expected = """
            input.fpl:2.24-2.30:  Type mismatch got 'String' when expecting 'Int'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arrayTypeError2() {
        val prog = """
            fun main()
                val a: Array<Int> = new ["a", "b", "c"]  # ERROR: String not compatible with Array<Int>

        """.trimIndent()

        val expected = """
            input.fpl:2.25-2.27:  Type mismatch got 'Array<String>' when expecting 'Array<Int>'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arrayTypeError3() {
        val prog = """
            fun main()
                val a = new Array<Int>()(1, 2)  # ERROR: Array expects exactly one size argument
        """.trimIndent()

        val expected = """
            input.fpl:2.13-2.15:  Array constructor requires exactly one argument
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arrayTypeError4() {
        val prog = """
            fun main()
                val a = new Array<Int>(-5)       # ERROR: negative array size
        """.trimIndent()

        val expected = """
            input.fpl:2.13-2.15:  Cannot create array of negative size
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arrayTypeError5() {
        val prog = """
            fun main()
                val a = new Array<Int>("five")    # ERROR: Non integer size
        """.trimIndent()

        val expected = """
            input.fpl:2.28-2.33:  Type mismatch got 'String' when expecting 'Int'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arrayTypeError6() {
        val prog = """
            fun main(x:Int)
                val a = const [1,2,x]     # ERROR: Non constant in array literal
        """.trimIndent()

        val expected = """
            input.fpl:2.24-2.24:  Cannot create constant array literal with non-constant element
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arrayInitializerLambda() {
        val prog = """
            extern fun print(i:Int)
                
            fun main()
                val arr = new Array<Int>(5){"hi"}
        """.trimIndent()

        val expected = """
            input.fpl:4.32-4.32:  Type mismatch got 'String' when expecting 'Int'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun forLoopErrors1() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            
            fun main()
                for i in 'a'..4
                    print(i)
                print('\n')
        """.trimIndent()

        val expected = """
            input.fpl:5.19-5.19:  Type mismatch got 'Int' when expecting 'Char'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun forLoopErrors2() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            
            fun main()
                for i in "a".."z"
                    print(i)
                print('\n')
        """.trimIndent()

        val expected = """
            input.fpl:5.22-5.22:  Range start must be of type Int or Char, got 'String'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun forLoopErrors3() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            
            fun main()
                for i in 3
                    print(i)
                print('\n')
        """.trimIndent()

        val expected = """
            input.fpl:5.9-5.9:   Cannot iterate over type 'Int'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun forLoopErrors4() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            
            fun main()
                for i : Int in 'a'..'z'
                    print(i)
                print('\n')
        """.trimIndent()

        val expected = """
            input.fpl:5.20-5.22:  Type mismatch got 'Char' when expecting 'Int'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun andTypeError() {
        val prog = """
        fun main()
            if "foo" and true
                return
        """.trimIndent()

        val expected = """
            input.fpl:2.8-2.12:  Type mismatch got 'String' when expecting 'Bool'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun orTypeError() {
        val prog = """
            fun main()
                if 1 or 'a'
                    return
        """.trimIndent()

        val expected = """
            input.fpl:2.8-2.8:  Type mismatch got 'Int' when expecting 'Bool'
            input.fpl:2.13-2.15:  Type mismatch got 'Char' when expecting 'Bool'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun notTypeError() {
        val prog = """
            fun main()
                if not 123
                    return
        """.trimIndent()

        val expected = """
            input.fpl:2.12-2.14:  Type mismatch got 'Int' when expecting 'Bool'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun breakOutsideLoop() {
        val prog = """
            extern fun print(i:Int)
    
            fun main()
                break
        """.trimIndent()

        val expected = """
            input.fpl:4.5-4.9:  Break statement outside of a loop
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun continueOutsideLoop() {
        val prog = """
            extern fun print(i:Int)
    
            fun main()
                continue
        """.trimIndent()

        val expected = """
            input.fpl:4.5-4.12:  Continue statement outside of a loop
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun newWithWrongNumberOfArgs() {
        val prog = """
            class Cat(val name:String, val age:Int)
    
            fun main()
                val c = new Cat("Whiskers")   # Missing age argument
        """.trimIndent()

        val expectedError = """
            input.fpl:4.13-4.15:  Constructor 'Cat' called with (String) when expecting (String,Int)
        """.trimIndent()

        runTest(prog, expectedError)
    }

    @Test
    fun newWithWrongArgType() {
        val prog = """
            class Cat(val name:String, val age:Int)
    
            fun main()
                val c = new Cat(42, 3)   # First argument should be String
        """.trimIndent()

        val expectedError = """
            input.fpl:4.13-4.15:  Constructor 'Cat' called with (Int,Int) when expecting (String,Int)
        """.trimIndent()

        runTest(prog, expectedError)
    }

    @Test
    fun accessUnknownField() {
        val prog = """
            extern fun print(s:String)
    
            class Dog(val name:String)
    
            fun main()
                val d = new Dog("Fido")
                print(d.age)    # No such field
        """.trimIndent()

        val expectedError = """
            input.fpl:7.16-7.16:  Class 'Dog' has no member 'age'
        """.trimIndent()

        runTest(prog, expectedError)
    }

    @Test
    fun fieldInitializerUsesUnknownName() {
        val prog = """
            class Person(val name:String)
                val greeting = "Hello " + fullname   # fullname not declared
        """.trimIndent()

        val expectedError = """
            input.fpl:2.31-2.38:  'fullname' is not defined
        """.trimIndent()

        runTest(prog, expectedError)
    }

    @Test
    fun duplicateFieldNames() {
        val prog = """
            class Point(val x:Int)
                val x = 42
        """.trimIndent()

        val expectedError = """
            input.fpl:2.5-2.7:  Duplicate symbol: x
        """.trimIndent()

        runTest(prog, expectedError)
    }
}