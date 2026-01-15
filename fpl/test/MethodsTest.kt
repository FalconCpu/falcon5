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
    fun methodCallsMethodImplicit() {
        val prog = """
            extern fun print(s:String)
            class Cat(val name:String)
                fun intro()
                    print("I am ")
                    print(name)
                    print("\n")
    
                fun meow()
                    intro()
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
            input.fpl:11.7-11.10:  Class 'Cat' has no member 'bark'
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
            input.fpl:10.13-10.16:  Method references not supported yet
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
            input.fpl:2.9-2.14:  Function 'double' must return a value along all paths
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun voidUsedAsValue() {
        val prog = """
            class Math
                fun shout(x:Int)
                    # no return
                    val y = x + 1
    
            fun main()
                val m = new Math()
                val r = m.shout(5)   # should not be assignable
        """.trimIndent()

        val expected = """
            input.fpl:8.9-8.9:  Variable 'r' cannot be of type Unit
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun methodOnPrimitive() {
        val prog = """
            fun main()
                val x = 42
                x.bark()   # primitives have no methods
        """.trimIndent()

        val expected = """
            input.fpl:3.7-3.10:  Cannot access member 'bark' of type 'Int'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun chainedMethodCalls() {
        val prog = """
            extern fun print(s:String)
            class Person(val name:String)
                fun clone() -> Person
                    return new Person(name)
                fun greet()
                    print("Hello, I'm ")
                    print(name)
                    print("\n")
    
            fun main()
                val p = new Person("Alice")
                p.clone().greet()
        """.trimIndent()

        val expected = """
            Hello, I'm Alice
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun recursiveMethod() {
        val prog = """
            extern fun print(i:Int)
            class Math
                fun fact(n:Int) -> Int
                    if n <= 1
                        return 1
                    return n * fact(n - 1)
    
            fun main()
                val m = new Math()
                print(m.fact(5))
        """.trimIndent()

        val expected = """
            120
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun shadowedField() {
        val prog = """
            extern fun print(i:Int)
            class Box(val value:Int)
                fun show(value:Int)
                    print(value)   # should print parameter, not field
    
            fun main()
                val b = new Box(123)
                b.show(456)
        """.trimIndent()

        val expected = """
            456
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun methodOverloadByArity() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            class Math
                fun mul(a:Int, b:Int) -> Int
                    return a * b
                fun mul(a:Int, b:Int, c:Int) -> Int
                    return a * b * c
    
            fun main()
                val m = new Math()
                print(m.mul(2, 3))      # calls 2-arg
                print("\n")
                print(m.mul(2, 3, 4))   # calls 3-arg
        """.trimIndent()

        val expected = """
            6
            24
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun methodOverloadByType() {
        val prog = """
        extern fun print(s:String)
        extern fun print(i:Int)
        
        class Printer
            fun echo(x:Int)
                print(x)
            print("\n")
            fun echo(x:String)
                print(x)

        fun main()
            val p = new Printer()
            p.echo(42)
            print("\n")
            p.echo("Hello")
    """.trimIndent()

        val expected = """
        42
        Hello
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun methodOverloadAmbiguous() {
        val prog = """
            class Math
                fun add(a:Int, b:Int) -> Int
                    return a + b
                fun add(a:String, b:String) -> String
                    return a + b
    
            fun main()
                val m = new Math()
                val r = m.add(42, "oops")   # mismatched overload
        """.trimIndent()

        val expected = """
            input.fpl:5.18-5.18:  Invalid operator '+' for types 'String' and 'String'
            input.fpl:9.18-9.18:  No function found for add(Int,String) candidates are:-
            Math/add(Int,Int)
            Math/add(String,String)
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun methodOverloadChain() {
        val prog = """
            extern fun print(s:String)
            class Greeter(val name:String)
                fun greet()
                    print("Hello, I'm ")
                    print(name)
                    print("\n")
                    
                fun greet(times:Int)
                    for i in 1..times
                        print("Hi, I'm ")
                        print(name)
                        print("\n")
    
            fun main()
                val g = new Greeter("Bob")
                g.greet()
                g.greet(2)
        """.trimIndent()

        val expected = """
            Hello, I'm Bob
            Hi, I'm Bob
            Hi, I'm Bob
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun methodOverloadWrongReturn() {
        val prog = """
            class Math
                fun foo() -> Int
                    return 42
                fun foo(x:Int) -> String
                    return 99   # wrong type
    
            fun main()
                val m = new Math()
                val s = m.foo(1)
        """.trimIndent()

        val expected = """
            input.fpl:5.16-5.17:  Type mismatch got 'Int' when expecting 'String'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun freeTest() {
        val prog = """
            extern fun print(s:String)
            
            class Cat(val name:String, val age:Int)
                fun free()
                    print("Freeing cat ")
                    print(name)
                    print("\n")

            fun main()
                val c = new Cat("Mittens", 3)
                print(c.name)
                print("\n")
                free c
        """.trimIndent()

        val expected = """
            Mittens
            Freeing cat Mittens

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun getMethodTest() {
        val prog = """
            # a 'get' method can be called using array syntax
            
            extern fun print(i:Int)
            extern fun print(s:String)

            class List
                var length = 0
                var items = new Array<Int>(4)
                
                fun get(index:Int) -> Int
                    return items[index]
                    
                fun add(value:Int)
                    if items.length = length
                        val oldItems = items
                        items = new Array<Int>(oldItems.length * 2)
                        for index in 0..<oldItems.length
                            items[index] = oldItems[index]
                        free(oldItems)
                    items[length] = value
                    length = length + 1
                    
                fun set(index:Int, value:Int)
                    items[index] = value
                
            fun main()
                val lst = new List()
                lst.add(10)
                lst.add(20)
                lst.add(30)
                print(lst[1])   # should print 20
                print("\n")
                
                for i in lst
                    print(i)      # should print all items
                    print("\n")
        """.trimIndent()

        val expected = """
            20
            10
            20
            30
            
        """.trimIndent()

        runTest(prog, expected)
    }
}