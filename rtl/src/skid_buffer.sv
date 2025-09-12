`timescale 1ns/1ps

module skid_buffer #(
    parameter WIDTH = 32
)
(
    input logic           clock,          // System clock
    input logic           reset,          // Active high reset

    // Input interface
    input logic           in_valid,       // High when input data is valid
    input logic [WIDTH-1:0] in_data,        // Input data

    // Output interface
    output logic          out_valid,      // High when output data is valid
    input logic           out_ready,      // High when downstream can accept data
    output logic [WIDTH-1:0]   out_data        // Output data
);

logic next_out_valid;
logic [WIDTH-1:0] next_out_data;
logic skid1_valid, next_skid1_valid;
logic skid2_valid, next_skid2_valid;
logic [WIDTH-1:0] skid1_data, next_skid1_data;
logic [WIDTH-1:0] skid2_data, next_skid2_data;
logic             error_overflow;

always_comb begin
    next_out_valid = out_valid;
    next_out_data  = out_data;
    next_skid1_valid = skid1_valid;
    next_skid1_data  = skid1_data;
    next_skid2_valid = skid2_valid;
    next_skid2_data  = skid2_data;
    error_overflow = 1'b0;

    // Shift data down the pipeline if possible
    if (out_ready) begin
        next_out_valid = skid1_valid;
        next_out_data  = skid1_data;
        next_skid1_valid = skid2_valid;
        next_skid1_data  = skid2_data;
        next_skid2_valid = 1'b0;
        next_skid2_data  = 'x;
    end

    // Accept new input data if possible
    if (in_valid) begin
        if (!next_out_valid) begin
            next_out_valid = 1'b1;
            next_out_data  = in_data;
        end else if (!next_skid1_valid) begin
            next_skid1_valid = 1'b1;
            next_skid1_data  = in_data;
        end else if (!next_skid2_valid) begin
            next_skid2_valid = 1'b1;
            next_skid2_data  = in_data;
        end else begin
            error_overflow = 1'b1; // Overflow, data lost
        end 
    end

    if (reset) begin
        next_out_valid = 1'b0;
        next_out_data  = 'x;
        next_skid1_valid = 1'b0;
        next_skid1_data  = 'x;
        next_skid2_valid = 1'b0;
        next_skid2_data  = 'x;
    end
end


always_ff @(posedge clock) begin
    out_valid   <= next_out_valid;
    out_data    <= next_out_data;
    skid1_valid <= next_skid1_valid;
    skid1_data  <= next_skid1_data;
    skid2_valid <= next_skid2_valid;
    skid2_data  <= next_skid2_data;
    if (error_overflow) begin
        $display("[%t] ERROR: Skid buffer overflow", $time);
    end
end


endmodule