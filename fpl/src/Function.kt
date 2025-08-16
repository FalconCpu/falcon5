val allFunctions = mutableListOf<Function>()

class Function (
    val location: Location,
    val name: String,
    val parameters: List<VarSymbol>,
    val returnType : Type,
    val isExtern: Boolean
) {
    val prog = mutableListOf<Instr>()
    val regs = cpuRegs.toMutableList<Reg>()      // Include all the CPU registers
    val labels = mutableListOf<Label>()
    val symToReg = mutableMapOf<VarSymbol, Reg>()
    val regAllocComments = mutableListOf<String>()
    var maxRegister = 0
    var stackVarSize = 0

    val endLabel = newLabel()

    init {
        // Don't add extern functions to allFunctions list - as we don't want to generate code for them
        if (!isExtern)
            allFunctions += this
    }

    override fun toString() = name

    private var tempCount = 0
    fun newTemp() : TempReg {
        val ret = TempReg("t${tempCount++}")
        regs += ret
        return ret
    }

    fun newLabel() : Label {
        val ret = Label("L${labels.size}")
        labels += ret
        return ret
    }

    fun getReg(sym: VarSymbol): Reg {
        return symToReg.getOrPut(sym) {
            val ret = UserReg(sym.name)
            regs += ret
            ret
        }
    }

    fun addInstr(instr: Instr) {
        prog.add(instr)
    }

    fun addAlu(op: BinOp, src1: Reg, src2: Reg): TempReg {
        val dest = newTemp()
        prog += InstrAlu(op, dest, src1, src2)
        return dest
    }

    fun addAlu(op: BinOp, src1: Reg, lit: Int): TempReg {
        val dest = newTemp()
        prog += InstrAluLit(op, dest, src1, lit)
        return dest
    }

    fun addMov(dest: Reg, src: Reg) {
        prog += InstrMov(dest, src)
    }

    fun addLdImm(value:Int): TempReg {
        val dest = newTemp()
        prog += InstrMovLit(dest, value)
        return dest
    }


    fun addCopy(src: Reg) : Reg{
        val dest = newTemp()
        prog += InstrMov(dest, src)
        return dest
    }

    fun addLea(src:Value) : Reg {
        val dest = newTemp()
        prog += InstrLea(dest, src)
        return dest
    }

    fun addJump(label: Label) {
        prog += InstrJump(label)
    }

    fun addLabel(label: Label) {
        prog += InstrLabel(label)
    }

    fun addCall(func: Function) {
        prog += InstrCall(func)
    }

    fun addBranch(op: BinOp, src1: Reg, src2: Reg, label: Label) {
        assert(op in listOf(BinOp.EQ_I, BinOp.NE_I, BinOp.LT_I, BinOp.LE_I, BinOp.GT_I, BinOp.GE_I))
        prog += InstrBranch(op, src1, src2, label)
    }

    fun dump(sb:StringBuilder) {
        sb.append("Function $name:\n")
        for (instr in prog)
            sb.append(instr.toString()).append("\n")
    }
}


fun Function.hasSameSignature(other: Function): Boolean {
    if (parameters.size!= other.parameters.size) return false
    for (i in parameters.indices)
        if (parameters[i].type!= other.parameters[i].type) return false
    return true
}

fun dumpAllFunctions() : String {
    val sb = StringBuilder()
    allFunctions.forEach { it.dump(sb) }
    return sb.toString()
}