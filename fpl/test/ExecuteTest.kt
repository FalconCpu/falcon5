import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class ExecuteTest {
    fun runTest(prog:String, expected:String) {
        val lexers = listOf(Lexer(StringReader(prog),"input.fpl"))
        val output = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, output)
    }

    @Test
    fun helloWorld() {
        val prog = """
            extern fun print(s:String)

            fun main()
                print("Hello, World!")
        """.trimIndent()

        val expected = """
            Hello, World!
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun whileLoop() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)

            fun main()
                var i = 0
                while (i < 10)
                    print(i)
                    print('\n')
                    i = i + 1
        """.trimIndent()

        val expected = """
            0
            1
            2
            3
            4
            5
            6
            7
            8
            9

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun repeatLoop() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)

            fun main()
                var i = 0
                repeat
                    print(i)
                    print('\n')
                    i = i + 1
                until i=10
        """.trimIndent()

        val expected = """
            0
            1
            2
            3
            4
            5
            6
            7
            8
            9

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun ifTest() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)

            fun main()
                var i = 0
                repeat
                    if i=1
                        print("i is 1")
                    elsif i=2
                        print("i is 2")
                    else
                        print("i is not 1 or 2")
                    print('\n')
                    i = i + 1
                until i=5
        """.trimIndent()

        val expected = """
            i is not 1 or 2
            i is 1
            i is 2
            i is not 1 or 2
            i is not 1 or 2

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arraysTest() {
        val prog = """
            extern fun print(i:Int)
            
            fun sum(a:Array<Int>) -> Int
                var total = 0
                var index = 0
                while index < a.length
                    total = total + a[index]
                    index = index + 1
                return total
                
            fun main()
                val arr = new Array<Int>(5)
                arr[0] = 1
                arr[1] = 2
                arr[2] = 3
                arr[3] = 4
                arr[4] = 5
                var result = sum(arr)
                print(result)
        """.trimIndent()

        val expected = """
            15
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arrayInitializerTest() {
        val prog = """
            extern fun print(i:Int)
            
            fun sum(a:Array<Int>) -> Int
                var total = 0
                var index = 0
                while index < a.length
                    total = total + a[index]
                    index = index + 1
                return total
                
            fun main()
                val arr = new [1,2,3,4,5]
                var result = sum(arr)
                print(result)
        """.trimIndent()

        val expected = """
            15
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun arrayInitializerLambda() {
        val prog = """
            extern fun print(i:Int)
            
            fun sum(a:Array<Int>) -> Int
                var total = 0
                var index = 0
                while index < a.length
                    total = total + a[index]
                    index = index + 1
                return total
                
            fun main()
                val arr = new Array<Int>(5){it*2}
                var result = sum(arr)
                print(result)
        """.trimIndent()

        val expected = """
            20
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun localArrayLambda() {
        val prog = """
            extern fun print(i:Int)
            
            fun sum(a:InlineArray<Int>(5)) -> Int
                var total = 0
                var index = 0
                while index < a.length
                    total = total + a[index]
                    index = index + 1
                return total
                
            fun main()
                val arr = InlineArray<Int>(5){it*2}
                var result = sum(arr)
                print(result)
        """.trimIndent()

        val expected = """
            20
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun forLoop() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            
            fun main()
                for i in 1..5
                    print(i)
                    print('\n')
        """.trimIndent()

        val expected = """
            1
            2
            3
            4
            5
            
        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun forLoopDownto() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            
            fun main()
                for i in 10..>5
                    print(i)
                    print('\n')
        """.trimIndent()

        val expected = """
            10
            9
            8
            7
            6

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun forLoopChars() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            
            fun main()
                for i in 'a'..'z'
                    print(i)
                print('\n')
        """.trimIndent()

        val expected = """
            abcdefghijklmnopqrstuvwxyz

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun forLoopEmptyRange() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)
            
            fun main()
                for i in 'z'..'a'
                    print(i)
                print('\n')
                print("Done")
        """.trimIndent()

        val expected = """
            
            Done
        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun forLoopArray() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)
            
            fun main()
                val arr = new ['a', 'b', 'c', 'd', 'e']
                for i in arr
                    print(i)
                print('\n')
                print("Done")
        """.trimIndent()

        val expected = """
            abcde
            Done
        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun andOrTest() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)
            
            fun main()
                for x in 0.. 10
                    print(x)
                    print(' ')
                    if x >= 5 and x <= 8
                        print("middle\n")
                    elsif x=4 or x=9
                        print("edge\n")
                    else
                        print("out\n")
        """.trimIndent()

        val expected = """
            0 out
            1 out
            2 out
            3 out
            4 edge
            5 middle
            6 middle
            7 middle
            8 middle
            9 edge
            10 out

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun breakContinueTest() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)
            
            fun main()
                for i in 0..10
                    if i = 2
                        continue
                    if i = 8
                        break
                    print(i)
                    print(' ')            
        """.trimIndent()

        val expected = """
            0 1 3 4 5 6 7 
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun whileBreakContinue() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
    
            fun main()
                var x = 0
                while x < 10
                    x = x + 1
                    if x = 3
                        continue
                    if x = 5
                        break
                    print(x)
                    print('\n')
        """.trimIndent()

        val expected = """
            1
            2
            4
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun repeatBreakContinue() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
    
            fun main()
                var x = 0
                repeat
                    x = x + 1
                    if x = 2
                        continue
                    if x = 4
                        break
                    print(x)
                    print('\n')
                until x >= 5
        """.trimIndent()

        val expected = """
            1
            3
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun forArrayBreakContinue() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
    
            fun main()
                val arr = new [1,2,3,4,5]
                for x in arr
                    if x = 2
                        continue
                    if x = 4
                        break
                    print(x)
                    print('\n')
        """.trimIndent()

        val expected = """
            1
            3
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nestedLoopBreakContinue() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
    
            fun main()
                for i in 1..3
                    for j in 1..3
                        if j = 2
                            continue
                        if j = 3
                            break
                        print(i)
                        print(' ')
                        print(j)
                        print('\n')
        """.trimIndent()

        val expected = """
            1 1
            2 1
            3 1
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun constArrayBasic() {
        val prog = """
        extern fun print(i:Int)
        extern fun print(c:Char)
        extern fun print(s:String)

        fun main()
            val arr = const ["apple", "banana", "cherry"]
            for i in arr
                print(i)
                print('\n')
        """.trimIndent()

        val expected = """
            apple
            banana
            cherry

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun constArrayIndexing() {
        val prog = """
            extern fun print(i:String)
            extern fun print(c:Char)
            
            fun main()
                val arr = const ["apple", "banana", "cherry"]
                print(arr[0])   # apple
                print('\n')
                print(arr[2])   # cherry
                print('\n')
        """.trimIndent()

        val expected = """
            apple
            cherry
            
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun nestedConstArray() {
        val prog = """
            extern fun print(i:String)
            extern fun print(i:Int)
            extern fun print(c:Char)
            
            fun main()
                val nested = const [
                    const [1, 2],
                    const [3, 4] ]
                for inner in nested
                    for x in inner
                        print(x)
                        print(' ')
                    print('\n')
        """.trimIndent()

        val expected = """
            1 2 
            3 4 

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun mixedConstAndNewArrays() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)

            fun main()
                val a = const [10, 20, 30]
                val b = new [40, 50, 60]
                val combined = new [a[1], b[2]]
                for x in combined
                    print(x)
                    print(' ')

        """.trimIndent()

        val expected = """
            20 60 
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun emptyConstArray() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)

            fun main()
                val empty = const Array<Int> []
                print("size: ")
                print(empty.length)   # assuming `.length` or equivalent property

        """.trimIndent()

        val expected = """
            size: 0
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun stringCompareIf() {
        val prog = """
            extern fun print(s:String)
            
            fun main()
                val fruits = const ["apple", "banana", "cherry"]
                if fruits[1] = "banana"
                    print("Yes\n")
                else
                    print("No\n")
        """.trimIndent()

        val expected = """
            Yes
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun stringCompareBasic() {
        val prog = """
            extern fun print(s:String)
    
            fun main()
                val s1 = "apple"
                val s2 = "banana"
                if s1 = "apple"
                    print("Eq1\n")
                if s2 != "apple"
                    print("Ne1\n")
        """.trimIndent()

        val expected = """
            Eq1
            Ne1
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun stringCompareOrdering() {
        val prog = """
            extern fun print(s:String)
    
            fun main()
                val a = "apple"
                val b = "banana"
                if a < b
                    print("a<b\n")
                if b > a
                    print("b>a\n")
                if a <= "apple"
                    print("a<=apple\n")
                if b >= "banana"
                    print("b>=banana\n")
        """.trimIndent()

        val expected = """
            a<b
            b>a
            a<=apple
            b>=banana
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun stringCompareInArrayLoop() {
        val prog = """
            extern fun print(s:String)
    
            fun main()
                val fruits = const ["apple", "banana", "cherry"]
                for f in fruits
                    if f = "banana"
                        print("Found banana\n")
                    else
                        print("Not banana\n")
        """.trimIndent()

        val expected = """
            Not banana
            Found banana
            Not banana
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nestedStringArrayCompare() {
        val prog = """
            extern fun print(s:String)
    
            fun main()
                val nested = const [
                    const ["a", "b"],
                    const ["c", "d"] ]
                for inner in nested
                    for s in inner
                        if s = "b"
                            print("b found\n")
                        else
                            print(s)
                            print(" ")
                    print("\n")
        """.trimIndent()

        val expected = """
            a b found
            
            c d 
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun stringLengthCompare() {
        val prog = """
            extern fun print(s:String)
            extern fun print(i:Int)
    
            fun main()
                val s1 = "abc"
                val s2 = "abcd"
                if s1 < s2
                    print("Shorter comes first\n")
                print("Length s1: ")
                print(s1.length)
                print("\n")
                print("Length s2: ")
                print(s2.length)
                print("\n")
        """.trimIndent()

        val expected = """
            Shorter comes first
            Length s1: 3
            Length s2: 4
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun stringCompareEmptyStrings() {
        val prog = """
            extern fun print(s:String)
            
            fun main()
                val s1 = ""
                val s2 = ""
                val s3 = "a"
                
                if s1 = s2
                    print("Empty equal\n")
                if s1 != s3
                    print("Empty not equal to non-empty\n")
        """.trimIndent()

        val expected = """
            Empty equal
            Empty not equal to non-empty
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun stringCompareSpecialChars() {
        val prog = """
            extern fun print(s:String)
            
            fun main()
                val s1 = "a\nb"
                val s2 = "a\nb"
                val s3 = "a\tb"
                
                if s1 = s2
                    print("Newline match\n")
                if s1 != s3
                    print("Different special char\n")
        """.trimIndent()

        val expected = """
            Newline match
            Different special char
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun stringCompareDifferentLengths() {
        val prog = """
            extern fun print(s:String)
            
            fun main()
                val s1 = "apple"
                val s2 = "applesauce"
                
                if s1 < s2
                    print("Shorter prefix comes first\n")
                if s2 > s1
                    print("Longer string is greater\n")
        """.trimIndent()

        val expected = """
            Shorter prefix comes first
            Longer string is greater
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun stringCompareArrayEdgeCases() {
        val prog = """
            extern fun print(s:String)
            
            fun main()
                val arr = const ["", "a", "ab", "abc", ""]
                for s in arr
                    if s = ""
                        print("Empty string\n")
                    else
                        print(s)
                        print("\n")
        """.trimIndent()

        val expected = """
            Empty string
            a
            ab
            abc
            Empty string
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun basicClass() {
        val prog = """
            extern fun print(s:String)
            extern fun print(i:Int)
            
            class Cat(val name:String, val age:Int)
            
            fun main()
                val kitty = new Cat("Whiskers", 3)
                print("Cat name: ")
                print(kitty.name)
                print("\n")
                print("Cat age: ")
                print(kitty.age)
                print("\n")
        """.trimIndent()

        val expected = """
            Cat name: Whiskers
            Cat age: 3

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun classWithMixedFields() {
        val prog = """
            extern fun print(s:String)
            extern fun print(i:Int)
            extern fun print(b:Bool)
    
            class Dog(val name:String, val age:Int, val vaccinated:Bool)
    
            fun main()
                val d = new Dog("Fido", 5, true)
                print(d.name)
                print("\n")
                print(d.age)
                print("\n")
                print(d.vaccinated)
                print("\n")
        """.trimIndent()

        val expected = """
            Fido
            5
            true
        
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun classWithNoFields() {
        val prog = """
        extern fun print(s:String)

        class Empty()

        fun main()
            val e = new Empty()
            print("Made an empty class\n")
    """.trimIndent()

        val expected = """
        Made an empty class
        
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun classWithBodyDeclaredFields() {
        val prog = """
        extern fun print(s:String)
        extern fun print(i:Int)

        class Point
            val x = 1
            val y = 2

        fun main()
            val p = new Point()
            print(p.x)
            print(",")
            print(p.y)
            print("\n")
    """.trimIndent()

        val expected = """
        1,2
        
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun fieldInitializerRefersToConstructorParam() {
        val prog = """
            extern fun print(s:String)
            extern fun print(i:Int)
    
            class Person(ageInYears:Int)
                val ageInMonths = ageInYears * 12
    
            fun main()
                val p = new Person(5)
                print(p.ageInMonths)
                print("\n")
        """.trimIndent()

        val expected = """
            60
        
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun multipleObjects() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
    
            class Counter(val start:Int)
                var value = start
    
            fun main()
                val c1 = new Counter(10)
                val c2 = new Counter(20)
                print(c1.value)
                print(" ")
                print(c2.value)
                print("\n")
        """.trimIndent()

        val expected = """
            10 20
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nullAssignmentAndCompare() {
        val prog = """
            extern fun print(s:String)
    
            class Cat(val name:String)
    
            fun main()
                val c:Cat? = null
                if c = null
                    print("Null OK\n")
        """.trimIndent()

        val expected = """
            Null OK
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun nullableAccess() {
        val prog = """
            extern fun print(s:String)
    
            class Cat(val name:String)
    
            fun main()
                val c:Cat? = new Cat("Whiskers")
                print(c??.name)
        """.trimIndent()

        val expected = """
            Whiskers
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun ifThen() {
        val prog = """
            extern fun print(s:String)
    
            fun main()
                for i in 0..3
                    if i < 2 then print("small\n")
                    else print("big\n")
        """.trimIndent()

        val expected = """
            small
            small
            big
            big

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun compoundAssign() {
        val prog = """
            extern fun print(s:String)
            extern  fun print(i:Int)
            extern  fun print(i:Char)
             
            fun main()
                var i = 0
                while i < 5
                    i += 1
                    print(i)
                    print('\n')
        """.trimIndent()

        val expected = """
            1
            2
            3
            4
            5

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun plusEqLocalVar() {
        val prog = """
            extern fun print(i:Int)

            fun main()
                var x = 5
                x += 3
                print(x)
        """.trimIndent()

        runTest(prog, "8")
    }

    @Test
    fun minusEqArrayElement() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            
            fun main()
                val arr = new Array<Int>(3)
                arr[0] = 10
                arr[1] = 20
                arr[2] = 30
                arr[1] -= 5
                for v in arr
                    print(v)
                    print(' ')
        """.trimIndent()

        runTest(prog, "10 15 30 ")
    }

    @Test
    fun plusEqClassField() {
        val prog = """
            extern fun print(i:Int)

            class Counter
                var value = 0

            fun main()
                val c = new Counter
                c.value += 42
                print(c.value)
        """.trimIndent()

        runTest(prog, "42")
    }

    @Test
    fun plusEqOnStringShouldFail() {
        val prog = """
            extern fun print(s:String)

            fun main()
                var s = "Hello"
                s += "World"
                print(s)
        """.trimIndent()

        val expected = """
            input.fpl:5.5-5.5:  Operator '+=' not defined for type 'String'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun constDecl() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)

            const TEN = 10

            fun main()
                var i = 0
                while i < TEN
                    print(i)
                    print('\n')
                    i = i + 1
        """.trimIndent()

        val expected = """
            0
            1
            2
            3
            4
            5
            6
            7
            8
            9

        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun abortTest() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)

            fun main()
                for i in 0..10
                    if i = 5
                        abort 0x1234
                    print(i)
                    print('\n')
        """.trimIndent()

        val expected = """
            0
            1
            2
            3
            4
            7-Segment = 001234
            Abort called with code 00001234 at FFFF036C
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun globalVars() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)

            var g1 = 0 
            var g2 = "Finished"
            
            fun printAndInc()
                print(g1)
                print('\n')
                g1 += 1

            fun main()
                while g1 < 10
                    printAndInc()
                print(g2)
        """.trimIndent()

        val expected = """
            0
            1
            2
            3
            4
            5
            6
            7
            8
            9
            Finished
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun whenTest() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)

            fun printValue(i:Int)
                when i
                    0 -> print("zero\n")
                    1 -> print("one\n")
                    2,3,4 -> print("a few\n")
                    else -> print("many\n")
            
            fun main()
                for x in 0..6
                    printValue(x)
                print("Finished\n")
        """.trimIndent()

        val expected = """
            zero
            one
            a few
            a few
            a few
            many
            many
            Finished
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun whenBadElse() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)

            fun printValue(i:Int)
                when i
                    0 -> print("zero\n")
                    "1" -> print("one\n")
                    else -> print("many\n")
                    2,3,4 -> print("a few\n")
            
            fun main()
                for x in 0..6
                    printValue(x)
                print("Finished\n")
        """.trimIndent()

        val expected = """
            input.fpl:8.9-8.11:  Type mismatch got 'String' when expecting 'Int'
            input.fpl:10.9-10.9:  The else case must be the last case in a when statement
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun functionPointerTest() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)

            fun doSomething()
                print("Hello")
            
            fun main()
                val x = doSomething
                x()
        """.trimIndent()

        val expected = """
            Hello
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun functionPointerTestWithArgs() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)

            fun doSomething(a:Int)
                print("Hello ")
                print(a)
                print("\n")
            
            fun main()
                val x = doSomething
                x(4)
        """.trimIndent()

        val expected = """
            Hello 4
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun functionPointerTestWithBadArgs() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)

            fun doSomething(a:Int)
                print("Hello ")
                print(a)
                print("\n")
            
            fun main()
                val x = doSomething
                x()
        """.trimIndent()

        val expected = """
            input.fpl:12.6-12.6:  Got 0 arguments when expecting 1
        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun ifExpression() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)

            fun doSomething(a:Int)
                print(if a > 0 then "Positive" else "Non-positive")
                print("\n")
            
            fun main()
                for i in -1..1
                    doSomething(i)
        """.trimIndent()

        val expected = """
            Non-positive
            Non-positive
            Positive

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun ifExpression1() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)

            fun doSomething(a:Int)
                print(if a > 0 then "Positive" else 2)
                print("\n")
            
            fun main()
                for i in -1..1
                    doSomething(i)
        """.trimIndent()

        val expected = """
            input.fpl:6.16-6.16:  Types 'String' and 'Int' have no common ancestor type
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun ifExpression2() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)

            class Animal
            class Dog(val name:String) : Animal

            fun doSomething(a:Animal) 
                val x = if a is Dog then a else return 
                print(x.name)       # Should be Dog here
            
            fun main()
                val d = new Dog("Fido")
                doSomething(d)
        """.trimIndent()

        val expected = """
            Fido
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun ifExpression3() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)

            class Animal
            class Dog(val name:String) : Animal
            class Cat(val name:String) : Animal

            fun doSomething(a:Int) -> Dog 
                val x = if a=1 then new Dog("Fido") else new Cat("Whiskers")
                return x        # Error as x should be Animal
            
            fun main()
                doSomething(0)
        """.trimIndent()

        val expected = """
            input.fpl:11.12-11.12:  Type mismatch got 'Animal' when expecting 'Dog'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun pointerTest() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(c:Char)
            extern fun print(s:String)

            fun doSomething(a:Pointer<Int>) 
                for i in 0..4
                    print(a[i])
                    print(' ')
            
            fun main()
                val c = new Array<Int>(5){it*10}
                doSomething(c)
        """.trimIndent()

        val expected = """
            0 10 20 30 40 
        """.trimIndent()

        runTest(prog, expected)
    }




}

