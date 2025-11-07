import java.util.BitSet

private val mem = UserReg("mem") // pseudo register representing memory

class CommonSubexpr(val func:Function) {
    private val numRows = func.prog.size
    private val numCols = func.regs.size

    val avail = Array(numRows){BitSet(numCols)}
    val tempRegs = func.regs.filterIsInstance<TempReg>()


    /**
     * Test to see if a register is dependent on other registers or memory/fields.
     */
    private fun dependsOn(reg:Reg, other:Reg) : Boolean {
        if (reg == other) return true
        if (reg !is TempReg) return false
        val ins = reg.def ?: return false
        if (ins is InstrLoad && other == mem) return true
        val src = ins.getSrcReg()
        return src.any { dependsOn(it, other) }
    }

    private fun dependsOn(reg:Reg, field:FieldSymbol) : Boolean {
        if (reg !is TempReg) return false
        val ins = reg.def ?: return false
        if (ins is InstrLoadField && ins.offset == field) {
            return true
        }
        val src = ins.getSrcReg()
        return src.any { dependsOn(it, field) }
    }

    private fun dependsOnAnyMem(reg:Reg) : Boolean {
        if (reg !is TempReg) return false
        val ins = reg.def ?: return false
        if (ins is InstrLoadField || ins is InstrLoad) return true
        val src = ins.getSrcReg()
        return src.any { dependsOnAnyMem(it) }
    }


    /**
     * Build the available expressions table.
     */
    fun buildAvail() {
        // Initially set all expressions to available - except the first row
        for(i in 1 until numRows)
            avail[i].set(0, numCols)
        // Now process each instruction in order - killing any expressions that are invalidated
        for(instr in func.prog) {
            val dest = instr.getDestReg()
            if (dest != null)
                for(temp in tempRegs)
                    if (dependsOn(temp, dest))
                        avail[instr.index].clear(temp.index)
            when(instr) {
                is InstrStore ->
                    for (temp in tempRegs)
                        if (dependsOn(temp, mem))
                            avail[instr.index].clear(temp.index)
                is InstrCall,
                is InstrVCall,
                is InstrIndCall ->
                    for (temp in tempRegs)
                        if (dependsOnAnyMem(temp))
                            avail[instr.index].clear(temp.index)
                is InstrStoreField ->
                    for (temp in tempRegs)
                        if (dependsOn(temp, instr.offset))
                            avail[instr.index].clear(temp.index)
                else -> {}
            }
        }
    }

    fun propagateAvail()  {
        var madeChange = true
        do {
            madeChange = false
            for (i in func.prog) {
                val dest = i.getDestReg()
                if (dest != null)
                    avail[i.index].set(dest.index)
                if (i is InstrJump || i is InstrBranch) {
                    val labelIndex = if (i is InstrJump) i.label.index else if (i is InstrBranch) i.label.index else error("Impossible")
                    val count = avail[labelIndex].cardinality()
                    avail[labelIndex].and(avail[i.index])
                    if (avail[labelIndex].cardinality() != count)
                        madeChange = true
                }
                if (i !is InstrJump && i !is InstrEnd)
                    avail[i.index + 1].and(avail[i.index])
            }
        } while(madeChange)
    }

    fun isEquivalent(reg:Reg, other:Reg) : Boolean {
        if (other is CpuReg) return false  // Don't mess with ABI registers
        if (reg == other) return true
        if (reg !is TempReg || other !is TempReg) return false

        val def1 = reg.def ?: return false          // Get the defining instruction for each register
        val def2 = other.def ?: return false
        if (def1::class != def2::class) return false
        when (def1) {
            is InstrAlu ->
                if (def1.op != (def2 as InstrAlu).op) return false

            is InstrAluLit -> {
                val d2 = def2 as InstrAluLit
                if (def1.op != d2.op || def1.lit != d2.lit) return false
            }

            is InstrIndex -> {
                val d2 = def2 as InstrIndex
                if (def1.size != d2.size) return false
            }

            is InstrLea -> {
                val d2 = def2 as InstrLea
                if (def1.src != d2.src) return false
            }

            is InstrLoad -> {
                val d2 = def2 as InstrLoad
                if (def1.size != d2.size || def1.offset!=d2.offset) return false
            }

            is InstrLoadField -> {
                val d2 = def2 as InstrLoadField
                if (def1.offset != d2.offset) return false
            }

            is InstrMovLit -> {
                val d2 = def2 as InstrMovLit
                if (def1.lit != d2.lit) return false
            }

            is InstrFpu -> {
                val d2 = def2 as InstrFpu
                if (def1.op != d2.op) return false
            }

            else -> return false
        }

        // Now compare the source registers of each instruction
        val src1 = def1.getSrcReg()
        val src2 = def2.getSrcReg()
        if (src1.size != src2.size) return false
        for(i in src1.indices)
            if (!isEquivalent(src1[i], src2[i]))
                return false
        return true
    }


    fun dump() {
        for(y in 0..4) {
            print(" ".repeat(33))
            for (x in 0..<numCols) {
                print(func.regs[x].name.padStart(5)[y])
                if (x % 8 == 7)
                    print(' ')
            }
            print("\n")
        }

        for(y in 0..<numRows) {
            print("%2s %-30s".format(y,func.prog[y].toString()))
            for (x in 0..<numCols) {
                val c = if (avail[y][x]) 'X' else '.'
                print(c)
                if (x%8==7)
                    print(' ')
            }
            print("\n")
        }
    }

    fun lookForEliminations() {
        for (i in func.prog) {
            val dest = i.getDestReg() ?: continue
            if (dest !is TempReg) continue

            for (j in avail[i.index].stream()) {
                val other = func.regs[j]
                if (isEquivalent(dest, other) && other != dest) {
                    val otherDef = if (other is TempReg) other.def else null  // Get the defining instruction for the other register

                    // Don't do the elimination for load immediate if the other definition is too far away (to avoid register pressure)
                    if (i is InstrMovLit && otherDef!=null && otherDef.index<i.index-10) {
                        if (debug)
                            println("Skipping elimination of ${dest.name} in favour of ${other.name} at instruction ${i.index} due to distance")
                        continue
                    }
                    if (debug)
                        println("Eliminating ${dest.name} in favour of ${other.name} at instruction ${i.index}")
                    func.prog[i.index].replace(InstrMov(dest,other))
                    break
                }
            }
        }
    }

    fun run() {
        buildAvail()
        propagateAvail()
        if (debug)
            dump()
        lookForEliminations()
    }
}