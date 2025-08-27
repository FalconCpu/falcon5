val allFunctions = mutableListOf<Function>()

class Function (
    val location: Location,
    val name: String,
    val thisSymbol : VarSymbol?,      // This is null for static functions
    val parameters: List<VarSymbol>,
    val returnType : Type,
    val qualifier: TokenKind
) {
    var virtualFunctionNumber = -1      // Virtual function number, -1 if not a virtual function

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
        if (qualifier != TokenKind.EXTERN)
            allFunctions += this
    }

    override fun toString() = name

    private var tempCount = 0
    fun newTemp() : TempReg {
        val ret = TempReg("t${tempCount++}")
        regs += ret
        return ret
    }

    fun newUserTemp() : UserReg {
        val ret = UserReg("t${tempCount++}")
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
            if (sym.type is TypeErrable) newUnionReg(newUserTemp(), ret) else ret
        }
    }

    fun newUnionReg(typeReg: Reg, valueReg: Reg) : UnionReg {
        val ret = UnionReg(typeReg, valueReg, "u${tempCount++}")
        return ret
    }

    fun stackAlloc(size: Int, offset:Int): Reg {
        // Allocate space on the stack for a variable.
        // Return a pointer 'offset' bytes into the allocated space
        val sizeRounded = (size+3) and -4
        val addr = stackVarSize
        stackVarSize += sizeRounded
        return addAlu(BinOp.ADD_I, cpuRegs[31], addr+offset)
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

    fun addLdImm(dest:CpuReg, value:Int) {
        prog += InstrMovLit(dest, value)
    }



    fun addLoad(size: Int, addr: Reg, offset: Int): TempReg {
        assert(size in listOf(1, 2, 4))
        val dest = newTemp()
        prog += InstrLoad(size, dest, addr, offset)
        return dest
    }

    fun addStore(size: Int, src: Reg, addr: Reg, offset: Int) {
        assert(size in listOf(1, 2, 4))
        prog += InstrStore(size, src, addr, offset)
    }

    fun addLoad(addr: Reg, field: FieldSymbol): TempReg {
        val size = field.type.getSize()
        assert(size in listOf(1, 2, 4))
        val dest = newTemp()
        prog += InstrLoadField(size, dest, addr, field)
        return dest
    }

    fun addStore(src: Reg, addr: Reg, field: FieldSymbol) {
        val size = field.type.getSize()
        assert(size in listOf(1, 2, 4))
        prog += InstrStoreField(size, src, addr, field)
    }

    fun addIndex(size: Int, src: Reg, bounds: Reg): TempReg {
        assert(size in listOf(1, 2, 4))
        val dest = newTemp()
        prog += InstrIndex(size, dest, src, bounds)
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

    fun addVCall(func: Function) {
        assert(func.virtualFunctionNumber >= 0)
        prog += InstrVCall(func)
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