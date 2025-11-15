
// The reg class is used to represent values in the IR code
sealed class Reg(val name: String) {
    var index = -1
    var useCount = 0
    override fun toString(): String = name
}
class UserReg(name: String) : Reg(name)
class CpuReg(name: String) : Reg(name)
class TempReg(name: String) : Reg(name) {
    var def : Instr? = null
}
class CompoundReg(val regs:List<Reg>, name:String) : Reg(name)

val cpuRegs = (0..31).map {
    CpuReg( when(it) {
        0 -> "0"
        31 -> "SP"
        else -> "R$it"
    } ) }
val resultReg = cpuRegs[8]
val zeroReg = cpuRegs[0]


class Label(val name: String) {
    var index = -1
    var useCount = 0
    override fun toString(): String = name
}

// Represent an instruction in the IR code
sealed class Instr() {
    var index = -1
    override fun toString(): String = when (this) {
        is InstrNop -> "NOP"
        is InstrAlu -> "$op $dest, $src1, $src2"
        is InstrAluLit -> "$op $dest, $src, $lit"
        is InstrFpu -> "$op $dest, $src1, $src2"
        is InstrBranch -> "${op.branchName()} $src1, $src2, $label"
        is InstrCall -> "CALL $func, $dest, $args"
        is InstrVCall -> "VCALL $func, $dest, $args"
        is InstrIndCall -> "INDCALL $func, $dest, $args"
        is InstrEnd -> "END $src"
        is InstrJump -> "JMP $label"
        is InstrLabel -> "$label:"
        is InstrMov -> "MOV $dest, $src"
        is InstrLea -> "LEA $dest, $src"
        is InstrMovLit -> "MOV $dest, $lit"
        is InstrStart -> "START"
        is InstrLoad -> "LD$size $dest, $addr[$offset]"
        is InstrStore -> "ST$size $src, $addr[$offset]"
        is InstrLoadField -> "LD$size $dest, $addr[$offset]"
        is InstrStoreField -> "ST$size $src, $addr[$offset]"
        is InstrIndex -> "IDX$size $dest, $src, $bounds"
        is InstrNullCheck -> "TIZ $src"
        is InstrSyscall -> "SYSCALL $num"
        is InstrLineNo -> "LINE $fileName:$lineNo"
    }

    fun getDestReg(): Reg? = when (this) {
        is InstrAlu -> dest
        is InstrAluLit -> dest
        is InstrFpu -> dest
        is InstrLea -> dest
        is InstrMov -> dest
        is InstrMovLit -> dest
        is InstrLoad -> dest
        is InstrCall -> dest
        is InstrVCall -> dest
        is InstrIndCall -> dest
        is InstrBranch,
        is InstrEnd,
        is InstrJump,
        is InstrNop,
        is InstrLabel,
        is InstrStart,
        is InstrStore -> null
        is InstrLoadField -> dest
        is InstrStoreField -> null
        is InstrIndex -> dest
        is InstrNullCheck -> null
        is InstrSyscall -> dest
        is InstrLineNo -> null
    }

    fun getSrcReg(): List<Reg> = when (this) {
        is InstrAlu -> listOf(src1, src2)
        is InstrAluLit -> listOf(src)
        is InstrFpu -> listOf(src1, src2)
        is InstrBranch -> listOf(src1, src2)
        is InstrMov -> listOf(src)
        is InstrCall -> args
        is InstrVCall -> args
        is InstrIndCall -> listOf(func)+ args
        is InstrEnd -> listOf(src)
        is InstrJump -> emptyList()
        is InstrLabel -> emptyList()
        is InstrLea -> emptyList()
        is InstrMovLit -> emptyList()
        is InstrStart -> emptyList()
        is InstrNop -> emptyList()
        is InstrLoad -> listOf(addr)
        is InstrStore -> listOf(src, addr)
        is InstrLoadField -> listOf(addr)
        is InstrStoreField -> listOf(src, addr)
        is InstrIndex -> listOf(src, bounds)
        is InstrNullCheck -> listOf(src)
        is InstrSyscall -> args
        is InstrLineNo -> emptyList()
    }
}

class InstrNop() : Instr()
class InstrAlu(val op:BinOp, val dest:Reg, val src1: Reg, val src2: Reg) : Instr()
class InstrAluLit(val op: BinOp, val dest: Reg, val src: Reg, val lit: Int) : Instr()
class InstrFpu(val op:FpuOp, val dest:Reg, val src1: Reg, val src2: Reg) : Instr()
class InstrMov(val dest: Reg, val src: Reg) : Instr()
class InstrMovLit(val dest: Reg, val lit: Int) : Instr()
class InstrLabel(val label: Label) : Instr()
class InstrJump(val label: Label) : Instr()
class InstrBranch(val op:BinOp, val src1: Reg, val src2: Reg, val label: Label) : Instr()
class InstrCall(val func: Function, val dest:Reg, val args:List<Reg>) : Instr()
class InstrVCall(val func: Function, val dest:Reg, val args:List<Reg>) : Instr()
class InstrIndCall(val func: Reg, val dest:Reg, val args:List<Reg>, val retType:Type) : Instr()
class InstrLea(val dest: Reg, val src: Value) : Instr()
class InstrLoad(val size:Int, val dest: Reg, val addr: Reg, val offset:Int) : Instr()
class InstrStore(val size:Int, val src: Reg, val addr: Reg, val offset:Int) : Instr()
class InstrLoadField(val size:Int, val dest: Reg, val addr: Reg, val offset: FieldSymbol) : Instr()
class InstrStoreField(val size:Int, val src: Reg, val addr: Reg, val offset:FieldSymbol) : Instr()
class InstrIndex(val size:Int, val dest:Reg, val src:Reg, val bounds:Reg) : Instr()
class InstrNullCheck(val src: Reg) : Instr()
class InstrStart() : Instr()
class InstrEnd(val src:Reg) : Instr()
class InstrSyscall (val num: Int, val dest:Reg, val args:List<Reg>) : Instr()
class InstrLineNo(val fileName:String, val lineNo: Int) : Instr()
