// CPU INSTRUCTION FORMAT
//
// 109876 543 21098 76543 21098765 43210
// KKKKKK III DDDDD AAAAA CCCCCCCC BBBBB
// K: Opcode Kind
// I: Opcode Index
// D: Destination Register
// A: Source Register A
// C: Literal value (combined B or D to make 13 bit literal, or with I,D,B to make a 21 bit literal)
// B: Source Register B

// Opcode kinds as they appear in the instruction format
`define KIND_ALU        6'h10
`define KIND_ALU_IMM    6'h11
`define KIND_LOAD       6'h12
`define KIND_STORE      6'h13
`define KIND_BRANCH     6'h14
`define KIND_JUMP       6'h15
`define KIND_JUMP_REG   6'h16
`define KIND_LDI        6'h17
`define KIND_LDPC       6'h18
`define KIND_MULDIV     6'h19
`define KIND_MULDIV_IMM 6'h1A
`define KIND_CFG        6'h1B
`define KIND_INDEX      6'h1C

// Breakdown to an internal more detailed opcode
`define OP_AND          6'h00
`define OP_OR           6'h01
`define OP_XOR          6'h02
`define OP_SHIFT        6'h03
`define OP_ADD          6'h04
`define OP_SUB          6'h05
`define OP_CLT          6'h06
`define OP_CLTU         6'h07
`define OP_LDB          6'h08
`define OP_LDH          6'h09
`define OP_LDW          6'h0A
`define OP_STB          6'h10
`define OP_STH          6'h11
`define OP_STW          6'h12
`define OP_MUL          6'h18
`define OP_DIVU         6'h1C
`define OP_DIVS         6'h1D
`define OP_MODU         6'h1E
`define OP_MODS         6'h1F
`define OP_BEQ          6'h20
`define OP_BNE          6'h21
`define OP_BLT          6'h22
`define OP_BGE          6'h23
`define OP_BLTU         6'h24
`define OP_BGEU         6'h25
`define OP_JMP          6'h26
`define OP_JMPR         6'h27
`define OP_LDI          6'h28
`define OP_LDPC         6'h29
`define OP_IDX1         6'h30
`define OP_IDX2         6'h31
`define OP_IDX4         6'h32
`define OP_ILLEGAL      6'h3F

