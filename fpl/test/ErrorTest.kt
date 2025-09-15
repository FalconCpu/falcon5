import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class ErrorTests {
    fun runTest(prog: String, expected: String) {
        val lexers = listOf(Lexer(StringReader(prog), "input.fpl"))
        val output = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, output)
    }

    @Test
    fun basicError() {
        val prog = """
            extern fun print(i:Int)
            extern fun print(s:String)
            
            enum Error(desc:String) [
                OUT_OF_MEMORY("Out of memory"),
                IO_ERROR("I/O error"),
                INVALID_ARGUMENT("Invalid argument"),
                FILE_NOT_FOUND("File not found") ]

            fun openFile(name:String) -> Int!
                if (name = "missing.txt")
                    return Error.FILE_NOT_FOUND
                else
                    return 42
           
            fun main()
                val handle1 = openFile("missing.txt")
                if (handle1 is Error)
                    print("Error opening file: ")
                    print(handle1.desc)
                else
                    print("File opened successfully with handle =")
                    print(handle1)
                print("\n")
                val handle2 = openFile("data.txt")
                if (handle2 is Error)
                    print("Error opening file: ")
                    print(handle2.desc)
                else
                    print("File opened successfully with handle =")
                    print(handle2)
                    
                    
        """.trimIndent()

        val expected = """
            Error opening file: File not found
            File opened successfully with handle =42
        """.trimIndent()

        runTest(prog, expected)
    }


    @Test
    fun multipleErrors() {
        val prog = """
        extern fun print(s:String)
        extern fun print(i:Int)
        
        enum Error(desc:String) [
            FILE_NOT_FOUND("File not found"),
            PERMISSION_DENIED("Permission denied") ]
        
        fun openFile(name:String, user:String) -> Int!
            if (name="secret.txt")
                return Error.PERMISSION_DENIED
            elsif (name="missing.txt")
                return Error.FILE_NOT_FOUND
            else
                return 42
        
        fun main()
            val h1 = openFile("missing.txt", "alice")
            if (h1 is Error)
                print("h1 error: ")
                print(h1.desc)
            else
                print("h1 ok: ")
                print(h1)
                
            print("\n")
            
            val h2 = openFile("secret.txt", "bob")
            if (h2 is Error)
                print("h2 error: ")
                print(h2.desc)
            else
                print("h2 ok: ")
                print(h2)
                
            print("\n")
            
            val h3 = openFile("data.txt", "carol")
            if (h3 is Error)
                print("h3 error: ")
                print(h3.desc)
            else
                print("h3 ok: ")
                print(h3)
    """.trimIndent()

        val expected = """
        h1 error: File not found
        h2 error: Permission denied
        h3 ok: 42
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun assignErrable() {
        val prog = """
        extern fun print(s:String)
        extern fun print(i:Int)
        
        enum Error(desc:String) [
            OUT_OF_MEMORY("Out of memory") ]
        
        fun allocate(n:Int) -> Int!
            if (n>100)
                return Error.OUT_OF_MEMORY
            else
                return n*2
        
        fun main()
            val a = allocate(50)
            val b = a
            if (b is Error)
                print("Error: ")
                print(b.desc)
            else
                print("Success: ")
                print(b)
            
            print("\n")
            
            val c = allocate(150)
            val d = c
            if (d is Error)
                print("Error: ")
                print(d.desc)
            else
                print("Success: ")
                print(d)
    """.trimIndent()

        val expected = """
        Success: 100
        Error: Out of memory
    """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun payloadAccessAfterBranch() {
        val prog = """
        extern fun print(s:String)
        
        enum Error(desc:String) [
            FILE_NOT_FOUND("File not found") ]
        
        fun open() -> Int!
            return Error.FILE_NOT_FOUND
        
        fun main()
            val r = open()
            if (r is Error)
                val msg = r.desc
                print(msg)
            else
                print("OK")
    """.trimIndent()

        val expected = "File not found"

        runTest(prog, expected)
    }

    @Test
    fun attemptAccessError() {
        val prog = """
        extern fun print(s:String)
        extern fun print(i:Int)
        
        enum Error(desc:String) [
            FILE_NOT_FOUND("File not found") ]
        
        fun open() -> Int!
            return Error.FILE_NOT_FOUND
        
        fun main()
            val r = open()
            print(r)
    """.trimIndent()

        val expected = """
            input.fpl:12.10-12.10:  No function found for print(Int!) candidates are:-
            print(String)
            print(Int)
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun tryTest() {
        val prog = """
        extern fun print(s:String)
        extern fun print(i:Int)
        
        enum Error(desc:String) [
            FILE_NOT_FOUND("File not found") ]
        
        fun open(filename:String) -> Int!
            if (filename="data.txt")
                return 42
            else
                return Error.FILE_NOT_FOUND
        
        fun processFile(filename:String) -> Int!
            val handle = try open(filename)
            print("File opened with handle = ")
            print(handle)
            print("\n")
            return handle * 2
        
        fun main()
            if (processFile("missing.txt") is Error)
                print("Failed to process missing.txt\n")
            else
                print("Processed missing.txt successfully\n")
                
            if (processFile("data.txt") is Error)
                print("Failed to process data.txt")
            else
                print("Processed data.txt successfully")
    """.trimIndent()

        val expected = """
            Failed to process missing.txt
            File opened with handle = 42
            Processed data.txt successfully
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun tryExpressionTests() {
        val prog = """
        extern fun print(i:Int)
        extern fun print(s:String)
        
        enum Error(desc:String) [
            FILE_NOT_FOUND("File not found"),
            OUT_OF_MEMORY("Out of memory") ]
        
        fun openFile(name:String) -> Int!
            if (name = "missing.txt")
                return Error.FILE_NOT_FOUND
            elsif (name = "bigfile.txt")
                return Error.OUT_OF_MEMORY
            else
                return 42
        
        fun main() -> Int!
            # Success path
            val h1 = try openFile("data.txt")
            print("Opened handle: ")
            print(h1)
            print("\n")
            
            # Error propagation
            val h2 = try openFile("missing.txt")
            print("This should not print: ")
            print(h2)
            print("\n")
            
            # Multiple try expressions
            val a = try openFile("data.txt")
            val b = try openFile("bigfile.txt")
            print("Sum handles: ")
            print(a + b)
            return 0
        """.trimIndent()

        val expected = """
            Opened handle: 42
            
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun castTest() {
        val prog = """
        extern fun print(i:Int)
        extern fun print(s:String)
        
        enum Error(desc:String) [
            FILE_NOT_FOUND("File not found"),
            OUT_OF_MEMORY("Out of memory") ]
        
        fun openFile(name:String) -> Int!
            if (name = "missing.txt")
                return Error.FILE_NOT_FOUND
            elsif (name = "bigfile.txt")
                return Error.OUT_OF_MEMORY
            else
                return 42
        
        fun main()
            # Success path
            val h1 = openFile("data.txt")
            print("Opened handle: ")
            print(h1 as Int)
            print("\n")
        """.trimIndent()

        val expected = """
            input.fpl:20.14-20.15:  Cannot cast type 'Int!' to 'Int'
        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun unsafeCastTest() {
        val prog = """
        extern fun print(i:Int)
        extern fun print(s:String)
        
        enum Error(desc:String) [
            FILE_NOT_FOUND("File not found"),
            OUT_OF_MEMORY("Out of memory") ]
        
        fun openFile(name:String) -> Int!
            if (name = "missing.txt")
                return Error.FILE_NOT_FOUND
            elsif (name = "bigfile.txt")
                return Error.OUT_OF_MEMORY
            else
                return 42
        
        fun main()
            # Success path
            val h1 = openFile("data.txt")
            print("Opened handle: ")
            print(unsafe(h1 as Int))
            print("\n")
        """.trimIndent()

        val expected = """
            Opened handle: 42
            
        """.trimIndent()

        runTest(prog, expected)
    }





}
