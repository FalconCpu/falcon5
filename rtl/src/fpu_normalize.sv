`timescale 1ns/1ns

module fpu_normalize (
    input logic        clock,

    input logic        add_valid,      // input valid signal
    input logic [26:0] add_mantissa,   // input mantissa (with guard/round bits)
    input logic [7:0]  add_exponent,   // input exponent (with bias)
    input logic        add_sign,       // input sign
    input logic [4:0]  add_dest,       // destination register

    input logic        mult_valid,      // input valid signal
    input logic [26:0] mult_mantissa,   // input mantissa (with guard/round bits)
    input logic [7:0]  mult_exponent,   // input exponent (with bias)
    input logic        mult_sign,       // input sign
    input logic [4:0]  mult_dest,       // destination register

    input logic        div_valid,      // input valid signal
    input logic [26:0] div_mantissa,   // input mantissa (with guard/round bits)
    input logic [7:0]  div_exponent,   // input exponent (with bias)
    input logic        div_sign,       // input sign
    input logic [4:0]  div_dest,       // destination register

    output logic [31:0] fpu_result,      // normalized floating-point result
    output logic        fpu_valid,       // result is valid
    output logic [4:0]  fpu_dest        // destination register
);

logic        in_valid;
logic [26:0] in_mantissa;
logic [7:0]  in_exponent;
logic        in_sign;
logic [4:0]  in_dest;



logic [4:0]  clz;
logic [4:0]  norm_shift;
logic [7:0]  normalized_exponent;
logic [22:0] normalized_mantissa;

// verilator lint_off BLKSEQ
always_ff @(posedge clock) begin
    // Select between add and multiply inputs
    if (add_valid) begin
        in_valid    = add_valid;
        in_mantissa = add_mantissa;
        in_exponent = add_exponent;
        in_sign     = add_sign;
        in_dest     = add_dest;
    end else if (mult_valid) begin
        in_valid    = mult_valid;
        in_mantissa = mult_mantissa;
        in_exponent = mult_exponent;
        in_sign     = mult_sign;
        in_dest     = mult_dest;
    end else begin
        in_valid    = div_valid;
        in_mantissa = div_mantissa;
        in_exponent = div_exponent;
        in_sign     = div_sign;
        in_dest     = div_dest;
    end

    // Count leading zeros for normalization


    casez(in_mantissa[25:1])
        25'b1????????????????????????: clz = 5'd0;
        25'b01???????????????????????: clz = 5'd1;
        25'b001??????????????????????: clz = 5'd2;
        25'b0001?????????????????????: clz = 5'd3;
        25'b00001????????????????????: clz = 5'd4;
        25'b000001???????????????????: clz = 5'd5;
        25'b0000001??????????????????: clz = 5'd6;
        25'b00000001?????????????????: clz = 5'd7;
        25'b000000001????????????????: clz = 5'd8;
        25'b0000000001???????????????: clz = 5'd9;
        25'b00000000001??????????????: clz = 5'd10;
        25'b000000000001?????????????: clz = 5'd11;
        25'b0000000000001????????????: clz = 5'd12;
        25'b00000000000001???????????: clz = 5'd13;
        25'b000000000000001??????????: clz = 5'd14;
        25'b0000000000000001?????????: clz = 5'd15;
        25'b00000000000000001????????: clz = 5'd16;
        25'b000000000000000001???????: clz = 5'd17;
        25'b0000000000000000001??????: clz = 5'd18;
        25'b00000000000000000001?????: clz = 5'd19;
        25'b000000000000000000001????: clz = 5'd20;
        25'b0000000000000000000001???: clz = 5'd21;
        25'b00000000000000000000001??: clz = 5'd22;
        25'b000000000000000000000001?: clz = 5'd23;
        25'b0000000000000000000000001: clz = 5'd24;
        default: clz = 5'd25;
    endcase

    // Calculate normalization shift
    if ({3'b0,clz} > in_exponent)
        norm_shift = in_exponent[4:0];
    else
        norm_shift = clz;

    // Do the normalization and rounding
    if (in_mantissa == 27'd0) begin
        normalized_exponent = 8'd0;
        normalized_mantissa = 23'd0;
    end else if (in_mantissa[26]) begin
        normalized_exponent = in_exponent + 8'd1;
        normalized_mantissa = in_mantissa[25:3];
    end else begin
        logic [26:0] mantissa_sum_shifted;
        normalized_exponent = in_exponent - {3'b0,norm_shift}; 
        mantissa_sum_shifted = (in_mantissa << norm_shift);
        normalized_mantissa = mantissa_sum_shifted[24:2];
    end

    // Assemble final result
    if (normalized_exponent == 8'hff) begin
        // Overflow to infinity
        fpu_result <= {in_sign, 8'hff, 23'h0};
    end else if (normalized_exponent == 8'h00) begin
        // Subnormal
        fpu_result <= {in_sign, normalized_exponent, 1'b0,normalized_mantissa[22:1]};
    end else 
        fpu_result <= {in_sign, normalized_exponent, normalized_mantissa};
    fpu_valid <= in_valid;
    fpu_dest  <= in_dest;

end

endmodule