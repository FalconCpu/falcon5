
// The broad `kinds` of instructions
`define KIND_ALU        6'h10
`define KIND_ALU_IMM    6'h11
`define KIND_LOAD       6'h12
`define KIND_STORE      6'h13
`define KIND_BRANCH     6'h14
`define KIND_JUMP       6'h15
`define KIND_JUMPR      6'h16
`define KIND_LDIMM      6'h17
`define KIND_LDPC       6'h18
`define KIND_MUL        6'h19
`define KIND_MUL_IMM    6'h1A
`define KIND_CFG        6'h1B
`define KIND_IDX        6'h1C
`define KIND_FPU        6'h1D

// Operations that can be performed
`define OP_AND          6'b000_000
`define OP_OR           6'b000_001
`define OP_XOR          6'b000_010
`define OP_SHIFT        6'b000_011
`define OP_ADD          6'b000_100
`define OP_SUB          6'b000_101
`define OP_CLT          6'b000_110
`define OP_CLTU         6'b000_111
`define OP_BEQ          6'b001_000
`define OP_BNE          6'b001_001
`define OP_BLT          6'b001_010
`define OP_BGE          6'b001_011
`define OP_BLTU         6'b001_100
`define OP_BGEU         6'b001_101
`define OP_JAL          6'b001_110
`define OP_JALR         6'b001_111
`define OP_LDB          6'b010_000
`define OP_LDH          6'b010_001
`define OP_LDW          6'b010_010
`define OP_LDUB         6'b010_100
`define OP_LDUH         6'b010_101
`define OP_STB          6'b011_000
`define OP_STH          6'b011_001
`define OP_STW          6'b011_010
`define OP_FADD         6'b100_000
`define OP_FSUB         6'b100_001
`define OP_FMUL         6'b100_010
`define OP_FDIV         6'b100_011
`define OP_FSQRT        6'b100_100
`define OP_FCMP         6'b100_101
`define OP_ITOF         6'b100_110
`define OP_FTOI         6'b100_111
`define OP_MUL          6'b101_000
`define OP_DIVU         6'b101_100
`define OP_DIVS         6'b101_101
`define OP_MODU         6'b101_110
`define OP_MODS         6'b101_111
`define OP_CFGR         6'b110_000
`define OP_CFGW         6'b110_001
`define OP_RTE          6'b110_010
`define OP_SYSCALL      6'b110_011
`define OP_LDIMM        6'b110_100
`define OP_LDPC         6'b110_101
`define OP_IDX1         6'b111_000
`define OP_IDX2         6'b111_001
`define OP_IDX4         6'b111_010
`define OP_ILLEGAL      6'b111_110
`define OP_NOP          6'b111_111

`define EXCEPTION_VECTOR 32'hFFFF0004

// Performance counter event types
`define PERF_OK          3'd0    // Instruction executed successfully
`define PERF_JUMP        3'd1    // Null due to jump delay slot
`define PERF_IFETHCH     3'd2    // Waiting for Instruction fetch
`define PERF_SCOREBOARD  3'd3    // Waiting for scoreboard
`define PERF_RESOURCE    3'd4    // Waiting for resource (divider/memory slots)

// Configuration register numbers
`define CFG_REG_VERSION      'h0
`define CFG_REG_EPC          'h1
`define CFG_REG_ECAUSE       'h2
`define CFG_REG_EDATA        'h3
`define CFG_REG_ESTATUS      'h4
`define CFG_REG_ESCRATCH     'h5
`define CFG_REG_MPU_CTRL     'hd
`define CFG_REG_MPU_ADD      'he

// Exception causes
`define CAUSE_INSTRUCTION_ACCESS_FAULT  4'h1
`define CAUSE_ILLEGAL_INSTRUCTION       4'h2
`define CAUSE_BREAKPOINT                4'h3
`define CAUSE_LOAD_ADDRESS_MISALIGNED   4'h4
`define CAUSE_LOAD_ACCESS_FAULT         4'h5
`define CAUSE_STORE_ADDRESS_MISALIGNED  4'h6
`define CAUSE_STORE_ACCESS_FAULT        4'h7
`define CAUSE_SYSTEM_CALL               4'h8
`define CAUSE_INDEX_OVERFLOW            4'h9

// Fields in status register
`define STATUS_SUPERVISOR     'h1 // CPU is in supervisor mode


