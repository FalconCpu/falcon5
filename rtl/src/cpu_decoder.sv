`timescale 1ns/1ns
`include "cpu.vh"

module cpu_decoder(
    input  logic         clock,
    input  logic         reset,

    // Input instruction
    output logic         p2_ready,            // CPU is ready for next instruction
    input  logic         p2_valid,            // Instruction is valid
    input  logic [31:0]  p2_instr,
    input  logic [31:0]  p2_pc,
    output logic [31:0]  p3_instr,            // Instruction for ALU stage (for exceptions)

    // Connections to the register file
    output logic         p2_literal_b,     // Is the B operand a literal
    output logic [31:0]  p2_literal,          // Literal value for B operand

    // Connections to the ALU
    output logic [31:0]  p3_pc,               // Address of the instruction
    output logic [5:0]   p3_op,               // Operation to perform
    output logic [31:0]  p3_literal,          // Literal value
    output logic         p3_bypass_a4,        // Bypass A from ALU stage
    output logic         p3_bypass_b4,        // Bypass B from ALU stage
    output logic         p3_bypass_a5,        // Bypass A from COM stage
    output logic         p3_bypass_b5,        // Bypass B from COM stage
    output logic [4:0]   p3_dest,             // Destination register 
    output logic [4:0]   p3_latent_dest,      // Destination register for latent instructions

    // Other inputs
    input  logic [4:0]   p4_dest,             // Destination register from ALU stage
    input  logic [4:0]   p5_dest,             // Destination register from COM stage
    input  logic         p5_is_mem_read,      // True if the instruction in the COM stage is a load
    input  logic         p4_divider_busy,     // Divider is busy
    input  logic         p4_mem_busy,         // Memory queue is full
    input  logic         p4_jump,             // Jump taken in ALU stage
    output logic [2:0]   perf_count           // 
);

// Instruction format
//    109876 543 21098 76543 21098765 43210
//    KKKKKK III DDDDD AAAAA CCCCCCCC BBBBB
//    
// K = Kind - what kind of instruction (ALU/LOAD/BRANCH etc) (6 bits)
// I = Opcode - The exact operation within the kind (3 bits)
// D = Destination register (5 bits)
// A = Source register A (5 bits)
// C = Constant - combined with other fields to form immediate (8 bits)
// B = Source register B (5 bits)
//
//
// Latencies:
// Most instructions complete in 1 cycle
// Multiply and config takes 2 cycles
// Loads and divides take multiple cycles. These are tracked with a scoreboard.

wire [5:0] instr_kind   = p2_instr[31:26];
wire [2:0] instr_opcode = p2_instr[25:23];
wire [4:0] instr_d      = p2_instr[22:18];
wire [4:0] instr_a      = p2_instr[17:13];
wire [4:0] instr_b      = p2_instr[4:0];
wire [7:0] instr_c      = p2_instr[12:5];

// Signals in this stage
logic       p2_use_a, p2_use_b;
logic       p2_bypass_a3, p2_bypass_b3;
logic       p2_bypass_a4, p2_bypass_b4;
logic       p2_is_divide, p2_is_memory;
logic       p2_illegal;
logic [4:0] p2_dest;                      // Destination register for instructions that have fixed latency
logic [4:0] p2_latent_dest;               // Destination register for instructions that have longer latency
logic [5:0] p2_op;

logic [31:0] scoreboard, prev_scoreboard; // One bit per register to indicate if it is busy
logic        p2_latent2, p3_latent2;      // True if this instruction will take 2 cycles
logic        hazard_a1, hazard_b1;        // Scoreboard hazard on A or B
logic        hazard_a2, hazard_b2;        // Latency hazard on A or B
logic        hazard_mem, hazard_div;      // Resource hazards
logic [2:0]  next_perf_count, next2_perf_count;
logic        prev_jump;
// Combinatorial logic

