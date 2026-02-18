object InstrScheduler {

    data class Edge (
        val pred: Node,
        val succ: Node,
        val latency: Int
    )

    data class Node (val instr: Instr) {
        val preds = mutableListOf<Edge>()
        val succs = mutableListOf<Edge>()
        var scheduled = false
        var criticalPath = 0
    }

    private fun Instr.getLatency(): Int {
        return when(this) {
            is InstrIndCall,
            is InstrIndex,
            is InstrJump,
            is InstrLabel,
            is InstrLea,
            is InstrLineNo,
            is InstrBranch,
            is InstrCall,
            is InstrMov,
            is InstrMovLit,
            is InstrNop,
            is InstrNullCheck,
            is InstrStart,
            is InstrStore,
            is InstrStoreField,
            is InstrSyscall,
            is InstrVCall,
            is InstrEnd -> 1
            is InstrAlu -> if (op==BinOp.MUL_I) 2 else if (op==BinOp.DIV_I || op==BinOp.MOD_I) 32 else 1
            is InstrAluLit -> if (op==BinOp.MUL_I) 2 else if (op==BinOp.DIV_I || op==BinOp.MOD_I) 32 else 1
            is InstrLoad,
            is InstrLoadField -> 2
            is InstrFpu -> if (op==FpuOp.DIV_F) 27 else 3
        }
    }

    fun buildDAG(prog: List<Instr>): List<Node> {
        val nodes = prog.map { Node(it) }
        val lastDef = mutableMapOf<Reg, Node>()
        val lastUse = mutableMapOf<Reg, Node>()

        // sentinel pseudo-register instance for memory
        val mem = TempReg("memory")

        for (n in nodes) {
            val isLoad  = n.instr is InstrLoad || n.instr is InstrLoadField
            val isStore = n.instr is InstrStore || n.instr is InstrStoreField

            // build uses list (include mem for loads)
            val instrUses: List<Reg> = n.instr.getSrcReg() + if (isLoad) listOf(mem) else emptyList()

            // build defs (mem for stores)
            val defs: List<Reg> = when {
                isStore -> listOf(mem)
                else -> {
                    val d = n.instr.getDestReg()
                    if (d != null) listOf(d) else emptyList()
                }
            }

            // --- RAW edges (producer -> this use) ---
            for (r in instrUses) {
                val pred = lastDef[r]
                if (pred != null) {
                    val edge = Edge(pred, n, pred.instr.getLatency())   // latency from producer
                    pred.succs.add(edge)
                    n.preds.add(edge)
                }
            }

            // --- For each def: add WAW (prevDef -> thisDef) and WAR (lastUse -> thisDef) edges ---
            for (d in defs) {
                // WAW: previous definition must come before this one
                lastDef[d]?.let { prevDef ->
                    val edge = Edge(prevDef, n, prevDef.instr.getLatency())
                    prevDef.succs.add(edge)
                    n.preds.add(edge)
                }

                // WAR (anti-dep): any prior use must come before this definition
                // Use a zero-latency edge (or pred.instr.getLatency() if you prefer)
                lastUse[d]?.let { prevUse ->
                    // avoid adding self-edge if same node used and defined (rare)
                    if (prevUse !== n) {
                        val edge = Edge(prevUse, n, 0)
                        prevUse.succs.add(edge)
                        n.preds.add(edge)
                        if (debug) println("WAR edge: ${prevUse.instr} -> ${n.instr} (reg $d)")
                    }
                }

                // Now record this node as the latest def
                lastDef[d] = n

                // Optionally clear lastUse[d] because later defs shouldn't care about older uses:
                // lastUse.remove(d)
            }

            // --- Update lastUse AFTER processing defs to avoid self-dependencies ---
            for (r in instrUses) {
                lastUse[r] = n
            }
        }

        return nodes
    }


//    fun buildDAG(prog:List<Instr>): List<Node> {
//        val nodes = prog.map { Node(it) }
//        val lastDef = mutableMapOf<Reg, Node>()
//
//        //  sentinel pseudo-register instance for memory
//        val mem = TempReg("memory")
//
//
//        for (n in nodes) {
//            val isLoad = n.instr is InstrLoad || n.instr is InstrLoadField
//            val isStore = n.instr is InstrStore || n.instr is InstrStoreField
//            val uses = n.instr.getSrcReg() + if (isLoad) listOf(mem) else emptyList()
//            val defs = if (isStore) mem else n.instr.getDestReg()
//
//            for (r in uses) {
//                val pred = lastDef[r]
//                if (pred!=null) {
//                    val edge = Edge(pred, n, pred.instr.getLatency())
//                    pred.succs.add(edge)
//                    n.preds.add(edge)
//                }
//            }
//
//            if (isStore) {
//                val prev = lastDef[mem]
//                if (prev != null) {
//                    val edge = Edge(prev, n, prev.instr.getLatency())
//                    prev.succs.add(edge)
//                    n.preds.add(edge)
//                }
//            }
//
//            if (defs!=null)
//                lastDef[defs] = n
//
//        }
//        return nodes
//    }

    fun calcCriticalPath(n: Node): Int {
        if (n.criticalPath != 0) return n.criticalPath        // If already computed, reuse it (avoid exponential recursion)

        if (n.succs.isEmpty())
            n.criticalPath = n.instr.getLatency()
        else
            n.criticalPath = n.succs.maxOf { it.latency + calcCriticalPath(it.succ) }
        return n.criticalPath
    }


    fun processBasicBlock(block:List<Instr>) : List<Instr>{

        // Calculate critical path lengths
        val dag = buildDAG(block)
        dag.forEach { if (it.preds.isEmpty()) calcCriticalPath(it) }

        if (debug) {
            println("Processing basic block:")
            for (n in dag)
                println("${n.instr}      CP=${n.criticalPath} Preds=${n.preds.size} Succs=${n.succs.size}")
        }

        // Ready list = no unscheduled predecessors
        val ready = ArrayDeque(dag.filter { it.preds.isEmpty() })
        val result = mutableListOf<Instr>()

        while (ready.isNotEmpty()) {
            // Pick node with the longest critical path
            val node = ready.maxBy{ it.criticalPath }
            ready.remove(node)
            if (node.scheduled)
                continue
            node.scheduled = true
            result += node.instr

            // Update successors
            for (edge in node.succs) {
                val succ = edge.succ
                if (!succ.scheduled && succ.preds.all { it.pred.scheduled })
                    ready.add(succ)
            }
        }

        if (debug) {
            println("Scheduled instructions:")
            for (n in result)
                println(n)
        }

        return result
    }

    private fun schedule(prog:List<Instr>) : List<Instr> {
        val currentBlock = mutableListOf<Instr>()
        val ret = mutableListOf<Instr>()
        for (ins in prog) {
            if (ins is InstrLabel || ins is InstrJump || ins is InstrBranch || ins is InstrEnd || ins is InstrStart
                || ins is InstrCall || ins is InstrVCall || ins is InstrIndCall) {
                if (currentBlock.isNotEmpty())
                    ret += processBasicBlock(currentBlock)
                ret += ins
                currentBlock.clear()
            } else {
                currentBlock.add(ins)
            }
        }
        return ret
    }

    fun runScheduler(func:Function) {
        val newProg = schedule(func.prog)
        func.prog.clear()
        func.prog.addAll(newProg)
    }
}