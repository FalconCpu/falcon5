
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

    // String Operations
    LT_S,
    GT_S,
    EQ_S,
    NE_S,
    LE_S,
    GE_S,

    // Boolean Operations
    AND_B,
    OR_B
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
    Operator(TokenKind.ASR,     TypeInt,   TypeInt, BinOp.ASR_I,  TypeInt),
    Operator(TokenKind.EQ,      TypeInt,   TypeInt, BinOp.EQ_I,   TypeBool),
    Operator(TokenKind.NEQ,     TypeInt,   TypeInt, BinOp.NE_I,   TypeBool),
    Operator(TokenKind.LT,      TypeInt,   TypeInt, BinOp.LT_I,   TypeBool),
    Operator(TokenKind.GT,      TypeInt,   TypeInt, BinOp.GT_I,   TypeBool),
    Operator(TokenKind.LTE,     TypeInt,   TypeInt, BinOp.LE_I,   TypeBool),
    Operator(TokenKind.GTE,     TypeInt,   TypeInt, BinOp.GE_I,   TypeBool),

    Operator(TokenKind.EQ,      TypeString, TypeString, BinOp.EQ_S,  TypeBool),
    Operator(TokenKind.NEQ,     TypeString, TypeString, BinOp.NE_S,  TypeBool),
    Operator(TokenKind.LT,      TypeString, TypeString, BinOp.LT_S,  TypeBool),
    Operator(TokenKind.GT,      TypeString, TypeString, BinOp.GT_S,  TypeBool),
    Operator(TokenKind.LTE,     TypeString, TypeString, BinOp.LE_S,  TypeBool),
    Operator(TokenKind.GTE,     TypeString, TypeString, BinOp.GE_S,  TypeBool),

    Operator(TokenKind.AND,     TypeBool,  TypeBool, BinOp.AND_B,  TypeBool),
    Operator(TokenKind.OR,      TypeBool,  TypeBool, BinOp.OR_B,   TypeBool)
)