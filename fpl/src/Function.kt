val allFunctions = mutableListOf<Function>()

class Function (
    val location: Location,
    val name: String,
    val thisSymbol : VarSymbol?,      // This is null for static functions
    val parameters: List<VarSymbol>,
    val returnType : Type,
    val qualifier: TokenKind,
    val isVararg: Boolean = false,
    val syscallNumber: Int = -1       // Syscall number, -1 if not a syscall
) {
    var virtualFunctionNumber = -1      // Virtual function number, -1 if not a virtual function

    val prog = mutableListOf<Instr>()
    val regs = cpuRegs.toMutableList<Reg>()      // Include all the CPU registers
    val labels = mutableListOf<Label>()
    val symToReg = mutableMapOf<VarSymbol, Reg>()
    val regAllocComments = mutableListOf<String>()
    var maxRegister = 0
    var stackVarSize = 0
    val returnRegister = if (returnType.isAggregate()) newCompoundReg(returnType.getNumElements()) else newUserTemp()

    // For an aggregate return type, the caller allocates memory return value
    val returnDestAddr = if (returnType is TypeStruct) newTemp() else null


    val endLabel = newLabel()
    val exitLabel = newLabel()
    var used = false

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

    fun newTemp(name:String) : TempReg {
        val ret = TempReg(name)
        regs += ret
        return ret
    }


    fun newTempAgg(name:String) : TempReg {
        val ret = TempReg("${name}_${tempCount++}")
        regs += ret
        return ret
    }


    fun newUserTemp() : UserReg {
        val ret = UserReg("u${tempCount++}")
        regs += ret
        return ret
    }

    fun newUserTemp(name:String) : UserReg {
        val ret = UserReg(name)
        regs += ret
        return ret
    }


    fun newAggregateTemp(numElements:Int) : CompoundReg {
        val name = "agg${tempCount++}"
        val regs = (0 until numElements).map { newTempAgg(name) }
        val ret = CompoundReg(regs, name)
        return ret
    }

    fun newLabel() : Label {
        val ret = Label("L${labels.size}")
        labels += ret
        return ret
    }

    fun getReg(sym: VarSymbol): Reg {
        return symToReg.getOrPut(sym) {
            val ret = when(sym.type) {
                is TypeErrable ->  CompoundReg(listOf(newUserTemp(), newUserTemp()), name)
                is TypeTuple -> {
                    val regs = sym.type.elementTypes.map{newUserTemp() }
                    CompoundReg(regs, name)
                }
                is TypeLong -> CompoundReg(listOf(newUserTemp(), newUserTemp()), name)
                else -> UserReg(sym.name)
            }
            regs += ret
            ret
        }
    }

    fun newCompoundReg(numElements:Int) : CompoundReg {
        val regs = (0 until numElements).map { newUserTemp() }
        val name = regs.joinToString(prefix = "{", postfix = "}")
        val ret = CompoundReg(regs, name)
        return ret
    }

    fun newInlineCompoundReg(base:CompoundReg) : CompoundReg {
        val regs = (0 until base.regs.size).map { newUserTemp("inline_$it") }
        val ret = CompoundReg(regs, regs.joinToString(prefix = "{", postfix = "}") )
        return ret
    }


    fun newCompoundReg(regs:List<Reg>) : CompoundReg {
        val copy = regs.map{ addCopy(it) }
        val name = copy.joinToString(prefix = "{", postfix = "}")
        val ret = CompoundReg(copy, name)
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

    fun stackAlloc(size: Int): Int {
        // Allocate space on the stack for a variable.
        // Return the offset of the variable from the stack base pointer
        val ret = stackVarSize
        val sizeRounded = (size+3) and -4
        stackVarSize += sizeRounded
        return ret
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

    fun addFpu(op: FpuOp, src1: Reg, src2: Reg): TempReg {
        val dest = newTemp()
        prog += InstrFpu(op, dest, src1, src2)
        return dest
    }

    fun addMov(dest: Reg, src: Reg) {
        if (src is CompoundReg) {
            require(dest is CompoundReg)
            require(dest.regs.size == src.regs.size)
            for (i in src.regs.indices)
                addMov(dest.regs[i], src.regs[i])
        } else
            prog += InstrMov(dest, src)
    }

    fun addLdImm(value:Int): TempReg {
        val dest = newTemp()
        prog += InstrMovLit(dest, value)
        return dest
    }

    fun addLdImm64(value:Long): CompoundReg {
        val dest = newAggregateTemp(2)
        prog += InstrMovLit(dest.regs[0], (value and 0xFFFFFFFF).toInt())
        prog += InstrMovLit(dest.regs[1], ((value ushr 32) and 0xFFFFFFFF).toInt())
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
        val dest = newTemp()
        if (size in listOf(1, 2, 4))
            prog += InstrIndex(size, dest, src, bounds)
        else {
            val dummy = newTemp()
            prog += InstrIndex(1, dummy, src, bounds)
            prog += InstrAluLit(BinOp.MUL_I, dest, dummy, size)
        }
        return dest
    }

    fun addLineInfo(filename:String, lineNo:Int) {
        prog += InstrLineNo(filename, lineNo)
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

    fun addCall(func: Function, args:List<Reg>) : Reg {
        val dest = when (func.returnType) {
            is TypeErrable -> newAggregateTemp(2)
            is TypeTuple -> newAggregateTemp(func.returnType.elementTypes.size)
            else -> newTemp()
        }
        prog += InstrCall(func, dest, args)
        return dest
    }


    fun addVCall(func: Function, args:List<Reg>) : TempReg {
        assert(func.virtualFunctionNumber >= 0)

        val dest = when (func.returnType) {
            is TypeErrable -> TODO("Handle errable return types in calls")
            is TypeTuple -> TODO("Handle tuple return types in calls")
            else -> newTemp()
        }
        prog += InstrVCall(func, dest, args)
        return dest
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


fun FunctionInstance.hasSameSignature(other: FunctionInstance): Boolean {
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