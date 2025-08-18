import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class InheritanceTests {
    fun runTest(prog: String, expected: String) {
        val lexers = listOf(Lexer(StringReader(prog), "input.fpl"))
        val output = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, output)
    }

    @Test
    fun basicInheritance() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            class Animal (val name:String)
            class Dog(name:String, val age:Int) : Animal(name)

            fun main()
                var a:Animal = new Dog("Rex", 5)
                print(a.name)
        """.trimIndent()

        val expected = """
            Rex
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun multiLevelInheritance() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            class A(val x:Int)
            class B(x:Int, val y:Int): A(x)
            class C(x:Int, y:Int, val z:Int): B(x,y)
    
            fun main()
                val c = new C(1,2,3)
                print(c.x)
                print("\n")
                print(c.y)
                print("\n")
                print(c.z)
        """.trimIndent()

        val expected = """
            1
            2
            3
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun fieldShadowingError() {
        val prog = """
            class A(val name:String)
            class B(val name:String): A("base")
            
            fun main()
                val b = new B("oops")
        """.trimIndent()

        val expected = """
            input.fpl:2.13-2.16:  Duplicate symbol: name
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun implicitSuperConstructorCall() {
        val prog = """
            extern fun print(i:Int)
            
            class A(val x:Int)
            class B(x:Int): A(x)
    
            fun main()
                val b = new B(42)
                print(b.x)
        """.trimIndent()

        val expected = """
            42
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun constructorParamsAndSuperCall() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            class A(val x:Int)
            class B(x:Int, val y:Int): A(x)
    
            fun main()
                val b = new B(10, 20)
                print(b.x)
                print("\n")
                print(b.y)
        """.trimIndent()

        val expected = """
            10
            20
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun emptySubclass() {
        val prog = """
            extern fun print(i:Int)
            
            class A(val x:Int)
            class B: A(99)
    
            fun main()
                val b = new B()
                print(b.x)
        """.trimIndent()

        val expected = """
            99
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun inheritanceWithDifferentTypes() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            class A(val name:String)
            class B(name:String, val age:Int): A(name)
            class C(name:String, age:Int, val active:Int): B(name, age)
    
            fun main()
                val c = new C("Fido", 7, 1)
                print(c.name)
                print("\n")
                print(c.age)
                print("\n")
                print(c.active)
        """.trimIndent()

        val expected = """
            Fido
            7
            1
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun virtualDispatchBasic() {
        val prog = """
            extern fun print(s:String)
    
            class Animal
                virtual fun speak()
                    print("???")
    
            class Dog : Animal
                override fun speak()
                    print("Woof")
    
            fun makeAnimal() -> Animal
                return new Dog()
    
            fun main()
                var a = makeAnimal()
                a.speak()
        """.trimIndent()

        val expected = """
            Woof
        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun multipleVirtualMethods() {
        val prog = """
            extern fun print(s:String)
            
            class Animal
                virtual fun speak()
                    print("???")
                virtual fun move()
                    print("...")
            
            class Dog : Animal
                override fun speak()
                    print("Woof")
                override fun move()
                    print("Run")
    
            fun main()
                var a:Animal = new Dog()
                a.speak()
                print(" ")
                a.move()
        """.trimIndent()

        val expected = """
            Woof Run
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun partialOverride() {
        val prog = """
            extern fun print(s:String)
            
            class Animal
                virtual fun speak()
                    print("???")
                virtual fun move()
                    print("...")
            
            class Sloth : Animal
                override fun move()
                    print("Slow")
    
            fun main()
                var a:Animal = new Sloth()
                a.speak()
                print(" ")
                a.move()
        """.trimIndent()

        val expected = """
            ??? Slow
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun chainedOverrides() {
        val prog = """
            extern fun print(s:String)
            
            class A
                virtual fun f()
                    print("A")
    
            class B : A
                override fun f()
                    print("B")
    
            class C : B
                override fun f()
                    print("C")
    
            fun main()
                var a:A = new C()
                a.f()
        """.trimIndent()

        val expected = """
            C
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun virtualAndNonVirtualMethods() {
        val prog = """
            extern fun print(s:String)

            class Animal
                virtual fun speak()
                    print("???")
                fun info()
                    print("I am an Animal")

            class Dog : Animal
                override fun speak()
                    print("Woof")
                fun wag()
                    print("Wagging tail")

            fun main()
                var a:Animal = new Dog()
                a.speak()   # should be virtual → Dog.speak
                print("\n")
                a.info()    # non-virtual → Animal.info
                print("\n")
                var d:Dog = new Dog()
                d.wag()     # non-virtual → Dog.wag
        """.trimIndent()

        val expected = """
            Woof
            I am an Animal
            Wagging tail
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun baseNonVirtualCalledThroughDerived() {
        val prog = """
            extern fun print(s:String)

            class Base
                fun greet()
                    print("Hello from Base")

            class Derived : Base
                fun extra()
                    print("Derived extra")

            fun main()
                var b:Base = new Derived()
                b.greet()   # static → Base.greet
                print("\n")
                var d:Derived = new Derived()
                d.extra()   # static → Derived.extra
        """.trimIndent()

        val expected = """
            Hello from Base
            Derived extra
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun overrideVirtualButKeepBaseNonVirtual() {
        val prog = """
            extern fun print(s:String)

            class A
                virtual fun foo()
                    print("A.foo\n")
                fun bar()
                    print("A.bar\n")

            class B : A
                override fun foo()
                    print("B.foo\n")
                fun baz()
                    print("B.baz\n")

            fun main()
                var a:A = new B()
                a.foo()   # virtual dispatch → B.foo
                a.bar()   # static → A.bar
                var b:B = new B()
                b.baz()   # static → B.baz
        """.trimIndent()

        val expected = """
            B.foo
            A.bar
            B.baz
            
        """.trimIndent()

        runTest(prog, expected)
    }
}
