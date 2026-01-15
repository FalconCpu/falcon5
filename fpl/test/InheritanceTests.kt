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

    @Test
    fun isTest() {
        val prog = """
            extern fun print(s:String)

            class Animal            
            class Dog : Animal
            class Cat : Animal

            fun getAnimal() -> Animal
                return new Dog()
        
            fun main()
                var a = getAnimal()
                if (a is Dog)
                    print("a is Dog")
                else
                    print("a is not Dog")
        """.trimIndent()

        val expected = """
            a is Dog
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun isTest2() {
        val prog = """
            extern fun print(s:String)

            class Animal
            class Dog : Animal
            class Cat : Animal
            
            fun newDog() -> Animal
                return new Dog()
            
            fun newCat() -> Animal
                return new Cat()
            
            fun main()
                var d = newDog()
                if (d is Dog) 
                    print("d is Dog\n")
                else 
                    print("d is not Dog\n")
                        
                var c = newCat()
                if (c is Dog) 
                    print("c is Dog\n") 
                else 
                    print("c is not Dog\n")
            
                var a = new Animal()
                if (a is Dog) 
                    print("a is Dog\n")
                elsif (a is Cat)
                    print("a is Cat\n")
                else
                    print("a is not cat or dog\n")
        """.trimIndent()

        val expected = """
            d is Dog
            c is not Dog
            a is not cat or dog

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun isTest3() {
        val prog = """
            extern fun print(s:String)

            class Animal
            class Dog : Animal
            class Puppy : Dog

            fun newPuppy() -> Animal
                return new Puppy()
            fun newDog() -> Animal
                return new Dog()

            fun main()
                var p = newPuppy()
                if (p is Puppy)
                    print("puppy is Puppy\n")
                else
                    print("puppy is not Puppy\n")
                    
                if (p is Dog)
                    print("puppy is Dog\n")
                else
                    print("puppy is not Dog\n")
                    
                var d:Animal = newDog()
                if (d is Puppy)
                    print("dog is Puppy\n")
                else
                    print("dog is not Puppy\n")

        """.trimIndent()

        val expected = """
            puppy is Puppy
            puppy is Dog
            dog is not Puppy

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun isNullTest() {
        val prog = """
            extern fun print(s:String)
            
            class Animal
            class Dog : Animal

            fun newDog() -> Animal?
                return null
            
            
            fun main()
                var n = newDog()
                if (n is Dog)
                    print("n is Dog\n")
                else
                    print("n is not Dog\n")
        """.trimIndent()

        val expected = """
            n is not Dog

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun isMixedRuntimeValues() {
        val prog = """
            extern fun print(s:String)

            class Animal
            class Dog : Animal
            class Cat : Animal
            
            fun printType(a:Animal)
                if (a is Dog)
                    print("It's a Dog\n")
                elsif (a is Cat)
                    print("It's a Cat\n")
                else
                    print("It's an Animal\n")
            
            fun main()
                printType(new Dog())
                printType(new Cat())
                printType(new Animal())
        """.trimIndent()

        val expected = """
            It's a Dog
            It's a Cat
            It's an Animal

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun basicTypeRefinement() {
        val prog = """
            extern fun print(s:String)

            class Animal
            class Dog(val name:String) : Animal
            class Cat(val color:String) : Animal
            
            fun printType(a:Animal)
                if (a is Dog)
                    print("It's a Dog named ")
                    print(a.name)
                    print("\n")
                elsif (a is Cat)
                    print("It's a Cat with color ")
                    print(a.color)
                    print("\n")
                else
                    print("It's an Animal\n")
            
            fun main()
                printType(new Dog("Rex"))
                printType(new Cat("Whiskers"))
                printType(new Animal())
        """.trimIndent()

        val expected = """
            It's a Dog named Rex
            It's a Cat with color Whiskers
            It's an Animal

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun testRefinementInElseBranch() {
        val src = """
            extern fun print(s:String)

            class Animal
                fun speak()
                    print("???")

            class Cat : Animal
                fun meow()
                    print("Meow!")

            fun main()
                var a:Animal = new Cat()
                if (a is Cat)
                    a.meow()
                else
                    a.speak()
        """.trimIndent()

        val expected = """
            Meow!
        """.trimIndent()
        runTest(src, expected)
    }

    @Test
    fun testNullableRefinement() {
        val src = """
            extern fun print(s:String)

            class Animal
            class Dog : Animal
                fun bark()
                    print("Woof!")

            fun main()
                var a:Animal? = new Dog()
                if (a is Dog)
                    a.bark()
                else
                    print("not a Dog")
        """.trimIndent()

        val expected = """
            Woof!
        """.trimIndent()

        runTest(src, expected)
    }

    @Test
    fun testNullableRefinementFailsWithNull() {
        val src = """
            extern fun print(s:String)

            class Animal
            class Dog : Animal
                fun bark()
                    print("Woof!")

            fun main()
                var a:Animal? = null
                if (a is Dog)
                    a.bark()
                else
                    print("not a Dog")
        """.trimIndent()

        val expected = """
            not a Dog
        """.trimIndent()

        runTest(src, expected)
    }

    @Test
    fun testRefinementInLoop() {
        val src = """
            extern fun print(s:String)

            class Animal
            class Dog : Animal
                fun bark()
                    print("Woof!\n")
            class Cat : Animal
                fun meow() 
                    print("Meow!\n")

            fun main()
                var animals = InlineArray<Animal> [ new Dog(), new Cat(), new Animal() ]
                for a in animals
                    if (a is Dog)
                        a.bark()
                    elsif (a is Cat)
                        a.meow()
                    else
                        print("???")
        """.trimIndent()

        val expected = """
            Woof!
            Meow!
            ???
        """.trimIndent()

        runTest(src, expected)
    }

    @Test
    fun testRefinementDoesNotLeakOutOfIf() {
        val src = """
            extern fun print(s:String)

            class Animal
            class Dog : Animal
                fun bark()
                    print("Woof!")

            fun main()
                var a:Animal = new Dog()
                if (a is Dog)
                    a.bark()
                # Outside the if, 'a' is still Animal, so bark() is invalid
                a.bark()
        """.trimIndent()

        val expected = """
            input.fpl:13.7-13.10:  Class 'Animal' has no member 'bark'
        """.trimIndent()

        runTest(src, expected)
    }

    @Test
    fun testRefinementDoesNotLeakOutOfElse() {
        val src = """
            extern fun print(s:String)

            class Animal
            class Cat : Animal
                fun meow()
                    print("Meow!")

            fun main()
                var a:Animal = new Cat()
                if (a is Cat)
                    print("it's a Cat")
                else
                    a.meow()
        """.trimIndent()

        val expected = """
            input.fpl:13.11-13.14:  Class 'Animal' has no member 'meow'
        """.trimIndent()

        runTest(src, expected)
    }

    @Test
    fun testRefinementDoesNotLeakPastLoop() {
        val src = """
            extern fun print(s:String)

            class Animal
            class Dog : Animal
                fun bark()
                    print("Woof!")

            fun main()
                var a:Animal = new Dog()
                while 1<2
                    if (a is Dog)
                        a.bark()
                # After the loop body, refinement should not hold
                a.bark()
        """.trimIndent()

        val expected = """
            input.fpl:14.7-14.10:  Class 'Animal' has no member 'bark'
        """.trimIndent()

        runTest(src, expected)
    }

    @Test
    fun testRefinementWithNullCheckDoesNotPersist() {
        val src = """
            extern fun print(s:String)

            class Animal
                fun speak()
                    print("???")

            fun main()
                var a:Animal? = null
                if (a is Animal)
                    a.speak()
                # Here a is still nullable, so calling speak() directly is invalid
                a.speak()
        """.trimIndent()

        val expected = """
            input.fpl:12.7-12.11:  Cannot access member as expression may be null
        """.trimIndent()

        runTest(src, expected)
    }

    @Test
    fun unsafeCastNotAllowed() {
        val src = """
            extern fun print(s:String)

            class Animal
                fun speak()
                    print("???")

            fun main()
                var a = (1234 as Animal)        # unsafe cast, should be compile error
        """.trimIndent()

        val expected = """
            input.fpl:8.19-8.20:  Cannot cast type 'Int' to 'Animal'
        """.trimIndent()

        runTest(src, expected)
    }

    @Test
    fun unsafeCastAllowed() {
        val src = """
            extern fun print(s:String)

            class Animal
                fun speak()
                    print("???")

            fun main()
                var a = unsafe(1234 as Animal)     # unsafe cast, should be allowed
        """.trimIndent()

        val expected = """
        """.trimIndent()

        runTest(src, expected)
    }



}
