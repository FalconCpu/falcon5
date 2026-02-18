
enum class BinOp {
    // Integer Operations
    ADD_I,
    SUB_I,
    MUL_I,
    DIV_I,
    MOD_I,
    LT_I,
    GT_I,
    EQ_I,
    NE_I,
    LE_I,
    GE_I,
    AND_I,
    OR_I,
    XOR_I,
    LSL_I,
    LSR_I,
    ASR_I,
    LTU_I,    // Less than unsigned
}

fun BinOp.isCommutative() : Boolean = when(this) {
    BinOp.ADD_I,
    BinOp.MUL_I,
    BinOp.AND_I,
    BinOp.OR_I,
    BinOp.XOR_I,
    BinOp.EQ_I,
    BinOp.NE_I -> true

    else -> false
}

fun BinOp.isCompare() : Boolean = when(this) {
    BinOp.LT_I,
    BinOp.GT_I,
    BinOp.EQ_I,
    BinOp.NE_I,
    BinOp.LE_I,
    BinOp.GE_I -> true
    else -> false
}

fun BinOp.branchName() : String = when(this) {
    BinOp.EQ_I -> "BEQ"
    BinOp.NE_I -> "BNE"
    BinOp.LT_I -> "BLT"
    BinOp.GT_I -> "BGT"
    BinOp.LE_I -> "BLE"
    BinOp.GE_I -> "BGE"
    else -> error("Illegal branch condition $this")
}

class Operator(val tokenKind:TokenKind, val lhsType:Type, val rhsType: Type, val resultOp:BinOp, val resultType: Type)

val operatorTable = listOf(
    Operator(TokenKind.PLUS,    TypeInt,   TypeInt, BinOp.ADD_I,  TypeInt),
    Operator(TokenKind.MINUS,   TypeInt,   TypeInt, BinOp.SUB_I,  TypeInt),
    Operator(TokenKind.STAR,    TypeInt,   TypeInt, BinOp.MUL_I,  TypeInt),
    Operator(TokenKind.SLASH,   TypeInt,   TypeInt, BinOp.DIV_I,  TypeInt),
    Operator(TokenKind.PERCENT, TypeInt,   TypeInt, BinOp.MOD_I,  TypeInt),
    Operator(TokenKind.AMPERSAND,TypeInt,  TypeInt, BinOp.AND_I,  TypeInt),
    Operator(TokenKind.BAR,     TypeInt,   TypeInt, BinOp.OR_I,   TypeInt),
    Operator(TokenKind.CARET,   TypeInt,   TypeInt, BinOp.XOR_I,  TypeInt),
    Operator(TokenKind.LSL,     TypeInt,   TypeInt, BinOp.LSL_I,  TypeInt),
    Operator(TokenKind.LSR,     TypeInt,   TypeInt, BinOp.LSR_I,  TypeInt),
    Operator(TokenKind.ASR,     TypeInt,   TypeInt, BinOp.ASR_I,  TypeInt)
)

fun BinOp.evaluate(lhs: Int, rhs: Int) : Int = when(this) {
    BinOp.ADD_I -> lhs + rhs
    BinOp.SUB_I -> lhs - rhs
    BinOp.MUL_I -> lhs * rhs
    BinOp.DIV_I -> lhs / rhs
    BinOp.MOD_I -> lhs % rhs
    BinOp.LT_I  -> if (lhs < rhs) 1 else 0
    BinOp.GT_I  -> if (lhs > rhs) 1 else 0
    BinOp.EQ_I  -> if (lhs == rhs) 1 else 0
    BinOp.NE_I  -> if (lhs != rhs) 1 else 0
    BinOp.LE_I  -> if (lhs <= rhs) 1 else 0
    BinOp.GE_I  -> if (lhs >= rhs) 1 else 0
    BinOp.AND_I -> lhs and rhs
    BinOp.OR_I  -> lhs or rhs
    BinOp.XOR_I -> lhs xor rhs
    BinOp.LSL_I -> lhs shl rhs
    BinOp.LSR_I -> lhs ushr rhs
    BinOp.ASR_I -> lhs shr rhs
    BinOp.LTU_I -> if (lhs.toUInt() < rhs.toUInt()) 1 else 0
}


enum class FpuOp {
    // Integer Operations
    ADD_F,
    SUB_F,
    MUL_F,
    DIV_F,
    SQRT_F,
    CMP_F,
    FTOI_F,
    ITOF_F
}