always_comb begin
    // Defaults
    p2_ready        = 1'b1;
    p2_use_a        = 1'b0;
    p2_use_b        = 1'b0;
    p2_dest         = 5'b0;
    p2_literal_b    = 1'b0;
    p2_is_divide    = 1'b0;
    p2_is_memory    = 1'b0;
    p2_literal      = 32'bx;
    p2_illegal      = 1'b0;
    p2_latent_dest  = 5'b0;
    p2_latent2      = 1'b0;
    scoreboard      = prev_scoreboard;

    // Decode the instruction
    if (p2_valid==1'b0 || p4_jump) begin
        p2_op    = `OP_NOP;
        p2_dest  = 5'b0;

    end else case(instr_kind)
        `KIND_ALU: begin
            p2_op      = {3'b000, instr_opcode};
            p2_use_a   = 1'b1;
            p2_use_b   = 1'b1;
            p2_dest    = instr_d;
            p2_literal   = {{19{instr_c[7]}}, instr_c, instr_b};        // Used for shift type
        end

        `KIND_ALU_IMM: begin
            p2_op        = {3'b000, instr_opcode};
            p2_use_a     = 1'b1;
            p2_literal_b = 1'b1;
            p2_literal   = {{19{instr_c[7]}}, instr_c, instr_b};
            p2_dest      = instr_d;
        end

        `KIND_LOAD: begin
            p2_op         = {3'b010, instr_opcode};
            p2_use_a      = 1'b1;
            p2_literal_b  = 1'b1;
            p2_literal    = {{19{instr_c[7]}}, instr_c, instr_b};
            p2_latent_dest= instr_d;
            p2_is_memory  = 1'b1;
            p2_illegal    = !(instr_opcode==3'b000 || instr_opcode==3'b001 || instr_opcode==3'b010);
        end

        `KIND_STORE: begin
            p2_op        = {3'b011, instr_opcode};
            p2_use_a     = 1'b1;
            p2_use_b     = 1'b1;
            p2_dest      = 5'b0;
            p2_literal   = {{19{instr_c[7]}}, instr_c, instr_d};
            p2_is_memory = 1'b1;
            p2_illegal   = !(instr_opcode==3'b000 || instr_opcode==3'b001 || instr_opcode==3'b010);
        end

        `KIND_BRANCH: begin
            p2_op      = {3'b001, instr_opcode};
            p2_use_a   = 1'b1;
            p2_use_b   = 1'b1;
            p2_dest    = 5'b0;
            p2_literal = {{17{instr_c[7]}}, instr_c, instr_d, 2'b0};
            p2_illegal = (instr_opcode[2:1]==2'b11);
        end

        `KIND_JUMP: begin
            p2_op        = `OP_JAL;
            p2_literal   = {{9{instr_c[7]}}, instr_c, instr_opcode, instr_a, instr_b, 2'b0};
            p2_dest      = instr_d;
        end

        `KIND_JUMPR: begin
            p2_op      = `OP_JALR;
            p2_use_a   = 1'b1;
            p2_dest    = instr_d;
            p2_literal = {{19{instr_c[7]}}, instr_c, instr_b};    
            p2_illegal = (instr_opcode!=3'b000);
        end

        `KIND_LDIMM: begin
            p2_op        = `OP_LDIMM;
            p2_literal_b = 1'b1;
            p2_literal   = {instr_c, instr_opcode, instr_a, instr_b, 11'b0};
            p2_dest      = instr_d;    
        end

        `KIND_LDPC: begin
            p2_op        = `OP_LDPC;
            p2_literal_b = 1'b1;
            p2_dest      = instr_d;    
            p2_literal   = {{9{instr_c[7]}}, instr_c, instr_opcode, instr_a, instr_b, 2'b0};
        end

        `KIND_MUL: begin
            p2_op      = {3'b101, instr_opcode};
            p2_use_a   = 1'b1;
            p2_use_b   = 1'b1;
            if (instr_opcode[2]) begin
                p2_is_divide   = 1'b1;
                p2_latent_dest = instr_d;
            end else begin
                p2_dest    = instr_d;
                p2_latent2 = 1'b1; // Multiply takes 2 cycles
                p2_illegal = (instr_opcode==3'b001 || instr_opcode==3'b010 || instr_opcode==3'b011);
            end
        end

        `KIND_MUL_IMM: begin
            p2_op        = {3'b101, instr_opcode};
            p2_use_a     = 1'b1;
            p2_literal_b = 1'b1;
            p2_literal   = {{19{instr_c[7]}}, instr_c, instr_b};
            if (instr_opcode[2]) begin
                p2_is_divide   = 1'b1;
                p2_latent_dest = instr_d;
            end else begin
                p2_dest    = instr_d;
                p2_latent2 = 1'b1; // Multiply takes 2 cycles
                p2_illegal = (instr_opcode==3'b001 || instr_opcode==3'b010 || instr_opcode==3'b011);
            end
        end

        `KIND_CFG: begin
            p2_op      = {3'b110, instr_opcode};
            p2_use_a   = 1'b1;
            p2_use_b   = 1'b0;
            p2_literal = {{19{instr_c[7]}}, instr_c, instr_b};
            p2_dest    = instr_d;
            p2_illegal = (instr_opcode[2]);
        end

        `KIND_IDX: begin
            p2_op        = {3'b111, instr_opcode};
            p2_use_a     = 1'b1;
            p2_use_b     = 1'b1;
            p2_dest      = instr_d;    
            p2_illegal   = !(instr_opcode==3'b000 || instr_opcode==3'b001 || instr_opcode==3'b010);
        end

        default: begin
            p2_op      = `OP_ILLEGAL;
            p2_dest    = 5'b0;
            p2_illegal = 1'b1;
        end
    endcase

    if (p2_illegal) begin
        p2_op    = `OP_ILLEGAL;
        p2_dest  = 5'b0;
        p2_latent_dest = 5'b0;
        p2_use_a = 1'b0;
        p2_use_b = 1'b0;
    end

    // Handle data bypassing
    p2_bypass_a3 = p2_use_a && (instr_a!=5'b0) && (instr_a==p3_dest);
    p2_bypass_b3 = p2_use_b && (instr_b!=5'b0) && (instr_b==p3_dest);
    p2_bypass_a4 = p2_use_a && (instr_a!=5'b0) && (instr_a==p4_dest);
    p2_bypass_b4 = p2_use_b && (instr_b!=5'b0) && (instr_b==p4_dest);

    // Handle hazards
    hazard_a1           = p2_use_a && scoreboard[instr_a];
    hazard_b1           = p2_use_b && scoreboard[instr_b];
    hazard_a2           = p2_use_a && instr_a==p3_dest && p3_latent2;
    hazard_b2           = p2_use_b && instr_b==p3_dest && p3_latent2;
    hazard_mem          = p2_is_memory && p4_mem_busy;
    hazard_div          = p2_is_divide && p4_divider_busy;

    if (hazard_a1 || hazard_b1 || hazard_a2 || hazard_b2 || hazard_mem || hazard_div) begin
        p2_op    = `OP_NOP;
        p2_dest  = 5'b0;
        p2_latent_dest = 5'b0;
        p2_ready = 1'b0;
    end

    // Update the scoreboard
    if (p5_is_mem_read)
        scoreboard[p5_dest] = 1'b0; // Clear the scoreboard for the instruction that just completed
    scoreboard[p2_latent_dest] = 1'b1;
    if (p4_jump)
        scoreboard[p3_latent_dest] = 1'b0; // Clear the scoreboard for the instruction that was in the ALU stage when a jump occurs
    scoreboard[0]       = 1'b0; // R0 is always ready

    // Update performance counter
    if (p4_jump || prev_jump)
        next_perf_count = `PERF_JUMP;
    else if (hazard_a1 || hazard_b1 || hazard_a2 || hazard_b2)
        next_perf_count = `PERF_SCOREBOARD;
    else if (hazard_mem || hazard_div)
        next_perf_count = `PERF_RESOURCE;        
    else
        next_perf_count = `PERF_OK;

    // reset
    if (reset) begin
        scoreboard      = 32'b0;
        p2_op           = `OP_NOP;
        p2_dest         = 5'b0;
        p2_ready        = 1'b1;
    end
end




// Pipeline registers
always_ff @(posedge clock) begin
    p3_bypass_a4    <= p2_bypass_a3;
    p3_bypass_b4    <= p2_bypass_b3;
    p3_bypass_a5    <= p2_bypass_a4;
    p3_bypass_b5    <= p2_bypass_b4;
    p3_pc           <= p2_pc + 4;
    p3_op           <= p2_op;
    p3_dest         <= p2_dest; 
    p3_literal      <= p2_literal;
    prev_scoreboard <= scoreboard;
    p3_latent_dest  <= p2_latent_dest;
    p3_latent2      <= p2_latent2;
    perf_count      <= p4_jump ? `PERF_JUMP : next2_perf_count;
    next2_perf_count<= next_perf_count;
    prev_jump       <= p4_jump;
    p3_instr        <= p2_instr;
end
endmodule