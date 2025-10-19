`timescale 1ps / 1ps

module fpu_i2f (
    input logic        clock,
    input logic        start,         // start signal
    input logic [31:0] in_integer,    // input value
    input logic [4:0]  in_dest,       // destination register

    output logic        i2f_valid,    // result is valid
    output logic [31:0] i2f_mantissa, // result mantissa
    output logic [7:0]  i2f_exponent, // result exponent
    output logic        i2f_sign,     // result sign
    output logic [4:0]  i2f_dest      // destination register
);

logic        dly_start;
logic [31:0] dly_in_integer;
logic [4:0]  dly_in_dest;

always_ff @(posedge clock) begin
    dly_start      <= start;
    dly_in_integer <= in_integer;
    dly_in_dest    <= in_dest;


    if (dly_in_integer[31])  begin
        i2f_mantissa <=  -dly_in_integer;
        i2f_sign     <= 1'b1;
    end else begin
        i2f_mantissa <= dly_in_integer;
        i2f_sign     <= 1'b0;
    end
    i2f_dest     <= dly_in_dest;
    i2f_exponent <= 8'd157; // Bias + 26
    i2f_valid    <= dly_start;
end

endmodule