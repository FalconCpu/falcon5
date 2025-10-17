module fpu_mult (
    input  logic        clock,
    input  logic        fpu_mult_start,  // start operation
    input  logic [31:0] fpu_a,           // operand A
    input  logic [31:0] fpu_b,           // operand B   
    input  logic [4:0]  fpu_dest,        // destination register

    output logic        mult_valid,        
    output logic [26:0] mult_mantissa,       
    output logic [7:0]  mult_exponent,       
    output logic        mult_sign,       
    output logic [4:0]  mult_dest
);

logic        sign_a;
logic [7:0]  exp_a;
logic [22:0] mant_a;
logic        sign_b;
logic [7:0]  exp_b;
logic [22:0] mant_b;
logic result_sign;

logic [23:0] norm_mant_a;
logic [23:0] norm_mant_b;
logic [47:0] mant_product;

logic [8:0] eff_exp_a;
logic [8:0] eff_exp_b;
logic [8:0] exp_sum;

// verilator lint_off BLKSEQ
always_ff @(posedge clock) begin
    // Extract sign, exponent, mantissa
    sign_a = fpu_a[31];
    exp_a  = fpu_a[30:23];
    mant_a = fpu_a[22:0];
    sign_b = fpu_b[31];
    exp_b  = fpu_b[30:23];
    mant_b = fpu_b[22:0];

    // Calculate result sign
    result_sign = sign_a ^ sign_b;

    // Handle special cases (zero, infinity, NaN)
    // if ((exp_a == 8'hff && mant_a != 23'd0) || (exp_b == 8'hff && mant_b != 23'd0)) begin
    //     // NaN
    //     mult_result <= {1'b0, 8'hff, 23'h400000}; // Quiet NaN
    // end else if ((exp_a == 8'hff) || (exp_b == 8'hff)) begin
    //     // Infinity
    //     mult_result <= {result_sign, 8'hff, 23'd0};
    // end else if ((exp_a == 8'd0 && mant_a == 23'd0) || (exp_b == 8'd0 && mant_b == 23'd0)) begin
    //     // Zero
    //     mult_result <= {result_sign, 8'd0, 23'd0};
    // end else begin

    // Normalize mantissas
    norm_mant_a = (exp_a == 8'd0) ? {1'b0, mant_a} : {1'b1, mant_a};
    norm_mant_b = (exp_b == 8'd0) ? {1'b0, mant_b} : {1'b1, mant_b};

    // Multiply mantissas
    mant_product = norm_mant_a * norm_mant_b;

    // Calculate exponent
    eff_exp_a = (exp_a == 8'd0) ? 9'd1 : {1'b0, exp_a};
    eff_exp_b = (exp_b == 8'd0) ? 9'd1 : {1'b0, exp_b};
    exp_sum = eff_exp_a + eff_exp_b - 9'd127;
    if (exp_sum[8:7]==2'b11)
        exp_sum = 9'd0; // Underflow
    else if (exp_sum[8:7]==2'b10)
        exp_sum = 9'h0ff; // Overflow

    // Prepare outputs for normalization stage
    mult_mantissa <= mant_product[47:21]; // Take the top 27 bits (with guard/round bits)
    mult_exponent <= exp_sum[7:0];
    mult_dest  <= fpu_dest;
    mult_valid <= fpu_mult_start;
    mult_sign  <= result_sign;
end

endmodule
