`timescale 1ns/1ns
`include "cpu.vh"

module cpu_complete(
    input logic clock,

    input logic [5:0]  p4_op,
    input logic [31:0] p4_result,
    input logic [31:0] p4_mult,       // Result of a multiply operation
    input logic        cpu_ready,     // Got an idle slot on the regfile write port -> mux in data from read fifo
    input logic [31:0] read_data,     // Data to be written to the regfile

    output logic [31:0] p5_result,
    output logic [31:0] p5_result_comb
);

always_comb begin
    if (cpu_ready)
        // Use the read data if the CPU is ready
        p5_result_comb = read_data;
    else if (p4_op==`OP_MUL)
        p5_result_comb = p4_mult; // Use the multiply result
    else
        // Pass through the result from the previous stage
        p5_result_comb = p4_result;
end

always_ff @(posedge clock) begin
    p5_result <= p5_result_comb;
end

endmodule