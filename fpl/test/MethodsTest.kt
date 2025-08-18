import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class MethodsTests {
    fun runTest(prog: String, expected: String) {
        val lexers = listOf(Lexer(StringReader(prog), "input.fpl"))
        val output = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, output)
    }

    @Test
    fun basicMethods() {
        val prog = """
            extern fun print(s:String)
            class Cat (val name:String)
                fun meow()
                    print("Meow! My name is ")
                    print(name)
                    print("\n")

            fun main()
                var cat = new Cat("Whiskers")
                cat.meow()
        """.trimIndent()

        val expected = """
            Meow! My name is Whiskers
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun methodWithParamsAndReturn() {
        val prog = """
            extern fun print(s:String)
            extern fun print(i:Int)
            
            class Math
                fun add(a:Int, b:Int) -> Int
                    return a + b
            
            fun main()
                val m = new Math()
                val result = m.add(40, 2)
                print(result)
        """.trimIndent()

        val expected = """
            42
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun methodUsesField() {
        val prog = """
            extern fun print(s:String)
            class Dog(val name:String)
                fun bark()
                    print("Woof from ")
                    print(name)
                    print("\n")
    
            fun main()
                val d = new Dog("Fido")
                d.bark()
        """.trimIndent()

        val expected = """
            Woof from Fido
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun methodReturnsInt() {
        val prog = """
            extern fun print(i:Int)
            class Math
                fun square(x:Int) -> Int
                    return x * x
    
            fun main()
                val m = new Math()
                val r = m.square(7)
                print(r)
        """.trimIndent()

        val expected = """
            49
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun methodCallsMethod() {
        val prog = """
            extern fun print(s:String)
            class Cat(val name:String)
                fun intro()
                    print("I am ")
                    print(name)
                    print("\n")
    
                fun meow()
                    this.intro()
                    print("Meow!\n")
    
            fun main()
                val c = new Cat("Whiskers")
                c.meow()
        """.trimIndent()

        val expected = """
            I am Whiskers
            Meow!
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun methodWithParams() {
        val prog = """
            extern fun print(i:Int)
            class Math
                fun add3(a:Int, b:Int, c:Int) -> Int
                    return a + b + c
    
            fun main()
                val m = new Math()
                val r = m.add3(10, 20, 30)
                print(r)
        """.trimIndent()

        val expected = """
            60
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun multipleInstances() {
        val prog = """
            extern fun print(s:String)
            class Dog(val name:String)
                fun bark()
                    print("Woof from ")
                    print(name)
                    print("\n")
    
            fun main()
                val d1 = new Dog("Rex")
                val d2 = new Dog("Spot")
                d1.bark()
                d2.bark()
        """.trimIndent()

        val expected = """
            Woof from Rex
            Woof from Spot
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun methodReturnsObject() {
        val prog = """
            extern fun print(s:String)
            class Person(val name:String)
                fun clone() -> Person
                    return new Person(name)
    
            fun main()
                val p1 = new Person("Alice")
                val p2 = p1.clone()
                print(p2.name)
        """.trimIndent()

        val expected = """
            Alice
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun wrongArgCount() {
        val prog = """
            class Math
                fun add(a:Int, b:Int) -> Int
                    return a + b
    
            fun main()
                val m = new Math()
                val r = m.add(42)   # missing 1 argument
        """.trimIndent()

        val expected = """
            input.fpl:7.18-7.18:  No function found for add(Int) candidates are:-
            Math/add(Int,Int)
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun wrongArgType() {
        val prog = """
            class Math
                fun square(x:Int) -> Int
                    return x * x
    
            fun main()
                val m = new Math()
                val r = m.square("oops")   # wrong type
        """.trimIndent()

        val expected = """
            input.fpl:7.21-7.21:  No function found for square(String) candidates are:-
            Math/square(Int)
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun methodDoesNotExist() {
        val prog = """
            extern fun print(s:String)
            
            class Cat(val name:String)
                fun meow() 
                    print("Meow! My name is ")
                    print(name)
                    print("\n")
    
            fun main()
                val c = new Cat("Whiskers")
                c.bark()   # no such method
        """.trimIndent()

        val expected = """
            input.fpl:11.11-11.11:  Class 'Cat' has no member 'bark'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun methodUsedAsField() {
        val prog = """
            extern fun print(s:String)
            class Cat(val name:String)
                fun meow()
                    print("Meow! My name is ")
                    print(name)
                    print("\n")
    
            fun main()
                val c = new Cat("Kitty")
                print(c.meow)   # missing ()
        """.trimIndent()

        val expected = """
            input.fpl:10.17-10.17:  Method references not supported yet
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun wrongReturnType() {
        val prog = """
            class Math
                fun getName() -> String
                    return 123   # wrong type
    
            fun main()
                val m = new Math()
                val s = m.getName()
        """.trimIndent()

        val expected = """
            input.fpl:3.16-3.18:  Type mismatch got 'Int' when expecting 'String'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun missingReturn() {
        val prog = """
            class Math
                fun double(x:Int) -> Int
                    if x > 0
                        return x * 2
                    # no return for x <= 0
    
            fun main()
                val m = new Math()
                val r = m.double(-5)
        """.trimIndent()

        val expected = """
            input.fpl:2.5-2.7:  Function 'double' must return a value along all paths
        """.trimIndent()

        runTest(prog, expected)
    }



}