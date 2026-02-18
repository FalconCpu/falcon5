import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.StringReader

// Tests for Long type (64-bit integers)

class LongTests {

    val printHexFunc = """
            
            extern fun print(a:Char)
            extern fun printHex(a:Int)
            fun printHex(a:Long)
                var v = a
                for i in 0..15
                    val digit = ((v lsr 60) & 0xFL) as Int
                    val hexChar = if digit<10 then ('0' + digit) else ('A' + digit - 10)
                    print(hexChar as Char)
                    v = v lsl 4
                print('\n')
            end fun
    """.trimIndent()

    fun runTest(prog: String, expected: String) {
        val lexers = listOf(Lexer(StringReader(prog + printHexFunc), "input.fpl"))
        val output = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, output)
    }

    @Test
    fun printHex() {
        // Test printing a long literal in hexadecimal
        val prog = """         
            fun main()
                printHex(0x123456789ABCDEFL)
        """.trimIndent()

        val expected = """
            0123456789ABCDEF
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun longVariables() {
        // Test declaring and using Long variables
        val prog = """
            fun main()
                val a:Long = 0x1234567890ABCDEFL
                var b:Long = 0xFEDCBA0987654321L
                printHex(a)
                printHex(b)
        """.trimIndent()

        val expected = """
            1234567890ABCDEF
            FEDCBA0987654321
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun longShiftLeft() {
        // Test left shift operator (lsl)
        val prog = """
            fun main()
                val a:Long = 0x0000000000000001L
                printHex(a lsl 4)
                printHex(a lsl 8)
                printHex(a lsl 32)
                printHex(a lsl 60)
        """.trimIndent()

        val expected = """
            0000000000000010
            0000000000000100
            0000000100000000
            1000000000000000
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun longShiftRightLogical() {
        // Test logical right shift operator (lsr)
        val prog = """
            fun main()
                val a:Long = 0x8000000000000000L
                printHex(a lsr 4)
                printHex(a lsr 8)
                printHex(a lsr 32)
                printHex(a lsr 60)
        """.trimIndent()

        val expected = """
            0800000000000000
            0080000000000000
            0000000080000000
            0000000000000008
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun longShiftRightArithmetic() {
        // Test arithmetic right shift operator (asr)
        val prog = """
            fun main()
                val a:Long = 0x8000000000000000L
                printHex(a asr 4)
                printHex(a asr 8)
                printHex(a asr 32)
                printHex(a asr 60)
        """.trimIndent()

        val expected = """
            F800000000000000
            FF80000000000000
            FFFFFFFF80000000
            FFFFFFFFFFFFFFF8
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun longShiftPositiveNumber() {
        // Test arithmetic right shift on positive number (should behave like logical shift)
        val prog = """
            fun main()
                val a:Long = 0x7FFFFFFFFFFFFFFFL
                printHex(a asr 4)
                printHex(a asr 32)
        """.trimIndent()

        val expected = """
            07FFFFFFFFFFFFFF
            000000007FFFFFFF
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun longAddition() {
        // Test addition of two Longs
        val prog = """
            fun main()
                val a:Long = 0x0000000000000001L
                val b:Long = 0x0000000000000002L
                printHex(a + b)
                
                val c:Long = 0x1111111111111111L
                val d:Long = 0x2222222222222222L
                printHex(c + d)
                
                val e:Long = 0xFFFFFFFFFFFFFFFFL
                val f:Long = 0x0000000000000001L
                printHex(e + f)
        """.trimIndent()

        val expected = """
            0000000000000003
            3333333333333333
            0000000000000000
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun longAdditionCarry() {
        // Test addition with carries between 32-bit words
        val prog = """
            fun main()
                val a:Long = 0x00000000FFFFFFFFL
                val b:Long = 0x0000000000000001L
                printHex(a + b)
                
                val c:Long = 0x0000000100000000L
                val d:Long = 0x00000000FFFFFFFFL
                printHex(c + d)
        """.trimIndent()

        val expected = """
            0000000100000000
            00000001FFFFFFFF
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun longBitwiseAnd() {
        // Test bitwise AND operator
        val prog = """
            fun main()
                val a:Long = 0xFFFFFFFFFFFFFFFFl
                val b:Long = 0x0F0F0F0F0F0F0F0FL
                printHex(a & b)
                
                val c:Long = 0xAAAAAAAAAAAAAAAAL
                val d:Long = 0x5555555555555555L
                printHex(c & d)
        """.trimIndent()

        val expected = """
            0F0F0F0F0F0F0F0F
            0000000000000000
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun longBitwiseOr() {
        // Test bitwise OR operator
        val prog = """
            fun main()
                val a:Long = 0xF0F0F0F0F0F0F0F0L
                val b:Long = 0x0F0F0F0F0F0F0F0FL
                printHex(a | b)
                
                val c:Long = 0xAAAAAAAAAAAAAAAAL
                val d:Long = 0x5555555555555555L
                printHex(c | d)
        """.trimIndent()

        val expected = """
            FFFFFFFFFFFFFFFF
            FFFFFFFFFFFFFFFF
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun longBitwiseXor() {
        // Test bitwise XOR operator
        val prog = """
            fun main()
                val a:Long = 0xFFFFFFFFFFFFFFFFl
                val b:Long = 0x0F0F0F0F0F0F0F0FL
                printHex(a ^ b)
                
                val c:Long = 0xAAAAAAAAAAAAAAAAL
                val d:Long = 0x5555555555555555L
                printHex(c ^ d)
                
                val e:Long = 0x123456789ABCDEFL
                printHex(e ^ e)
        """.trimIndent()

        val expected = """
            F0F0F0F0F0F0F0F0
            FFFFFFFFFFFFFFFF
            0000000000000000
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun longEquality() {
        // Test equality comparison
        val prog = """
            fun main()
                val a:Long = 0x123456789ABCDEFL
                val b:Long = 0x123456789ABCDEFL
                val c:Long = 0xFEDCBA0987654321L
                
                print(if a = b then 'Y' else 'N')
                print(if a = c then 'Y' else 'N')
                print(if b = c then 'Y' else 'N')
        """.trimIndent()

        val expected = """
            YNN
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun longInequality() {
        // Test inequality comparison
        val prog = """
            fun main()
                val a:Long = 0x123456789ABCDEFL
                val b:Long = 0x123456789ABCDEFL
                val c:Long = 0xFEDCBA0987654321L
                
                print(if a != b then 'Y' else 'N')
                print(if a != c then 'Y' else 'N')
                print(if b != c then 'Y' else 'N')
        """.trimIndent()

        val expected = """
            NYY
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun castIntToLong() {
        // Test casting Int to Long
        val prog = """
            fun main()
                val a:Int = 0x12345678
                val b:Long = a as Long
                printHex(b)
                
                val c:Int = -1
                val d:Long = c as Long
                printHex(d)
        """.trimIndent()

        val expected = """
            0000000012345678
            FFFFFFFFFFFFFFFF
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun castLongToInt() {
        // Test casting Long to Int (truncation)
        val prog = """
            fun main()
                val a:Long = 0x123456789ABCDEFL
                val b:Int = a as Int
                printHex(b)
                print('\n')
                
                val c:Long = 0xFFFFFFFF00000000L
                val d:Int = c as Int
                printHex(d)
        """.trimIndent()

        val expected = """
            89ABCDEF
            00000000
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun longComplexExpression() {
        // Test combining multiple operations
        val prog = """
            fun main()
                val a:Long = 0x00000000FFFFFFFFL
                val b:Long = 0xFFFFFFFF00000000L
                val c:Long = ((a lsl 32) | (b lsr 32)) & 0xFFFF0000FFFF0000L
                printHex(c)
                
                val x:Long = 0x1234567890ABCDEFL
                val y:Long = 0x0F0F0F0F0F0F0F0FL
                val z:Long = (x & y) + ((x | y) lsr 4)
                printHex(z)
        """.trimIndent()

        val expected = """
            FFFF0000FFFF0000
            03F7FBFFFA060A0D
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun longWithVariableReassignment() {
        // Test mutable Long variables
        val prog = """
            fun main()
                var a:Long = 0x0000000000000001L
                printHex(a)
                a = a lsl 8
                printHex(a)
                a = a + 0x0000000000000002L
                printHex(a)
                a = a & 0xFFFFFFFFFFFFFF00L
                printHex(a)
        """.trimIndent()

        val expected = """
            0000000000000001
            0000000000000100
            0000000000000102
            0000000000000100
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun longZeroAndMaxValues() {
        // Test edge case values
        val prog = """
            fun main()
                val zero:Long = 0x0000000000000000L
                val max:Long = 0xFFFFFFFFFFFFFFFFL
                printHex(zero)
                printHex(max)
                printHex(zero + max)
                printHex(max & zero)
                printHex(max | zero)
        """.trimIndent()

        val expected = """
            0000000000000000
            FFFFFFFFFFFFFFFF
            FFFFFFFFFFFFFFFF
            0000000000000000
            FFFFFFFFFFFFFFFF
            
        """.trimIndent()

        runTest(prog, expected)
    }

}