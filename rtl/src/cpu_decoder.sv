`timescale 1ns/1ns
`include "cpu.vh"

// This block contains the decoder and control logic for the CPU

module cpu_decoder(
    input logic     clock,
    input logic     reset,

    // Connection to the instruction fetch
    input  logic        p2_valid,       // Is there a valid instruction in the pipeline?
    input  logic [31:0] p2_instr,       // Instruction to decode
    input  logic [31:0] p2_pc,          // Address of the instruction
    output logic        p2_ready,       // Can we accept this instruction?
    
    // Signals back from later stages in the pipeline
    input  logic        p3_jump,        // A jump is about to be taken
    input  logic        p4_jump,        // Has a jump been taken

    // control signals for the P2 - registerfile
    output logic        p2_literal_b,
    output logic [31:0] p2_literal,

    // Control signals for P3 - Execute stage
    output logic        p3_bypass_a4,   // Use output of instruction now at P4 as operand A
    output logic        p3_bypass_b4,   // Use output of instruction now at P4 as operand B
    output logic        p3_bypass_a5,   // Use output of instruction now at P5 as operand A
    output logic        p3_bypass_b5,   // Use output of instruction now at P5 as operand B
    output logic [5:0]  p3_op,          // Operation to perform
    output logic [31:0] p3_literal,     // Literal value to use
    output logic [31:0] p3_pc,          // PC of the instruction
    output logic [4:0]  p3_latent_dest, // Destination register for a latent instruction

    // Control signals for P4 - Compeltion stage
    output logic [5:0]  p4_op,          // Operation to perform

    // Signals to control the read fifo
    output logic        cpu_ready,      // Signal to the read fifo that we are ready to accept data
    input  logic [4:0]  read_dest_reg,  // The register the read fifo wishes to write to
    input               div_ready,      // The divider is ready for a new operation

    // Control signals for P5 - Writeback stage
    output logic [4:0]  p5_dest_reg   // Which register to write the result to
);

// pipeline registers

// Break the input field down into fields
wire [5:0] instr_k = p2_instr[31:26]; // Opcode
wire [2:0] instr_i = p2_instr[25:23]; // Function code
wire [4:0] instr_d = p2_instr[22:18]; // Destination register
wire [4:0] instr_a = p2_instr[17:13]; // Source register A
wire [7:0] instr_c = p2_instr[12:5];  // Constant value
wire [4:0] instr_b = p2_instr[4:0];   // Source register B

// Decode the instruction
logic [5:0]    p2_op;
logic          p2_use_a;        // Use source register A
logic          p2_use_b;        // Use source register B
logic [4:0]    p2_dest_reg;     // Which register to write the result to
logic          p2_bypass_a3;    // Use output of instruction now at P3 as operand A
logic          p2_bypass_b3;    // Use output of instruction now at P3 as operand B
logic          p2_bypass_a4;    // Use output of instruction now at P4 as operand A
logic          p2_bypass_b4;    // Use output of instruction now at P4 as operand B
logic          p2_latency2;     // Is this a latency 2 instruction?
logic          p3_latency2;
logic          p2_is_div; 

logic [4:0]    p2_latent_dest;  // Destination register for a latent instruction
logic [4:0]    p3_dest_reg;     // Destination register instruction now at P3
logic [4:0]    p4_dest_reg;     // Destination register instruction now at P4

logic [31:0]   scoreboard, prev_scoreboard;

// Instructions can be categorised into 3 latency classes. Those who's output is availible
// in the next cycle, those who's output is availible in the cycle after that, and those
// who's latency is greater than 2 cycles or unknown.
// We handle latency2 instructions by introducing a hazard check to prevent the next instruction
// from using the output of the current instruction as an operand.
// Longer latency instructions are handled by a scoreboard - with one bit per register to indicate
// whether the register is valid or not. The scoreboard is updated in the writeback stage,

