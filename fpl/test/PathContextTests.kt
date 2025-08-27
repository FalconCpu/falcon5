import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class PathContextTests {
    fun runTest(prog: String, expected: String) {
        val lexers = listOf(Lexer(StringReader(prog), "input.fpl"))
        val output = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, output)
    }

    @Test
    fun uninitializedVar() {
        val prog = """
            extern fun print(i:Int)

            fun main()
                var x:Int
                print(x)
        """.trimIndent()

        val expected = """
            input.fpl:5.11-5.11:  'x' is uninitialized
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun uninitializedVar1() {
        val prog = """
            extern fun print(i:Int)

            fun main()
                val x:Int       # uninitialized
                x = 42          # Now it is initialized
                print(x)
        """.trimIndent()

        val expected = """
            42
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun uninitializedVar2() {
        val prog = """
            extern fun print(i:Int)

            fun main()
                val x:Int       # uninitialized
                x = 42          # Now it is initialized
                x = 43          # error - cannot reassign a val
                print(x)
        """.trimIndent()

        val expected = """
            input.fpl:6.5-6.5:  'x' is not mutable
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun uninitializedVar3() {
        val prog = """
            extern fun print(i:Int)
            
            fun main()
                val x:Int
                if true
                    x = 1
                else
                    x = 2
                print(x)

        """.trimIndent()

        val expected = """
            1
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun uninitializedVar4() {
        val prog = """
            extern fun print(i:Int)
            
            fun main()
                val x:Int
                if true
                    x = 1
                print(x)

        """.trimIndent()

        val expected = """
            input.fpl:7.11-7.11:  'x' may be uninitialized
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun uninitializedVar5() {
        val prog = """
            extern fun print(i:Int)
            
            fun main()
                val x:Int
                if true
                    x = 1
                x = 2           # Error - cannot reassign a val
                print(x)

        """.trimIndent()

        val expected = """
            input.fpl:7.5-7.5:  'x' is not mutable
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun loopDefiniteInitialization() {
        val prog = """
            extern fun print(i:Int)
            fun main()
                var x:Int
                x = 0
                while x < 3
                    x = x + 1
                print(x)
        """.trimIndent()

        val expected = """
            3
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun loopMaybeUninitialized() {
        val prog = """
            extern fun print(i:Int)
            fun main()
                var x:Int
                while false
                    x = 42
                print(x)
        """.trimIndent()

        val expected = """
            input.fpl:6.11-6.11:  'x' may be uninitialized
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun loopAssignValError() {
        val prog = """
            extern fun print(i:Int)
            fun main()
                val x:Int
                while true
                    x = 42
                print(x)
        """.trimIndent()

        val expected = """
            input.fpl:5.9-5.9:  'x' is not mutable
            input.fpl:6.5-6.9:  Statement is unreachable
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nestedLoopMaybeUninitialized() {
        val prog = """
            extern fun print(i:Int)
            fun main()
                var x:Int
                var y:Int
                while false
                    x = 1
                    while false
                        y = 2
                print(x)
                print(y)
        """.trimIndent()

        val expected = """
            input.fpl:9.11-9.11:  'x' may be uninitialized
            input.fpl:10.11-10.11:  'y' may be uninitialized
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun loopBreakInitialization() {
        val prog = """
            extern fun print(i:Int)
            fun main()
                var x:Int
                while true
                    x = 42
                    break
                print(x)
        """.trimIndent()

        val expected = """
            42
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun loopContinueInitialization() {
        val prog = """
            extern fun print(i:Int)
            fun main()
                var x:Int
                var y:Int
                while y < 2
                    y = y + 1
                    x = y
                    continue
                print(x)
                print(y)
        """.trimIndent()

        val expected = """
            input.fpl:5.11-5.11:  'y' is uninitialized
            input.fpl:9.11-9.11:  'x' is uninitialized
            input.fpl:10.11-10.11:  'y' is uninitialized
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun conditionalInit() {
        val prog = """
            extern fun print(i:Int)
            fun main()
                var x:Int
                var y:Int
                y = 0
                if y = 0
                    x = 10
                else
                    x = 20
                    y = y + 1
                print(x)
        """.trimIndent()

        val expected = """
            10
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun loopConditionalMaybeUninitialized() {
        val prog = """
            extern fun print(i:Int)
            fun main()
                var x:Int
                var y:Int
                y = 0
                while y < 2
                    if y = 0
                        x = 10
                    y = y + 1
                print(x)
        """.trimIndent()

        val expected = """
            input.fpl:10.11-10.11:  'x' may be uninitialized
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun loopBreakEnsuresInit() {
        val prog = """
            extern fun print(i:Int)
            fun main()
                var x:Int
                while true
                    x = 99
                    break
                print(x)
        """.trimIndent()

        val expected = """
            99
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun loopContinuePreventsInit() {
        val prog = """
            extern fun print(i:Int)
            fun main()
                var x:Int
                var y:Int
                y = 0
                while y < 2
                    continue
                    x = 5
                    y = y + 1
                print(x)
        """.trimIndent()

        val expected = """
            input.fpl:8.9-8.9:  Statement is unreachable
            input.fpl:10.11-10.11:  'x' is uninitialized
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nullable() {

        val prog = """
            extern fun print(s:String)
            extern fun print(i:Int)
            class Cat(val name: String)
            
            fun greet(cat: Cat?)
                print("Hello, ")
                print(cat.name)             # error as cat is nullable
            
            fun main()
                val c = new Cat("Whiskers")
                greet(c)
        """.trimIndent()

        val expected = """
            input.fpl:7.19-7.19:  Cannot access member as expression may be null
        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun nullableAccess() {

        val prog = """
            extern fun print(s:String)
            extern fun print(i:Int)
            class Cat(val name: String)
            
            fun greet(cat: Cat?)
                if (cat != null)
                    print("Hello, ")
                    print(cat.name)
                else
                    print("No cat to greet")
            
            fun main()
                val c = new Cat("Whiskers")
                greet(c)
        """.trimIndent()

        val expected = """
            Hello, Whiskers
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nullableCheckError() {
        val prog = """
            extern fun print(i:Int)
            fun main()
                val x:Int?
                x = null
                print(x + 1)
        """.trimIndent()

        val expected = """
            input.fpl:5.13-5.13:  Invalid operator '+' for types 'Null' and 'Int'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nullableCheckElseBranch() {
        val prog = """
            extern fun print(i:Int)
            
            fun foo(a:Int?)
                if a != null
                    print(a + 1)   # ok
                else
                    print(0)
            
            fun main()
                foo(42)
        """.trimIndent()

        val expected = """
            43
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun andRefinement() {
        val prog = """
            extern fun print(s:String)
            class Cat(val name:String)
            
            fun foo(c:Cat?)
                if c != null and c.name = "Whiskers"
                    print("ok\n")
                else
                    print("not ok\n")
                    
            fun main()
                val c = new Cat("Whiskers")
                foo(c)
                val d = new Cat("Daisy")
                foo(d)
                foo(null)
        """.trimIndent()

        val expected = """
            ok
            not ok
            not ok
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun orRefinementFail() {
        val prog = """
            extern fun print(s:String)
            class Cat(val name:String)
            
            fun foo(c:Cat?)
                if c != null or true
                    print(c.name)   # error: may be null
        """.trimIndent()

        val expected = """
            input.fpl:6.21-6.21:  Cannot access member as expression may be null
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun doubleCheckAnd() {
        val prog = """
            extern fun print(s:String)
            class Cat(val name:String)
            
            fun foo(c:Cat?)
                if c != null and c != null
                    print(c.name)   # ok
                    print("\n")
                else
                    print("no cat\n")
                    
            fun main()
                val c = new Cat("Whiskers")
                foo(c)
                foo(null)
        """.trimIndent()

        val expected = """
            input.fpl:5.24-5.25:  Cannot compare types 'Cat' and 'Null'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nullableReturnElimination() {
        val prog = """
            extern fun print(s:String)
            class Cat(val name:String)
    
            fun greet(c:Cat?)
                if c = null
                    return
                print("Hello, ")
                print(c.name)    # ok, null branch returned
                print("\n")
                
            fun main()
                print("Part 1\n")
                greet(null)      # ok, null argument
                print("Part 2\n")
                val c = new Cat("Whiskers")
                greet(c)
                
                
        """.trimIndent()

        val expected = """
            Part 1
            Part 2
            Hello, Whiskers

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun nullableReturnNonNullBranch() {
        val prog = """
            extern fun print(s:String)
            class Cat(val name:String)
    
            fun greet(c:Cat?)
                if c != null
                    print(c.name)
                    return
                print("no cat")
                print(c.name)    # error: here c may be null
        """.trimIndent()

        val expected = """
            input.fpl:9.17-9.17:  Cannot access member 'name' of type 'Null'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nullableBreakInitialization() {
        val prog = """
            extern fun print(s:String)
            class Cat(val name:String)
    
            fun main()
                var c:Cat? = null
                while true
                    if c = null
                        c = new Cat("Mog")
                        break
                print(c.name)   # ok, loop guarantees break assigns c
        """.trimIndent()

        val expected = """
            Mog
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nullableBreakOneBranch() {
        val prog = """
            extern fun print(s:String)
            class Cat(val name:String)
    
            fun main()
                var c:Cat?
                c = null
                while true
                    if c = null
                        break
                    else
                        c = new Cat("Mog")
                print(c.name)   # error, c may still be null
        """.trimIndent()

        val expected = """
            input.fpl:12.17-12.17:  Cannot access member 'name' of type 'Null'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nullableContinue() {
        val prog = """
            extern fun print(s:String)
            class Cat(val name:String)
    
            fun main()
                var c:Cat? = null
                var i:Int = 0
                while i < 2
                    if c = null
                        i = i + 1
                        continue
                    print(c.name)   # unreachable if c==null path
                    i = i + 1
                print("done")
        """.trimIndent()

        val expected = """
            done
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nullableBreakPartial() {
        val prog = """
            extern fun print(s:String)
            class Cat(val name:String)
    
            fun main()
                var c:Cat? = null
                while true
                    if c = null
                        c = new Cat("Felix")
                        break
                    else
                        break
                print(c.name)   # The loop can only terminate through the break - so this is OK
        """.trimIndent()

        val expected = """
            Felix
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nullableContinuePreventsInit() {
        val prog = """
            extern fun print(s:String)
            class Cat(val name:String)
    
            fun main()
                var c:Cat? = null
                var i = 0
                while i < 3
                    if i = 0
                        c = new Cat("Tom")
                        continue   # c assigned but path jumps
                    i = i + 1
                print(c.name)   # error: continue skipped assignment
        """.trimIndent()

        val expected = """
            input.fpl:12.17-12.17:  Cannot access member as expression may be null
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nullableReturnEnsuresInit() {
        val prog = """
        extern fun print(s:String)
        class Cat(val name:String)

        fun foo(flag:Bool) -> Cat
            var c:Cat?
            c = null
            if flag
                c = new Cat("A")
                return c
            else
                c = new Cat("B")
                return c
            # no fallthrough

        fun main()
            val d = foo(true)
            print(d.name)   # ok
            print("\n")
            val e = foo(false)
            print(e.name)   # ok
    """.trimIndent()

        val expected = """
        A
        B
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nullableReturnPartial() {
        val prog = """
            extern fun print(s:String)
            class Cat(val name:String)
    
            fun foo(flag:Bool) -> Cat?
                var c:Cat? = null
                if flag
                    return c
                c = new Cat("Tabby")
                return c
    
            fun main()
                val d = foo(true)
                print(d.name)   # error, return may yield null
        """.trimIndent()

        val expected = """
            input.fpl:13.17-13.17:  Cannot access member as expression may be null
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun mustReturn_simple() {
        val prog = """
            extern fun print(i:Int)
            fun foo() -> Int
                return 42
    
            fun main()
                print(foo())
        """.trimIndent()

        val expected = """
            42
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun mustReturn_missing() {
        val prog = """
            extern fun print(i:Int)
            fun foo() -> Int
                val x = 1
                # missing return
    
            fun main()
                print(foo())
        """.trimIndent()

        val expected = """
            input.fpl:2.1-2.3:  Function 'foo' must return a value along all paths
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun mustReturn_ifElseBoth() {
        val prog = """
            extern fun print(i:Int)
            
            fun foo() -> Int
                if true
                    return 1
                else
                    return 2
    
            fun main()
                print(foo())
        """.trimIndent()

        val expected = """
            1
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun mustReturn_ifElseMissing() {
        val prog = """
            extern fun print(i:Int)
            fun foo() -> Int
                if true
                    return 1
                # else branch has no return
    
            fun main()
                print(foo())
        """.trimIndent()

        val expected = """
            input.fpl:2.1-2.3:  Function 'foo' must return a value along all paths
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun mustReturn_whileLoop() {
        val prog = """
            extern fun print(i:Int)
            fun foo() -> Int
                while false
                    return 1
                # loop might not execute
                # so missing return
    
            fun main()
                print(foo())
        """.trimIndent()

        val expected = """
            input.fpl:2.1-2.3:  Function 'foo' must return a value along all paths
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun mustReturn_withBreak() {
        val prog = """
            extern fun print(i:Int)
            fun foo() -> Int
                while true
                    break
                return 99
    
            fun main()
                print(foo())
        """.trimIndent()

        val expected = """
            99
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun mustReturn_withReturnInLoop() {
        val prog = """
            extern fun print(i:Int)
            fun foo() -> Int
                while true
                    return 5
    
            fun main()
                print(foo())
        """.trimIndent()

        val expected = """
            5
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun mustReturn_nestedIfs() {
        val prog = """
            extern fun print(i:Int)
            fun foo() -> Int
                if true
                    if false
                        return 1
                    else
                        return 2
                # missing return after if
    
            fun main()
                print(foo())
        """.trimIndent()

        val expected = """
            input.fpl:2.1-2.3:  Function 'foo' must return a value along all paths
        """.trimIndent()

        runTest(prog, expected)
    }


}


