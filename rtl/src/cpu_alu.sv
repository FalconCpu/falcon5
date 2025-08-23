`timescale 1ns/1ns
`include "cpu.vh"

module cpu_alu (
    input clock,
    input reset, 

    // Inputs from the P2 - Decode stage
    input logic [31:0] p3_data_a,
    input logic [31:0] p3_data_b,
    input logic        p3_bypass_a4,   // Use output of instruction now at P4 as operand A
    input logic        p3_bypass_b4,   // Use output of instruction now at P4 as operand B
    input logic        p3_bypass_a5,   // Use output of instruction now at P5 as operand A
    input logic        p3_bypass_b5,   // Use output of instruction now at P5 as operand B
    input logic [5:0]  p3_op,          // Operation to perform
    input logic [31:0] p3_literal,     // Literal value to use
    input logic [31:0] p3_pc,          // Addres of next instruction
    input logic [4:0]  p3_latent_dest, // Destination register for a latent instruction

    input logic [31:0] p5_result,

    // Memory requests
    output logic        p3_mem_request,
    output logic        p3_mem_write,
    output logic [31:0] p3_mem_address,
    output logic [31:0] p3_mem_wdata,
    output logic [3:0]  p3_mem_wstrb,
    output logic        p3_mem_misaligned,

    // Jumps
    output logic        p3_jump,       // a jump is about to be taken (combinatorial output)
    output logic        p4_jump,       // a jump has been taken
    output logic [31:0] p4_jump_address,

    // connection to the divider
    output logic        p3_div_start,  // pulsed to start division
    output logic [31:0] p3_numerator,  // Numerator for the division
    output logic [31:0] p3_denominator, // Denominator for the division  
    output logic        p3_div_sign,   // Sign of result: 1=negative
    output logic        p3_div_mod,    // 0=divide

    // outputs
    output logic [31:0] p4_result,
    output logic [31:0] p4_mult
);

logic [31:0] p3_result;
logic        [31:0] data_a;
logic        [31:0] data_b;
logic signed [31:0] signed_data_a;
logic               clt;
logic               cltu;
logic        [31:0] jump_target;
logic        [31:0] p3_jump_address;
logic        [31:0] mem_addr;
logic        [31:0] p3_mult;
logic        [32:0] sub;

