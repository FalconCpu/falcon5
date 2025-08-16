import java.io.FileWriter

val debug = false

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    val name = "Kotlin"
    //TIP Press <shortcut actionId="ShowIntentionActions"/> with your caret at the highlighted text
    // to see how IntelliJ IDEA suggests fixing it.
    println("Hello, " + name + "!")

    for (i in 1..5) {
        //TIP Press <shortcut actionId="Debug"/> to start debugging your code. We have set one <icon src="AllIcons.Debugger.Db_set_breakpoint"/> breakpoint
        // for you, but you can always add more by pressing <shortcut actionId="ToggleLineBreakpoint"/>.
        println("i = $i")
    }
}


enum class StopAt{PARSE, TYPE_CHECK, CODE_GEN, BACKEND, ASSEMBLY, EXECUTE}

var assemblyFiles = listOf("stdlib/start.f32")
private var asmFileName = "asm.f32"
private var executableFileName = "asm.hex"


private fun runAssembler(filenames:List<String>, format:String) {
    val process = ProcessBuilder("f32asm.exe", format,"-o",executableFileName,  *filenames.toTypedArray())
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    val exitCode = process.waitFor()
    if (exitCode != 0)
        Log.error(process.inputStream.bufferedReader().readText())
}

private fun runProgram() : String {
    val process = ProcessBuilder("f32sim", "-t", "-a" , "asm.hex")
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    val exitCode = process.waitFor()
    if (exitCode != 0)
        Log.error("Execution failed with exit code $exitCode")
    return process.inputStream.bufferedReader().readText().replace("\r\n", "\n")
}


fun compile(lexers:List<Lexer>, stopAt: StopAt) : String {
    Log.clear()
    allFunctions.clear()
    StringValue.clear()

    // Run the parsing
    val astTop = Parser.parseAll(lexers)
    if (Log.hasErrors()) return Log.getMessages()
    if (stopAt == StopAt.PARSE) return astTop.dump()

    val tctTop = astTop.typeCheck()
    if (Log.hasErrors()) return Log.getMessages()
    if (stopAt == StopAt.TYPE_CHECK) return tctTop.dump()

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
    return runProgram()

}