

// The reg class is used to represent values in the IR code
sealed class Reg(val name: String) {
    var index = -1
    override fun toString(): String = name
}
class UserReg(name: String) : Reg(name)
class CpuReg(name: String) : Reg(name)
class TempReg(name: String) : Reg(name)

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
    override fun toString(): String = name
}

// Represent an instruction in the IR code
sealed class Instr() {
    var index = -1
    override fun toString(): String = when (this) {
        is InstrNop -> "NOP"
        is InstrAlu -> "$op $dest, $src1, $src2"
        is InstrAluLit -> "$op $dest, $src, $lit"
        is InstrBranch -> "${op.branchName()} $src1, $src2, $label"
        is InstrCall -> "CALL $func"
        is InstrEnd -> "END"
        is InstrJump -> "JMP $label"
        is InstrLabel -> "$label:"
        is InstrMov -> "MOV $dest, $src"
        is InstrLea -> "LEA $dest, $src"
        is InstrMovLit -> "MOV $dest, $lit"
        is InstrStart -> "START"
    }

    fun getDestReg(): Reg? = when (this) {
        is InstrAlu -> dest
        is InstrAluLit -> dest
        is InstrLea -> dest
        is InstrMov -> dest
        is InstrMovLit -> dest
        is InstrCall -> if (func.returnType!=TypeUnit) resultReg else null
        is InstrBranch,
        is InstrEnd,
        is InstrJump,
        is InstrNop,
        is InstrLabel,
        is InstrStart -> null
    }

    fun getSrcReg(): List<Reg> = when (this) {
        is InstrAlu -> listOf(src1, src2)
        is InstrAluLit -> listOf(src)
        is InstrBranch -> listOf(src1, src2)
        is InstrMov -> listOf(src)
        is InstrCall -> emptyList()
        is InstrEnd -> emptyList()
        is InstrJump -> emptyList()
        is InstrLabel -> emptyList()
        is InstrLea -> emptyList()
        is InstrMovLit -> emptyList()
        is InstrStart -> emptyList()
        is InstrNop -> emptyList()
    }
}

class InstrNop() : Instr()
class InstrAlu(val op:BinOp, val dest:Reg, val src1: Reg, val src2: Reg) : Instr()
class InstrAluLit(val op: BinOp, val dest: Reg, val src: Reg, val lit: Int) : Instr()
class InstrMov(val dest: Reg, val src: Reg) : Instr()
class InstrMovLit(val dest: Reg, val lit: Int) : Instr()
class InstrLabel(val label: Label) : Instr()
class InstrJump(val label: Label) : Instr()
class InstrBranch(val op:BinOp, val src1: Reg, val src2: Reg, val label: Label) : Instr()
class InstrCall(val func: Function) : Instr()
class InstrLea(val dest: Reg, val src: Value) : Instr()
class InstrStart() : Instr()
class InstrEnd() : Instr()
