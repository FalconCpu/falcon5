`timescale 1ns / 1ps

module fpu (
    input logic        clock,

    input logic [3:0]  fpu_op,
    input logic [31:0] fpu_in_a,
    input logic [31:0] fpu_in_b,
    input logic [4:0]  fpu_in_dest,

    output logic        fpu_valid,
    output logic [4:0]  fpu_dest,
    output logic [31:0] fpu_result,
    output logic        fpu_div_busy
);

logic        add_valid;
logic [31:0] add_mantissa;
logic [7:0]  add_exponent;
logic        add_sign;
logic [4:0]  add_dest;

logic        mult_valid;
logic [31:0] mult_mantissa;
logic [7:0]  mult_exponent;
logic        mult_sign;
logic [4:0]  mult_dest;

logic        i2f_valid;
logic [31:0] i2f_mantissa;
logic [7:0]  i2f_exponent;
logic        i2f_sign;
logic [4:0]  i2f_dest;

logic        f2i_valid;
logic [31:0] f2i_integer;
logic [4:0]  f2i_dest;

logic [31:0] cmp_result;
logic [4:0]  cmp_dest;
logic        cmp_valid;

logic        div_valid;
logic [31:0] div_mantissa;
logic [7:0]  div_exponent;
logic        div_sign;
logic [4:0]  div_dest;


fpu_add  fpu_add_inst (
    .clock(clock),
    .fpu_add_start(fpu_op==4'h8 || fpu_op==4'h9), // add/sub
    .fpu_add_sub(fpu_op==4'h9),
    .fpu_a(fpu_in_a),
    .fpu_b(fpu_in_b),
    .fpu_dest(fpu_in_dest),
    .add_valid(add_valid),
    .add_mantissa(add_mantissa),
    .add_exponent(add_exponent),
    .add_sign(add_sign),
    .add_dest(add_dest)
  );

fpu_mult  fpu_mult_inst (
    .clock(clock),
    .fpu_mult_start(fpu_op==4'hA),
    .fpu_a(fpu_in_a),
    .fpu_b(fpu_in_b),
    .fpu_dest(fpu_in_dest),
    .mult_valid(mult_valid),
    .mult_mantissa(mult_mantissa),
    .mult_exponent(mult_exponent),
    .mult_sign(mult_sign),
    .mult_dest(mult_dest)
  );

fpu_i2f  fpu_i2f_inst (
    .clock(clock),
    .start(fpu_op==4'hE),
    .in_integer(fpu_in_b),
    .in_dest(fpu_in_dest),
    .i2f_valid(i2f_valid),
    .i2f_mantissa(i2f_mantissa),
    .i2f_exponent(i2f_exponent),
    .i2f_sign(i2f_sign),
    .i2f_dest(i2f_dest)
  );

fpu_div  fpu_div_inst (
    .clock(clock),
    .fpu_div_start(fpu_op==4'hB),
    .fpu_a(fpu_in_a),
    .fpu_b(fpu_in_b),
    .fpu_dest(fpu_in_dest),
    .div_valid(div_valid),
    .div_mantissa(div_mantissa),
    .div_exponent(div_exponent),
    .div_sign(div_sign),
    .div_dest(div_dest),
    .fpu_div_busy(fpu_div_busy)
  );

fpu_ftoi  fpu_ftoi_inst (
    .clock(clock),
    .start(fpu_op==4'hF),
    .in_float(fpu_in_b),
    .in_dest(fpu_in_dest),
    .f2i_valid(f2i_valid),
    .f2i_integer(f2i_integer),
    .f2i_dest(f2i_dest)
  );

fpu_fcmp  fpu_fcmp_inst (
    .clock(clock),
    .start(fpu_op==4'hD),
    .fpu_a(fpu_in_a),
    .fpu_b(fpu_in_b),
    .fpu_dest(fpu_in_dest),
    .cmp_result(cmp_result),
    .cmp_dest(cmp_dest),
    .cmp_valid(cmp_valid)
  );

fpu_normalize  fpu_normalize_inst (
    .clock(clock),
    .add_valid(add_valid),
    .add_mantissa(add_mantissa),
    .add_exponent(add_exponent),
    .add_sign(add_sign),
    .add_dest(add_dest),
    .mult_valid(mult_valid),
    .mult_mantissa(mult_mantissa),
    .mult_exponent(mult_exponent),
    .mult_sign(mult_sign),
    .mult_dest(mult_dest),
    .i2f_valid(i2f_valid),
    .i2f_mantissa(i2f_mantissa),
    .i2f_exponent(i2f_exponent),
    .i2f_sign(i2f_sign),
    .i2f_dest(i2f_dest),
    .f2i_integer(f2i_integer),
    .f2i_valid(f2i_valid),
    .f2i_dest(f2i_dest),
    .fcmp_integer(cmp_result),
    .fcmp_valid(cmp_valid),
    .fcmp_dest(cmp_dest),
    .div_valid(div_valid),
    .div_mantissa(div_mantissa),
    .div_exponent(div_exponent),
    .div_sign(div_sign),
    .div_dest(div_dest),
    .fpu_result(fpu_result),
    .fpu_valid(fpu_valid),
    .fpu_dest(fpu_dest)
  );


endmodule
