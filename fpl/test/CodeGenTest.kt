import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class CodeGenTest {
    fun runTest(prog:String, expected:String) {
        val lexers = listOf(Lexer(StringReader(prog),"input.fpl"))
        val output = compile(lexers, StopAt.CODE_GEN)
        assertEquals(expected, output)
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
            Function topLevel:
            END 0
            Function add(Int,Int):
            START
            ADD_I t1, x, y
            MOV u0, t1
            JMP L0
            L0:
            END u0
            Function main():
            START
            MOV t1, 1
            MOV t2, 2
            CALL add(Int,Int), t3, [t1, t2]
            MOV z, t3
            L0:
            END u0

        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun helloWorld() {
        val prog = """
            extern fun print(s:String)

            fun main()
                print("Hello, World!")
        """.trimIndent()

        val expected = """
            Function topLevel:
            END 0
            Function main():
            START
            LEA t1, STRING0
            CALL print(String), t2, [t1]
            L0:
            END u0

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun whileLoop() {
        val prog = """
            extern fun print(i:Int)

            fun main()
                var i = 0
                while (i < 10)
                    print(i)
                    i = i + 1
        """.trimIndent()

        val expected = """
            Function topLevel:
            END 0
            Function main():
            START
            MOV t1, 0
            MOV i, t1
            JMP L4
            L2:
            CALL print(Int), t2, [i]
            MOV t3, 1
            ADD_I t4, i, t3
            MOV i, t4
            L4:
            MOV t5, 10
            BLT i, t5, L2
            JMP L3
            L3:
            L0:
            END u0

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
            Function topLevel:
            END 0
            Function sum(Array<Int>):
            START
            MOV t1, 0
            MOV total, t1
            MOV t2, 0
            MOV index, t2
            JMP L4
            L2:
            LD4 t3, a[length]
            IDX4 t4, index, t3
            ADD_I t5, a, t4
            LD4 t6, t5[0]
            ADD_I t7, total, t6
            MOV total, t7
            MOV t8, 1
            ADD_I t9, index, t8
            MOV index, t9
            L4:
            LD4 t10, a[length]
            BLT index, t10, L2
            JMP L3
            L3:
            MOV u0, total
            JMP L0
            L0:
            END u0
            Function main():
            START
            MOV t1, 5
            MOV t2, 4
            CALL mallocArray(Int,Int), t3, [t1, t2]
            MUL_I t4, t1, 4
            CALL bzero, t5, [t3, t4]
            MOV arr, t3
            MOV t6, 1
            MOV t7, 0
            LD4 t8, arr[length]
            IDX4 t9, t7, t8
            ADD_I t10, arr, t9
            ST4 t6, t10[0]
            MOV t11, 2
            MOV t12, 1
            LD4 t13, arr[length]
            IDX4 t14, t12, t13
            ADD_I t15, arr, t14
            ST4 t11, t15[0]
            MOV t16, 3
            MOV t17, 2
            LD4 t18, arr[length]
            IDX4 t19, t17, t18
            ADD_I t20, arr, t19
            ST4 t16, t20[0]
            MOV t21, 4
            MOV t22, 3
            LD4 t23, arr[length]
            IDX4 t24, t22, t23
            ADD_I t25, arr, t24
            ST4 t21, t25[0]
            MOV t26, 5
            MOV t27, 4
            LD4 t28, arr[length]
            IDX4 t29, t27, t28
            ADD_I t30, arr, t29
            ST4 t26, t30[0]
            CALL sum(Array<Int>), t31, [arr]
            MOV result, t31
            CALL print(Int), t32, [result]
            L0:
            END u0

        """.trimIndent()

        runTest(prog, expected)
    }


}