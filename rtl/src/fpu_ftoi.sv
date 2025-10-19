`timescale 1ns/1ns

module fpu_ftoi(
    input logic        clock,
    input logic        start,         // start signal
    input logic [31:0] in_float,      // input value
    input logic [4:0]  in_dest,       // destination register

    output logic        f2i_valid,    // result is valid
    output logic [31:0] f2i_integer,  // result integer
    output logic [4:0]  f2i_dest      // destination register
);

logic [7:0] shift_amount;
logic [7:0] exponent;
logic [31:0] temp_integer;

// verilator lint_off BLKSEQ
always_ff @(posedge clock) begin
    exponent = in_float[30:23];
    if (start) begin
        if (exponent<8'd127)
            // Value < 1.0 -> integer part is 0
            f2i_integer <= 32'd0;
        else if (exponent>8'd158)
            // Overflow -> saturate to max positive/negative integer
            f2i_integer <= (in_float[31]) ? 32'h80000000 : 32'h7fffffff;
        else begin
            // Normal case
            shift_amount = 8'd158 - exponent;
            temp_integer = {1'b1, in_float[22:0], 8'd0} >> shift_amount;
            if (in_float[31]) // negative
                f2i_integer <= -temp_integer;
            else
                f2i_integer <= temp_integer;
        end

        f2i_dest    <= in_dest;
        f2i_valid   <= 1'b1;
    end else 
        f2i_valid <= 1'b0;

end

endmodule