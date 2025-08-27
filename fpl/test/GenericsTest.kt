import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class GenericsTests {
    fun runTest(prog: String, expected: String) {
        val lexers = listOf(Lexer(StringReader(prog), "input.fpl"))
        val output = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, output)
    }

    @Test
    fun basicGenerics() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            class Wrapper<T>(val value:T)
                
            fun main()
                val a = new Wrapper<String>("Hello world!")
                print(a.value)

        """.trimIndent()

        val expected = """
            Hello world!
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun differentTypes() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            class Wrapper<T>(val value:T)
                
            fun main()
                val a = new Wrapper<String>("Hello world!")
                val b = new Wrapper<Int>(42)
                print(a.value)
                print("\n")
                print(b.value)

        """.trimIndent()

        val expected = """
            Hello world!
            42
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun genericAsField() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            class Wrapper<T>(val value:T)
        
            class Holder<T>(val inner:Wrapper<T>)
                
            fun main()
                val h = new Holder<Int>(new Wrapper<Int>(123))
                print(h.inner.value)

        """.trimIndent()

        val expected = """
            123
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun inheritedGeneric() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            class Box<T>(val value:T)
            class NamedBox<U>(val name:String, value:U) : Box<U>(value)
            
            fun main()
                val nb = new NamedBox<Int>("Score", 99)
                print(nb.name)
                print(" ")
                print(nb.value)

        """.trimIndent()

        val expected = """
            Score 99
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun genericMethodReturn() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            class Wrapper<T>(val value:T)
                fun get() -> T
                    return value
            
            fun main()
                val a = new Wrapper<String>("Hello")
                print(a.get())
        """.trimIndent()

        val expected = "Hello"
        runTest(prog, expected)
    }

    @Test
    fun genericMethodParam() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            class Box<T>(var x:T)
                fun get() -> T
                    return x
                    
                fun set(y:T)
                    x = y
            
            fun main()
                val a = new Box<Int>(5)
                print(a.x)
                print("\n")
                a.set(10)
                print(a.x)
        """.trimIndent()

        val expected = """
            5
            10
        """.trimIndent()
        runTest(prog, expected)
    }


    @Test
    fun basicList() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            class List<T>
                var count = 0
                var items = new Array<T>(4)
                
                fun add(a:T)
                    if items.length=count
                        print("Resizing array\n")
                        val oldItems = items
                        items = new Array<T>(oldItems.length*2)
                        for index in 0..<oldItems.length
                            items[index] = oldItems[index]
                        # free(oldItems)
                    items[count] = a
                    count = count + 1
                    
                fun get(index:Int)->T
                    return items[index]
            
            fun main()
                val a = new List<String>
                a.add("Hello")
                a.add("World")
                a.add("Testing")
                a.add("Testing")
                a.add("1")
                a.add("2")
                a.add("3")
                
                for i in 0..<a.count
                    print(a.get(i))
                    print("\n")
        """.trimIndent()

        val expected = """
            Resizing array
            Hello
            World
            Testing
            Testing
            1
            2
            3

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun iteratorTest() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            class List<T>
                var length = 0
                var items = new Array<T>(4)
                
                fun add(a:T)
                    if items.length=length
                        val oldItems = items
                        items = new Array<T>(oldItems.length*2)
                        for index in 0..<oldItems.length
                            items[index] = oldItems[index]
                        # free(oldItems)
                    items[length] = a
                    length = length + 1
                    
                fun get(index:Int)->T
                    return items[index]
            
            fun main()
                val a = new List<String>
                a.add("Hello")
                a.add("World")
                a.add("Testing")
                a.add("Testing")
                a.add("1")
                a.add("2")
                a.add("3")
                
                for i in a
                    print(i)
                    print("\n")
        """.trimIndent()

        val expected = """
            Hello
            World
            Testing
            Testing
            1
            2
            3

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun iterateIntArray() {
        val prog = """
            extern fun print(i:Int)
    
            fun main()
                val arr = new Array<Int>(3)
                arr[0] = 10
                arr[1] = 20
                arr[2] = 30
                for x in arr
                    print(x)
        """.trimIndent()

        val expected = "102030"
        runTest(prog, expected)
    }

    @Test
    fun iterateNestedLoop() {
        val prog = """
            extern fun print(s:String)
    
            class PairList
                var length = 2
                fun get(i:Int)->Array<String>
                    if i=0 
                        return new ["A","B"]
                    else 
                        return new ["C","D"]
    
            fun main()
                val p = new PairList
                for row in p
                    for s in row
                        print(s)
        """.trimIndent()

        val expected = "ABCD"
        runTest(prog, expected)
    }

    @Test
    fun notIterableNoGet() {
        val prog = """
            extern fun print(i:Int)
            
            class Foo
                var length = 3
    
            fun main()
                val f = new Foo
                for x in f
                    # should fail, Foo has no get()
                    print(x)
        """.trimIndent()

        val expected = """
            input.fpl:8.9-8.9:  Type 'Foo' is not iterable
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun notIterableNoLength() {
        val prog = """
            extern fun print(i:Int)

            class Foo
                fun get(i:Int)->Int
                    return i
    
            fun main()
                val f = new Foo
                for x in f
                    # should fail, Foo has no length
                    print(x)
        """.trimIndent()
        val expected = """
            input.fpl:9.9-9.9:  Type 'Foo' is not iterable
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun invalidGetSignature() {
        val prog = """
            extern fun print(i:Int)
            
            class Foo
                var length = 2
                fun get()->Int
                    return 42
    
            fun main()
                val f = new Foo
                for x in f
                    # should fail, get() needs an Int parameter
                    print(x)
        """.trimIndent()

        val expected = """
            input.fpl:10.9-10.9:  Type 'Foo' is not iterable
        """.trimIndent()
        runTest(prog, expected)
    }





}
