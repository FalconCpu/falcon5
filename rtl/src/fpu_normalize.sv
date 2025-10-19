`timescale 1ns/1ns

module fpu_normalize (
    input logic        clock,

    input logic        add_valid,      // input valid signal
    input logic [31:0] add_mantissa,   // input mantissa (with guard/round bits)
    input logic [7:0]  add_exponent,   // input exponent (with bias)
    input logic        add_sign,       // input sign
    input logic [4:0]  add_dest,       // destination register

    input logic        mult_valid,      // input valid signal
    input logic [31:0] mult_mantissa,   // input mantissa (with guard/round bits)
    input logic [7:0]  mult_exponent,   // input exponent (with bias)
    input logic        mult_sign,       // input sign
    input logic [4:0]  mult_dest,       // destination register

    input logic        i2f_valid,      // input valid signal
    input logic [31:0] i2f_mantissa,   // input mantissa (with guard/round bits)
    input logic [7:0]  i2f_exponent,   // input exponent (with bias)
    input logic        i2f_sign,       // input sign
    input logic [4:0]  i2f_dest,       // destination register

    input logic [31:0] f2i_integer,    // input integer value
    input logic        f2i_valid,      // input valid signal
    input logic [4:0]  f2i_dest,       // destination register

    input logic [31:0] fcmp_integer,    // input integer value
    input logic        fcmp_valid,      // input valid signal
    input logic [4:0]  fcmp_dest,       // destination register

    input logic        div_valid,      // input valid signal
    input logic [31:0] div_mantissa,   // input mantissa (with guard/round bits)
    input logic [7:0]  div_exponent,   // input exponent (with bias)
    input logic        div_sign,       // input sign
    input logic [4:0]  div_dest,       // destination register

    output logic [31:0] fpu_result,      // normalized floating-point result
    output logic        fpu_valid,       // result is valid
    output logic [4:0]  fpu_dest        // destination register
);

logic        in_valid;
logic [31:0] in_mantissa;
logic [7:0]  in_exponent;
logic        in_sign;
logic [4:0]  in_dest;

logic [4:0]  clz;
logic [4:0]  norm_shift;
logic [7:0]  normalized_exponent;
logic [22:0] normalized_mantissa;
logic        is_integer;

// verilator lint_off BLKSEQ
always_ff @(posedge clock) begin
    is_integer = 1'b0;

    // Select between add and multiply inputs
    if (add_valid) begin
        in_valid    = 1'b1;
        in_mantissa = add_mantissa;
        in_exponent = add_exponent;
        in_sign     = add_sign;
        in_dest     = add_dest;
    end else if (mult_valid) begin
        in_valid    = 1'b1;
        in_mantissa = mult_mantissa;
        in_exponent = mult_exponent;
        in_sign     = mult_sign;
        in_dest     = mult_dest;
    end else if (i2f_valid) begin
        in_valid    = 1'b1;
        in_mantissa = i2f_mantissa;
        in_exponent = i2f_exponent;
        in_sign     = i2f_sign;
        in_dest     = i2f_dest;
    end else if (f2i_valid) begin
        in_valid    = 1'b1;
        in_mantissa = f2i_integer;
        in_dest      = f2i_dest;
        is_integer   = 1'b1;
    end else if (fcmp_valid) begin
        in_valid    = 1'b1;
        in_mantissa = fcmp_integer;
        in_dest     = fcmp_dest;
        is_integer   = 1'b1;
    end else begin
        in_valid    = div_valid;
        in_mantissa = div_mantissa;
        in_exponent = div_exponent;
        in_sign     = div_sign;
        in_dest     = div_dest;
    end

    // Count leading zeros for normalization
    casez(in_mantissa[30:0])
        31'b1??????????????????????????????: clz = 5'd0;
        31'b01?????????????????????????????: clz = 5'd1;
        31'b001????????????????????????????: clz = 5'd2;
        31'b0001???????????????????????????: clz = 5'd3;
        31'b00001??????????????????????????: clz = 5'd4;
        31'b000001?????????????????????????: clz = 5'd5;
        31'b0000001????????????????????????: clz = 5'd6;
        31'b00000001???????????????????????: clz = 5'd7;
        31'b000000001??????????????????????: clz = 5'd8;
        31'b0000000001?????????????????????: clz = 5'd9;
        31'b00000000001????????????????????: clz = 5'd10;
        31'b000000000001???????????????????: clz = 5'd11;
        31'b0000000000001??????????????????: clz = 5'd12;
        31'b00000000000001?????????????????: clz = 5'd13;
        31'b000000000000001????????????????: clz = 5'd14;
        31'b0000000000000001???????????????: clz = 5'd15;
        31'b00000000000000001??????????????: clz = 5'd16;
        31'b000000000000000001?????????????: clz = 5'd17;
        31'b0000000000000000001????????????: clz = 5'd18;
        31'b00000000000000000001???????????: clz = 5'd19;
        31'b000000000000000000001??????????: clz = 5'd20;
        31'b0000000000000000000001?????????: clz = 5'd21;
        31'b00000000000000000000001????????: clz = 5'd22;
        31'b000000000000000000000001???????: clz = 5'd23;
        31'b0000000000000000000000001??????: clz = 5'd24;
        31'b00000000000000000000000001?????: clz = 5'd25;
        31'b000000000000000000000000001????: clz = 5'd26;
        31'b0000000000000000000000000001???: clz = 5'd27;
        31'b00000000000000000000000000001??: clz = 5'd28;
        31'b000000000000000000000000000001?: clz = 5'd29;
        31'b0000000000000000000000000000001: clz = 5'd30;
        31'b0000000000000000000000000000000: clz = 5'd31;
        default: clz = 5'd25;
    endcase

    // Calculate normalization shift
    if ({3'b0,clz} > in_exponent)
        norm_shift = in_exponent[4:0];
    else
        norm_shift = clz;

    // Do the normalization and rounding
    if (in_mantissa == 32'd0) begin
        normalized_exponent = 8'd0;
        normalized_mantissa = 23'd0;
    end else if (in_mantissa[31]) begin
        normalized_exponent = in_exponent + 8'd1;
        normalized_mantissa = in_mantissa[30:8];
    end else begin
        logic [31:0] mantissa_sum_shifted;
        normalized_exponent = in_exponent - {3'b0,norm_shift}; 
        mantissa_sum_shifted = (in_mantissa << norm_shift);
        normalized_mantissa = mantissa_sum_shifted[29:7];
    end

    // Assemble final result
    if (is_integer) begin
        fpu_result <= in_mantissa; // just pass through integer value
    end else if (normalized_exponent == 8'hff) begin
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