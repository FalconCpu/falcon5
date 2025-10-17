`timescale 1ns / 1ps

module fpu (
    input logic        clock,

    input logic [3:0]  fpu_op,
    input logic [31:0] fpu_in_a,
    input logic [31:0] fpu_in_b,
    input logic [4:0]  fpu_in_dest,

    output logic        fpu_valid,
    output logic [4:0]  fpu_dest,
    output logic [31:0] fpu_result
);

logic        add_valid;
logic [26:0] add_mantissa;
logic [7:0]  add_exponent;
logic        add_sign;
logic [4:0]  add_dest;

logic        mult_valid;
logic [26:0] mult_mantissa;
logic [7:0]  mult_exponent;
logic        mult_sign;
logic [4:0]  mult_dest;

logic        div_valid;
logic [26:0] div_mantissa;
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
    .div_dest(div_dest)
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