always_comb begin
    // Select the operands based on bypassing
    data_a = p3_bypass_a4 ? p4_result : 
             p3_bypass_a5 ? p5_result :
             p3_data_a;
    data_b = p3_bypass_b4 ? p4_result : 
             p3_bypass_b5 ? p5_result :
             p3_data_b;

    // Some common expressions
    signed_data_a = $signed(data_a);
    sub = {1'b0, data_a} - {1'b0, data_b};
    clt = sub[31];
    cltu = sub[32];
    jump_target = p3_pc + {p3_literal[29:0], 2'b00}; // PC-relative jump
    mem_addr    = data_a + p3_literal;

    // defaults
    p3_jump         = 1'b0;
    p3_jump_address = 32'bx;
    p3_result       = 32'bx;
    p3_mem_request  = 1'b0;
    p3_mem_write    = 1'bx;
    p3_mem_address  = 32'bx;
    p3_mem_wdata     = 32'bx;
    p3_mem_wstrb    = 4'bx;
    p3_mem_misaligned = 1'b0;
    p3_mult         = 32'bx;
    p3_div_start    = 1'b0;
    p3_numerator    = 32'bx;
    p3_denominator  = 32'bx;
    p3_div_sign     = 1'bx;
    p3_div_mod      = 1'bx;


    case(p3_op) 
        `OP_AND:   p3_result = data_a & data_b;
        `OP_OR:    p3_result = data_a | data_b;
        `OP_XOR:   p3_result = data_a ^ data_b;
        `OP_SHIFT: p3_result = (p3_literal[6:5]==2'b00) ? (data_a << p3_literal[4:0]) :
                               (p3_literal[6:5]==2'b01) ? (data_a >> p3_literal[4:0]) :
                               (p3_literal[6:5]==2'b10) ? (signed_data_a >>> p3_literal[4:0]) :
                               32'hx;
        `OP_ADD:   p3_result = data_a + data_b;
        `OP_SUB:   p3_result = sub[31:0];
        `OP_CLT:   p3_result = {31'b0, clt};
        `OP_CLTU:  p3_result = {31'b0, cltu};

        `OP_LDB: begin 
            p3_mem_request = 1'b1;
            p3_mem_write   = 1'b0;
            p3_mem_address = mem_addr;
            p3_mem_wdata   = {23'bx,2'b00,p3_mem_address[1:0],p3_latent_dest};
        end

        `OP_LDH: begin 
            p3_mem_misaligned  = mem_addr[0];
            p3_mem_request = !p3_mem_misaligned;
            p3_mem_write   = 1'b0;
            p3_mem_address = mem_addr;
            p3_mem_wdata   = {23'bx,2'b01,p3_mem_address[1:0],p3_latent_dest};
        end

        `OP_LDW: begin 
            p3_mem_misaligned  = mem_addr[1:0]!=0;
            p3_mem_request = !p3_mem_misaligned;
            p3_mem_write   = 1'b0;
            p3_mem_address = mem_addr;
            p3_mem_wdata   = {23'bx,2'b10,p3_mem_address[1:0],p3_latent_dest};
        end

        `OP_STB: begin 
            p3_mem_request = 1'b1;
            p3_mem_write   = 1'b1;
            p3_mem_address = mem_addr;
            p3_mem_wdata   = {data_b[7:0],data_b[7:0],data_b[7:0],data_b[7:0]};
            p3_mem_wstrb   = 4'b0001 << mem_addr[1:0];
        end

        `OP_STH: begin 
            p3_mem_misaligned  = mem_addr[0];
            p3_mem_request = !p3_mem_misaligned;
            p3_mem_write   = 1'b1;
            p3_mem_address = mem_addr;
            p3_mem_wdata   = {data_b[15:0],data_b[15:0]};
            p3_mem_wstrb   = (mem_addr[1]==1'b0) ? 4'b0011 : 4'b1100;
        end

        `OP_STW: begin 
            p3_mem_misaligned  = mem_addr[1:0]!=0;
            p3_mem_request = !p3_mem_misaligned;
            p3_mem_write   = 1'b1;
            p3_mem_address = mem_addr;
            p3_mem_wdata   = data_b[31:0];
            p3_mem_wstrb   = 4'b1111;
        end

        `OP_MUL: p3_mult = data_a * data_b;
        `OP_DIVU: begin 
            p3_div_start   = 1'b1;
            p3_numerator   = data_a;
            p3_denominator = data_b;
            p3_div_sign    = 1'b0; // unsigned division
            p3_div_mod     = 1'b0; // divide
        end
        `OP_DIVS: begin 
            p3_div_start   = 1'b1;
            p3_numerator   = data_a[31] ? -data_a : data_a;
            p3_denominator = data_b[31] ? -data_b : data_b;
            p3_div_sign    = (data_b!=0) ? data_a[31] ^ data_b[31] : 1'b0; // sign of result
            p3_div_mod     = 1'b0; // divide
        end
        `OP_MODU: begin 
            p3_div_start   = 1'b1;
            p3_numerator   = data_a;
            p3_denominator = data_b;
            p3_div_sign    = 1'b0; // unsigned division
            p3_div_mod     = 1'b1; // mod
        end
        `OP_MODS: begin 
            p3_div_start   = 1'b1;
            p3_numerator   = data_a[31] ? -data_a : data_a;
            p3_denominator = data_b[31] ? -data_b : data_b;
            p3_div_sign    = data_a[31]; // sign of result
            p3_div_mod     = 1'b1; // mod
        end
        `OP_BEQ: if (data_a==data_b) begin
                     p3_jump         = 1'b1;
                     p3_jump_address = jump_target;
                 end
        `OP_BNE: if (data_a!=data_b) begin
                     p3_jump         = 1'b1;
                     p3_jump_address = jump_target;
                 end
        `OP_BLT: if (clt) begin
                     p3_jump         = 1'b1;
                     p3_jump_address = jump_target;
                 end
        `OP_BGE: if (!clt) begin
                     p3_jump         = 1'b1;
                     p3_jump_address = jump_target;
                 end
        `OP_BLTU: if (cltu) begin
                     p3_jump         = 1'b1;
                     p3_jump_address = jump_target;
                 end
        `OP_BGEU: if (!cltu) begin
                     p3_jump         = 1'b1;
                     p3_jump_address = jump_target;
                 end
        `OP_JMP: begin 
                    p3_jump         = 1'b1;
                    p3_jump_address = jump_target;
                    p3_result       = p3_pc;
                 end
        `OP_JMPR: begin 
                    p3_jump         = 1'b1;
                    p3_jump_address = data_a + {p3_literal[29:0], 2'b00};
                    p3_result       = p3_pc;
                 end
        `OP_LDI: p3_result = p3_literal;
        `OP_LDPC: p3_result = jump_target;
        `OP_IDX1: begin end
        `OP_IDX2: begin end
        `OP_IDX4: begin end
        default: begin end
    endcase


end


always_ff @(posedge clock) begin
    p4_result       <= p3_result;
    p4_jump         <= p3_jump;
    p4_jump_address <= p3_jump_address;
    p4_mult         <= p3_mult;
end

endmodule