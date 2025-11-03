`timescale 1ns/1ns

module fpu_div (
    input logic        clock,
    input logic        fpu_div_start,  // start operation
    input logic [31:0] fpu_a,           // operand A
    input logic [31:0] fpu_b,           // operand B   
    input logic [4:0]  fpu_dest,        // destination register

    output logic        div_valid,        
    output logic [31:0] div_mantissa,       
    output logic [7:0]  div_exponent,       
    output logic        div_sign,       
    output logic [4:0]  div_dest,
    output logic        fpu_div_busy,
    input logic         div_ack
);

logic        sign_a;
logic [7:0]  exp_a;
logic [22:0] mant_a;
logic        sign_b;
logic [7:0]  exp_b;
logic [22:0] mant_b;

logic [23:0] denominator;
logic [24:0] remainder;
logic [4:0]  count;
logic [24:0] s;
logic        div_valid_internal;


assign fpu_div_busy = fpu_div_start || (count != 0);

initial begin
    div_valid_internal = 1'b0;
    count = 5'd0;
end

assign div_valid = div_valid_internal && !div_ack;

// verilator lint_off BLKSEQ
always_ff @(posedge clock) begin
    div_valid_internal <= div_valid_internal && !div_ack;

    if (fpu_div_start) begin
        sign_a = fpu_a[31];
        exp_a  = fpu_a[30:23];
        mant_a = fpu_a[22:0];
        sign_b = fpu_b[31];
        exp_b  = fpu_b[30:23];
        mant_b = fpu_b[22:0];
        div_sign <= sign_a ^ sign_b;
        remainder  <= { 1'b0, (exp_a!=8'd0) , mant_a};
        denominator <= { (exp_b!=8'd0) , mant_b};
        div_exponent <= exp_a - exp_b + 8'd127;
        div_mantissa <= 32'd0;
        div_dest  <= fpu_dest;
        count <= 5'd31;
    end else if (count!=0) begin
        s = remainder - {1'b0,denominator};
        if (s[24] == 1'b0) begin
            remainder <= {s[23:0],1'b0};
            div_mantissa <= {div_mantissa[30:0], 1'b1};
        end else begin
            remainder <= {remainder[23:0],1'b0};
            div_mantissa <= {div_mantissa[30:0], 1'b0};
        end
        count <= count - 5'd1;
        if (count == 5'd1) begin
            div_valid_internal <= 1'b1;
        end
    end

end


endmodule