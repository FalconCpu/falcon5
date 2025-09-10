
data class PathContext (
    val uninitializedVariables : Set<Symbol>,
    val maybeUninitializedVariables : Set<Symbol>,
    val refinedTypes : Map<Symbol, Type>,
    val unreachable : Boolean
) {
    fun addUninitialized(symbol: Symbol) = PathContext(
        uninitializedVariables + symbol,
        maybeUninitializedVariables + symbol,
        refinedTypes,
        unreachable
    )

    fun initialize(symbol:Symbol) = if (symbol !in maybeUninitializedVariables) this else
        PathContext(
        uninitializedVariables - symbol,
        maybeUninitializedVariables - symbol,
        refinedTypes,
        unreachable
    )

    fun refineType(expr:TctExpr, type: Type) : PathContext {
        val sym = when (expr) {
            is TctVariable -> expr.sym
            is TctGlobalVarExpr -> expr.sym
            else -> return this
        }
        return PathContext(
            uninitializedVariables,
            maybeUninitializedVariables,
            refinedTypes + (sym to type),
            unreachable
        )
    }


}

val emptyPathContext = PathContext(
    uninitializedVariables = emptySet(),
    maybeUninitializedVariables = emptySet(),
    refinedTypes = emptyMap(),
    unreachable = false
)

val unreachablePathContext = PathContext(
    uninitializedVariables = emptySet(),
    maybeUninitializedVariables = emptySet(),
    refinedTypes = emptyMap(),
    unreachable = true
)

fun List<PathContext>.merge() : PathContext {
    // Only consider paths which are reachable
    val reachable = this.filter { !it.unreachable }

    // If no path is reachable then return empty
    if (reachable.isEmpty())
        return unreachablePathContext
    if (reachable.size == 1)
        return reachable.first()

    // maybeUninitialized contains symbols which are uninitialized along any reachable path
    val maybeUninitialized = reachable.flatMap { it.maybeUninitializedVariables }.toSet()

    // uninitialised is the symbols which are uninitialized along all reachable paths
    val uninitialised = reachable.first().uninitializedVariables.filter{ sym-> reachable.all {it.uninitializedVariables.contains(sym)}}.toSet()

    // For refined types, only keep types that are consistent across all reachable paths
    val refinedTypes = reachable.first().refinedTypes.filter { (symbol, type) ->
        reachable.all { it.refinedTypes[symbol] == type }
    }

    return PathContext(uninitialised, maybeUninitialized, refinedTypes, false)
}


class BranchPathContext(
    val trueBranch: PathContext,
    val falseBranch: PathContext,
    val expr : TctExpr
)