module fpu_fcmp (
    input  logic        clock,
    input  logic        start,
    input  logic [31:0] fpu_a,
    input  logic [31:0] fpu_b,
    input  logic [4:0]  fpu_dest,

    output logic [31:0] cmp_result,
    output logic [4:0]  cmp_dest,
    output logic        cmp_valid
);

logic a_mag_greater;
logic a_zero;
logic b_zero;
logic sign_a;
logic [7:0] exp_a;
logic [22:0] mant_a;
logic sign_b;
logic [7:0] exp_b;
logic [22:0] mant_b;
logic a_nan;
logic b_nan;

logic [31:0] dly_result;
logic [4:0]  dly_dest;
logic        dly_valid;


// verilator lint_off BLKSEQ
always_ff @(posedge clock) begin
    sign_a = fpu_a[31];
    exp_a = fpu_a[30:23];
    mant_a = fpu_a[22:0];
    sign_b = fpu_b[31];
    exp_b = fpu_b[30:23];
    mant_b = fpu_b[22:0];

    // Detect NaNs
    a_nan = (exp_a == 8'hFF) && (mant_a != 0);
    b_nan = (exp_b == 8'hFF) && (mant_b != 0);

    a_mag_greater = fpu_a[30:0] > fpu_b[30:0];  // compare magnitude only
    a_zero = (exp_a == 8'h00) && (mant_a == 0);
    b_zero = (exp_b == 8'h00) && (mant_b == 0);

    if (a_nan || b_nan) begin
        dly_result <= 32'd0;   // unordered -> treat as equal/false
    end else if (fpu_a==fpu_b || (a_zero && b_zero)) begin
        dly_result <= 32'd0;    // equal
    end else if (sign_a) begin
        if (sign_b) begin       // both negative
            dly_result <= a_mag_greater ? 32'hFFFFFFFF : 32'd1;
        end else begin          // a negative, b positive
            dly_result <= 32'hFFFFFFFF;
        end
    end else begin                  // a positive
        if (sign_b) begin       // b negative
            dly_result <= 32'd1;
        end else begin          // both positive
            dly_result <= a_mag_greater ? 32'd1 : 32'hFFFFFFFF;
        end
    end

    dly_dest  <= fpu_dest;
    dly_valid <= start;
    cmp_result <= dly_result;
    cmp_dest   <= dly_dest;
    cmp_valid  <= dly_valid;
end

endmodule