always_comb begin
    // default values
    p2_op        = 6'b0;
    p2_use_a     = 1'b0;
    p2_use_b     = 1'b0;
    p2_literal_b = 1'b0;
    p2_literal   = 32'bx;
    p2_dest_reg  = 5'b0;
    p2_latency2  = 1'b0;
    p2_latent_dest = 5'b0;
    p2_is_div = 1'b0;
    scoreboard   = prev_scoreboard;

    case(instr_k) 
        `KIND_ALU: begin 
            p2_op        = {3'b0,instr_i};
            p2_use_a     = 1'b1;
            p2_use_b     = 1'b1;
            p2_dest_reg  = instr_d;
        end

        `KIND_ALU_IMM: begin 
            p2_op        = {3'b0,instr_i};
            p2_use_a     = 1'b1;
            p2_literal_b = 1'b1;
            p2_literal   = {{19{instr_c[7]}}, instr_c, instr_b}; 
            p2_dest_reg  = instr_d;
        end

        `KIND_LOAD: begin 
            p2_op        = (instr_i<=3'h2) ? {3'b001,instr_i} : `OP_ILLEGAL;
            p2_use_a     = 1'b1;
            p2_literal   = {{19{instr_c[7]}}, instr_c, instr_b}; 
            p2_latent_dest= instr_d;
        end

        `KIND_STORE: begin 
            p2_op        = (instr_i<=3'h2) ? {3'b010,instr_i} : `OP_ILLEGAL;
            p2_use_a     = 1'b1;
            p2_use_b     = 1'b1;
            p2_literal   = {{19{instr_c[7]}}, instr_c, instr_d}; 
        end

        `KIND_BRANCH: begin 
            p2_op        = (instr_i<=3'h5) ? {3'b100,instr_i} : `OP_ILLEGAL;
            p2_use_a     = 1'b1;
            p2_use_b     = 1'b1;
            p2_literal   = {{19{instr_c[7]}}, instr_c, instr_d}; 
        end

        `KIND_JUMP: begin 
            p2_op        = `OP_JMP;
            p2_literal   = {{11{instr_c[7]}}, instr_c, instr_i, instr_a, instr_b};
            p2_dest_reg  = instr_d;
        end

        `KIND_JUMP_REG: begin 
            p2_op        = `OP_JMPR;
            p2_use_a     = 1'b1;
            p2_literal   = {{19{instr_c[7]}}, instr_c, instr_b}; 
            p2_dest_reg  = instr_d;
        end

        `KIND_LDI: begin 
            p2_op        = `OP_LDI;
            p2_literal   = {instr_c, instr_i, instr_a, instr_b, 11'b0};
            p2_dest_reg  = instr_d;
        end

        `KIND_LDPC: begin 
            p2_op        = `OP_LDPC;
            p2_literal   = {{11{instr_c[7]}},instr_c, instr_i, instr_a, instr_b};
            p2_dest_reg  = instr_d;
        end

        `KIND_MULDIV: begin 
            p2_op        = {3'b011,instr_i};
            if(instr_i[2]==1'b0) begin
                // Multiply op
                p2_latency2  = 1'b1;
                p2_dest_reg  = instr_d;
            end else begin
                // Divide op
                p2_latent_dest = instr_d;
                p2_is_div = 1'b1;
            end
            p2_use_a     = 1'b1;
            p2_use_b     = 1'b1;
        end

        `KIND_MULDIV_IMM: begin 
            p2_op        = {3'b011,instr_i};
            p2_use_a     = 1'b1;
            p2_literal_b = 1'b1;
            p2_literal   = {{19{instr_c[7]}}, instr_c, instr_b}; 
            if(instr_i[2]==1'b0) begin
                // Multiply op
                p2_latency2  = 1'b1;
                p2_dest_reg  = instr_d;
            end else begin
                // Divide op
                p2_latent_dest = instr_d;
                p2_is_div = 1'b1;
            end
        end
    
        `KIND_CFG: begin end

        `KIND_INDEX: begin 
            p2_op        = {3'b110,instr_i};
            p2_use_a     = 1'b1;
            p2_use_b     = 1'b1;
            p2_dest_reg  = instr_d;
        end

        default: begin
            p2_op       = `OP_ILLEGAL;
        end
    endcase

    // Check for bypassing
    p2_bypass_a3 = p2_use_a && instr_a==p3_dest_reg && p3_dest_reg!=0;
    p2_bypass_b3 = p2_use_b && instr_b==p3_dest_reg && p3_dest_reg!=0;
    p2_bypass_a4 = p2_use_a && instr_a==p4_dest_reg && p4_dest_reg!=0;
    p2_bypass_b4 = p2_use_b && instr_b==p4_dest_reg && p4_dest_reg!=0;
    
    // TODO : Scoreboard and check for hazards
    if (!p4_jump)
        scoreboard[p3_latent_dest] = 1'b1;
    scoreboard[0] = 1'b0;

    p2_ready = !( 
        (p2_is_div && !div_ready) ||
        (p2_use_a && instr_a==p3_dest_reg && p3_latency2 && p3_dest_reg!=0) || 
        (p2_use_b && instr_b==p3_dest_reg && p3_latency2 && p3_dest_reg!=0) || 
        (p2_use_a && scoreboard[instr_a]) ||
        (p2_use_b && scoreboard[instr_b]) ||
        (scoreboard[p2_dest_reg])
    );

    // If a jump is taken then nullify the instruction
    // If the instruction wasn't fetched then nullify it
    if (p3_jump | p4_jump || !p2_valid || reset || !p2_ready) begin
        p2_op       = 6'h0;
        p2_dest_reg = 5'b0;
        p2_use_a    = 1'b0;
        p2_use_b    = 1'b0;
        p2_latent_dest = 5'b0;
    end



    // If there is a free slot on the regfile write port then allow the read fifo to use it
    if (p4_dest_reg==5'b0) begin
        cpu_ready = 1'b1;
        p5_dest_reg = read_dest_reg;
        scoreboard[p5_dest_reg] = 1'b0;
    end else begin
        cpu_ready = 1'b0;
        p5_dest_reg = p4_dest_reg;
    end

    if (reset)
        scoreboard = 32'b0; // Reset scoreboard

end


always_ff @(posedge clock) begin
    p3_bypass_a4    <= p2_bypass_a3;
    p3_bypass_b4    <= p2_bypass_b3;
    p3_bypass_a5    <= p2_bypass_a4;
    p3_bypass_b5    <= p2_bypass_b4;
    p3_op           <= p2_op;
    p3_literal      <= p2_literal;
    p3_dest_reg     <= p2_dest_reg;
    p3_pc           <= p2_pc + 4; // PC of the next instruction
    p3_latent_dest  <= p2_latent_dest;
    p3_latency2     <= p2_latency2;

    p4_op           <= p3_op;
    p4_dest_reg     <= p3_dest_reg;

    prev_scoreboard <= scoreboard;
end
endmodule