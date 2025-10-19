module fpu_add (
    input  logic        clock,
    input  logic        fpu_add_start,   // start add/sub operation
    input  logic        fpu_add_sub,     // 0=add 1=sub
    input  logic [31:0] fpu_a,           // operand A
    input  logic [31:0] fpu_b,           // operand B   
    input  logic [4:0]  fpu_dest,        // destination register

    output logic        add_valid,       // input valid signal
    output logic [31:0] add_mantissa,    // input mantissa (with guard/round bits)
    output logic [7:0]  add_exponent,    // input exponent (with bias)
    output logic        add_sign,        // input sign
    output logic [4:0]  add_dest         // destination register
);

// pipeline stage 1 - unpack, align, add/fpu_add_sub
logic [31:0] fpu_b_pol;
logic [31:0] larger, smaller;
logic        larger_subnormal, smaller_subnormal;
logic [26:0] larger_mantissa, smaller_mantissa;
logic [7:0]  larger_exponent, smaller_exponent;
logic [7:0]  exponent_diff;
logic        larger_sign, smaller_sign;
logic [31:0] smaller_mantissa_aligned;
logic [31:0] mantissa_sum;
logic [4:0]  dly_add_dest;
logic        dly_add_valid;

// verilator lint_off BLKSEQ
always_ff @(posedge clock) begin
    // If subtracting, negate operand B
    fpu_b_pol = fpu_add_sub ? {~fpu_b[31], fpu_b[30:0]} : fpu_b;

    // Determine larger and smaller operands
    if (fpu_a[30:0] > fpu_b_pol[30:0]) begin
        larger  = fpu_a;
        smaller = fpu_b_pol;
    end else begin
        larger  = fpu_b_pol;
        smaller = fpu_a;
    end

    // Extract mantissas and exponents
    larger_subnormal  = (larger[30:23] == 8'b0);
    smaller_subnormal = (smaller[30:23] == 8'b0);
    larger_mantissa  <= {1'b0,!larger_subnormal, larger[22:0],2'b0};     // 2 extra bits for guard and round
    smaller_mantissa <= {1'b0,!smaller_subnormal, smaller[22:0],2'b0};
    larger_exponent  <= larger_subnormal ? 8'd1 : larger[30:23];
    smaller_exponent <= smaller_subnormal ? 8'd1 : smaller[30:23];
    larger_sign      <= larger[31];
    smaller_sign     <= smaller[31];

    // Calculate exponent difference, and align smaller mantissa
    exponent_diff = larger_exponent - smaller_exponent;
    smaller_mantissa_aligned = {smaller_mantissa,5'b0} >> exponent_diff;

    // Perform addition or subtraction
    if (larger_sign == smaller_sign) 
        mantissa_sum = {larger_mantissa,5'b0} + smaller_mantissa_aligned;
    else 
        mantissa_sum = {larger_mantissa,5'b0} - smaller_mantissa_aligned;

    add_exponent <= larger_exponent;
    if (mantissa_sum == 32'd0)
        add_sign     <= 1'b0; // result is zero, sign is positive
    else
        add_sign     <= larger_sign;

    add_mantissa <= mantissa_sum;
    dly_add_dest <= fpu_dest;
    dly_add_valid <= fpu_add_start;
    add_dest     <= dly_add_dest;
    add_valid    <= dly_add_valid;
end


endmodule


