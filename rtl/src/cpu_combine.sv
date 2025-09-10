`timescale 1ns/1ns
`include "cpu.vh"
// Just a pass-through module for now.clock
//
// Note - the register file has a registered INPUT - so the signals
// we send to that block actually need to be ready BEFORE the clock edge
// rather than after it. To avoid confusion these are labeled p5u_* rather than p5_*

module cpu_combine(
    input  logic         clock,
    input  logic         reset,

    // Inputs from ALU
    input  logic [5:0]  p4_op,               // Operation to perform
    input  logic [4:0]  p4_dest,             // Destination register
    input  logic        p4_dest_zero,        // True if the destination register is x0
    input  logic [31:0] p4_alu_result,       // Result of the ALU
    input  logic [31:0] p4_mult_result,      // Result of the multiplier

    // Inputs from READPATH
    output logic        mem_ready,
    input  logic [4:0]  mem_dest,
    input  logic [31:0] mem_result,

    // Results
    output  logic [4:0]  p5u_dest_reg,       // Destination register from the COM stage
    output  logic [31:0] p5u_result,         // Result from the COM stage (for bypassing)
    output  logic [31:0] p5_result,
    output  logic        p5_is_mem_read      // True if this is a load instruction 
);


always_comb begin
    mem_ready = 1'b0;

    // Default outputs
    if (p4_dest_zero) begin
        p5u_dest_reg = mem_dest;
        p5u_result   = mem_result;
        mem_ready    = 1'b1;
    end else if (p4_op==`OP_MUL) begin
        p5u_dest_reg = p4_dest;
        p5u_result   = p4_mult_result;
    end else begin
        p5u_dest_reg = p4_dest;
        p5u_result   = p4_alu_result;
    end
end

assign p5_is_mem_read = p4_dest_zero;

always_ff @(posedge clock) begin
    p5_result <= p5u_result;
end

endmodule