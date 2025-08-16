
private fun Function.rebuildIndex() {
    for((index,instr) in prog.withIndex()) {
        instr.index = index
        if (instr is InstrLabel)
            instr.label.index = index
    }

    for((index,reg) in regs.withIndex())
        reg.index = index
}


fun Function.runBackend() {
    rebuildIndex()

}


fun runBackend() {
    for(func in allFunctions)
        func.runBackend()
}