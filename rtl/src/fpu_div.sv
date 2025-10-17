`timescale 1ns/1ns

module fpu_div (
    input logic        clock,
    input logic        fpu_div_start,  // start operation
    input logic [31:0] fpu_a,           // operand A
    input logic [31:0] fpu_b,           // operand B   
    input logic [4:0]  fpu_dest,        // destination register

    output logic        div_valid,        
    output logic [26:0] div_mantissa,       
    output logic [7:0]  div_exponent,       
    output logic        div_sign,       
    output logic [4:0]  div_dest
);

logic        sign_a;
logic [7:0]  exp_a;
logic [22:0] mant_a;
logic        sign_b;
logic [7:0]  exp_b;
logic [22:0] mant_b;

logic [23:0] denominator;
logic [23:0] remainder;
logic [4:0]  count;
logic [24:0] s;

// verilator lint_off BLKSEQ
always_ff @(posedge clock) begin
    div_valid <= 1'b0;

    if (fpu_div_start) begin
        sign_a = fpu_a[31];
        exp_a  = fpu_a[30:23];
        mant_a = fpu_a[22:0];
        sign_b = fpu_b[31];
        exp_b  = fpu_b[30:23];
        mant_b = fpu_b[22:0];
        div_sign <= sign_a ^ sign_b;
        remainder  <= { (exp_a!=8'd0) , mant_a};
        denominator <= { (exp_b!=8'd0) , mant_b};
        div_exponent <= exp_a - exp_b + 8'd127;
        div_mantissa <= 27'd0;
        div_dest  <= fpu_dest;
        count <= 5'd26;
    end else if (count!=0) begin
        s = remainder - {1'b0,denominator};
        if (s[24] == 1'b0) begin
            remainder <= {s[22:0],1'b0};
            div_mantissa <= {div_mantissa[25:0], 1'b1};
        end else begin
            remainder <= {remainder[22:0],1'b0};
            div_mantissa <= {div_mantissa[25:0], 1'b0};
        end
        count <= count - 5'd1;
        if (count == 5'd1) begin
            div_valid <= 1'b1;
        end
    end

end


endmodule