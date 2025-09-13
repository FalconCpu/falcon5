import java.io.FileReader
import java.io.FileWriter

val debug = false


//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: program [options] <file1> <file2> ...")
        println("Options: --stop-at=[PARSE|TYPE_CHECK|CODE_GEN|BACKEND|ASSEMBLY|EXECUTE]")
        return
    }

    var stopAt = StopAt.BINARY
    val filenames = mutableListOf<String>()

    for (arg in args) {
        when {
            arg.startsWith("--stop-at=") -> {
                val value = arg.substringAfter("=")
                stopAt = try {
                    StopAt.valueOf(value)
                } catch (e: IllegalArgumentException) {
                    println("Invalid stop-at value: $value")
                    return
                }
            }

            arg.endsWith(".fplprj") -> {
                try {
                    val prjFiles = java.io.File(arg).readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                    filenames.addAll(prjFiles)
                } catch (e: Exception) {
                    Log.error(nullLocation, "Failed to read project file: ${e.message}")
                    return
                }
            }

            else -> filenames.add(arg)
        }
    }

    if (filenames.isEmpty()) {
        println("No input files specified.")
        return
    }

    val lexers = try {filenames.filter{it.endsWith(".fpl") }.map { Lexer(FileReader(it), it) }}
    catch (e:Exception) {
        Log.error(nullLocation, "Failed to open input file: ${e.message}")
        return
    }

    val asmFileArg = filenames.filter { it.endsWith(".f32") }
    if (asmFileArg.isNotEmpty())
        assemblyFiles = asmFileArg

    val result = compile(lexers, stopAt)
    println(result)
}


enum class StopAt{PARSE, TYPE_CHECK, CODE_GEN, BACKEND, ASSEMBLY, BINARY, EXECUTE}

var assemblyFiles = listOf("stdlib/start.f32")
private var asmFileName = "asm.f32"
private var executableFileName = "asm.hex"


private fun runAssembler(filenames:List<String>, format:String) {
    val process = ProcessBuilder("f32asm.exe", format,"-o",executableFileName,  *filenames.toTypedArray())
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    val exitCode = process.waitFor()
    if (exitCode != 0)
        Log.error(nullLocation, process.inputStream.bufferedReader().readText())
}

private fun runProgram() : String {
    val process = ProcessBuilder("f32sim", "-t", "-a" , "asm.hex")
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    val exitCode = process.waitFor()
    if (exitCode != 0)
        Log.error(nullLocation, "Execution failed with exit code $exitCode")
    return process.inputStream.bufferedReader().readText().replace("\r\n", "\n")
}


fun compile(lexers:List<Lexer>, stopAt: StopAt) : String {
    Log.clear()
    allFunctions.clear()
    allSymbols.clear()
    allGlobals.clear()
    Value.clear()
    errorEnum = null
    allAccessSymbols.clear()

    // Run the parsing
    val astTop = Parser.parseAll(lexers)
    if (Log.hasErrors()) return Log.getMessages()
    if (stopAt == StopAt.PARSE) return astTop.dump()

    val tctTop = astTop.typeCheck()
    if (Log.hasErrors()) return Log.getMessages()
    if (stopAt == StopAt.TYPE_CHECK) return tctTop.dump()
    astTop.writeSymbolMap()

    // Run the code generation
    tctTop.codeGen()
    if (Log.hasErrors()) return Log.getMessages()
    if (stopAt == StopAt.CODE_GEN) return dumpAllFunctions()

    // Run the backend
    runBackend()
    if (Log.hasErrors()) return Log.getMessages()
    if (stopAt == StopAt.BACKEND) return dumpAllFunctions()

    // Generate assembly
    val asm = genAssembly()
    if (Log.hasErrors()) return Log.getMessages()
    if (stopAt == StopAt.ASSEMBLY) return asm

    // Save the assembly code to a file
    val asmFile = FileWriter(asmFileName)
    asmFile.write(asm)
    asmFile.close()

    runAssembler(assemblyFiles + listOf(asmFileName), "-hex")
    if (Log.hasErrors()) return Log.getMessages()
    if (stopAt == StopAt.BINARY) return "Generated binary file: $executableFileName"

    return runProgram()
}