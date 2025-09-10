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
            END
            Function add(Int,Int):
            START
            MOV x, R1
            MOV y, R2
            ADD_I t0, x, y
            MOV R8, t0
            JMP L0
            L0:
            END
            Function main():
            START
            MOV t0, 1
            MOV t1, 2
            MOV R1, t0
            MOV R2, t1
            CALL add(Int,Int)
            MOV t2, R8
            MOV z, t2
            L0:
            END

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
            END
            Function main():
            START
            LEA t0, STRING0
            MOV R1, t0
            CALL print(String)
            L0:
            END

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
            END
            Function main():
            START
            MOV t0, 0
            MOV i, t0
            JMP L3
            L1:
            MOV R1, i
            CALL print(Int)
            MOV t1, 1
            ADD_I t2, i, t1
            MOV i, t2
            L3:
            MOV t3, 10
            BLT i, t3, L1
            JMP L2
            L2:
            L0:
            END

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
            END
            Function sum(Array<Int>):
            START
            MOV a, R1
            MOV t0, 0
            MOV total, t0
            MOV t1, 0
            MOV index, t1
            JMP L3
            L1:
            LD4 t2, a[length]
            IDX4 t3, index, t2
            ADD_I t4, a, t3
            LD4 t5, t4[0]
            ADD_I t6, total, t5
            MOV total, t6
            MOV t7, 1
            ADD_I t8, index, t7
            MOV index, t8
            L3:
            LD4 t9, a[length]
            BLT index, t9, L1
            JMP L2
            L2:
            MOV R8, total
            JMP L0
            L0:
            END
            Function main():
            START
            MOV t0, 5
            MOV R1, t0
            MOV R2, 4
            CALL mallocArray(Int,Int)
            MOV t1, R8
            MUL_I t2, t0, 4
            MOV R1, t1
            MOV R2, t2
            CALL bzero
            MOV arr, t1
            MOV t3, 1
            MOV t4, 0
            LD4 t5, arr[length]
            IDX4 t6, t4, t5
            ADD_I t7, arr, t6
            ST4 t3, t7[0]
            MOV t8, 2
            MOV t9, 1
            LD4 t10, arr[length]
            IDX4 t11, t9, t10
            ADD_I t12, arr, t11
            ST4 t8, t12[0]
            MOV t13, 3
            MOV t14, 2
            LD4 t15, arr[length]
            IDX4 t16, t14, t15
            ADD_I t17, arr, t16
            ST4 t13, t17[0]
            MOV t18, 4
            MOV t19, 3
            LD4 t20, arr[length]
            IDX4 t21, t19, t20
            ADD_I t22, arr, t21
            ST4 t18, t22[0]
            MOV t23, 5
            MOV t24, 4
            LD4 t25, arr[length]
            IDX4 t26, t24, t25
            ADD_I t27, arr, t26
            ST4 t23, t27[0]
            MOV R1, arr
            CALL sum(Array<Int>)
            MOV t28, R8
            MOV result, t28
            MOV R1, result
            CALL print(Int)
            L0:
            END

        """.trimIndent()

        runTest(prog, expected)
    }


}