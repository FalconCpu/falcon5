`timescale 1ns/1ns

module fpu_output (
    input  logic        clock,

    input  logic        fpu_add_valid,   // Adder result is valid
    input  logic [4:0]  fpu_add_dest,    // Destination register
    input  logic [31:0] fpu_add_result,  // Adder result

    output logic        fpu_valid,       // Writeback data valid
    output logic [4:0]  fpu_dest,
    output logic [31:0] fpu_result,
    output logic        fpu_overflow
);

logic        skid1_fpu_valid, prev_skid1_fpu_valid;
logic [4:0]  skid1_fpu_dest, prev_skid1_fpu_dest;
logic [31:0] skid1_fpu_data, prev_skid1_fpu_data;
logic        skid2_fpu_valid, prev_skid2_fpu_valid;
logic [4:0]  skid2_fpu_dest, prev_skid2_fpu_dest;
logic [31:0] skid2_fpu_data, prev_skid2_fpu_data;

always_comb begin
    // Shift the buffers down
    fpu_valid       = prev_skid1_fpu_valid;
    fpu_dest        = prev_skid1_fpu_dest;
    fpu_result      = prev_skid1_fpu_data;
    skid1_fpu_valid = prev_skid2_fpu_valid;
    skid1_fpu_dest  = prev_skid2_fpu_dest;
    skid1_fpu_data  = prev_skid2_fpu_data;
    skid2_fpu_valid = 1'b0; 
    skid2_fpu_dest  = 5'b0;
    skid2_fpu_data  = 32'b0;

    // If new data arrived output it
    if (fpu_add_valid) begin
        if (!fpu_valid) begin
            fpu_valid = 1'b1;
            fpu_dest  = fpu_add_dest;
            fpu_result  = fpu_add_result;
        end else if (!skid1_fpu_valid) begin
            skid1_fpu_valid = 1'b1;
            skid1_fpu_dest  = fpu_add_dest;
            skid1_fpu_data  = fpu_add_result;
        end else if (!skid2_fpu_valid) begin
            skid2_fpu_valid = 1'b1;
            skid2_fpu_dest  = fpu_add_dest;
            skid2_fpu_data  = fpu_add_result;
        end else begin
            $display("FPU output skid buffer overflow");
            fpu_overflow = 1'b1;
        end
    end
end

always_ff @(posedge clock) begin
    prev_skid1_fpu_valid  <= skid1_fpu_valid;
    prev_skid1_fpu_dest   <= skid1_fpu_dest;
    prev_skid1_fpu_data   <= skid1_fpu_data;
    prev_skid2_fpu_valid  <= skid2_fpu_valid;
    prev_skid2_fpu_dest   <= skid2_fpu_dest;
    prev_skid2_fpu_data   <= skid2_fpu_data;
end

endmodule