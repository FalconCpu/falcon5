`timescale 1ns/1ns

module cpu_divider(
    input  logic             clock,
    input  logic             reset,
    output logic             div_ready,           // Divider ready to begin an operation
    input  logic             p3_div_start,        // pulsed to start division
    input  logic [31:0]      p3_numerator,        // Numerator for the division
    input  logic [31:0]      p3_denominator,      // Denominator for the division  
    input  logic [4:0]       p3_latent_dest,      // Destination register for the result
    input  logic             p3_div_sign,         // Sign of result: 1=negative
    input  logic             p3_div_mod,          // 0=divide 1=mod
    
    output logic [31:0]      div_result,
    output logic             div_valid,           // result is complete
    output logic [4:0]       div_dest_reg
);
    
logic [4:0]  this_count, next_count;
logic [31:0] this_numerator, next_numerator;
logic [31:0] this_denominator, next_denominator;
logic [31:0] this_quotient, next_quotient;
logic [31:0] this_remainder, next_remainder;
logic [4:0]  next_dest_reg;
logic        this_sign, next_sign;
logic        this_div_mod, next_div_mod;
logic        next_div_valid;
logic        prev_div_ready;

logic signed [31:0] n,s;

// div_valid gets asserted when the division is complete
// div_ready gets asserted when the pipeline has accepted the result

always_comb begin
    next_numerator   = this_numerator;
    next_denominator = this_denominator;
    next_quotient    = this_quotient;
    next_remainder   = this_remainder;
    next_count       = this_count;
    next_dest_reg    = div_dest_reg;
    next_sign        = this_sign;
    next_div_mod     = this_div_mod;
    next_div_valid   = 1'b0;
    div_ready        = prev_div_ready;
    n = 32'bx;
    s = 32'bx;

    if (p3_div_start) begin
        next_sign        = p3_div_sign;
        next_div_mod     = p3_div_mod;
        next_numerator   = p3_numerator;
        next_denominator = p3_denominator;
        next_dest_reg    = p3_latent_dest;
        next_remainder   = 0;
        next_quotient    = 0;
        next_count       = 31;
        next_div_valid   = 1'b0;
        div_ready        = 1'b0;
    end else if (!div_ready && !div_valid) begin
        n = {this_remainder[30:0], this_numerator[this_count]};
        s = n - this_denominator;
        if (s>=0) begin
            next_remainder = s;
            next_quotient = {this_quotient[30:0], 1'b1};
        end else begin
            next_remainder = n;
            next_quotient = {this_quotient[30:0], 1'b0};
        end 
        next_count = this_count - 1'b1;
        if (this_count==0)
            next_div_valid = 1'b1;
    end

    // output the result
    if (div_valid) begin
        case({this_div_mod, this_sign})
            2'b00: div_result = this_quotient;
            2'b01: div_result = -this_quotient;
            2'b10: div_result = this_remainder;
            2'b11: div_result = -this_remainder;
        endcase
        div_ready = 1'b1;
    end else
        div_result = 32'bx;
    
    if (reset) begin
        div_ready = 1'b1;
        next_div_valid = 1'b0;
    end
end

always @(posedge clock) begin
    this_count     <= next_count;
    this_numerator <= next_numerator;
    this_denominator <= next_denominator;
    this_quotient  <= next_quotient;
    this_remainder <= next_remainder;
    div_valid      <= next_div_valid;
    prev_div_ready <= div_ready;
    this_div_mod   <= next_div_mod;
    this_sign      <= next_sign;
    div_dest_reg   <= next_dest_reg;
end

endmodule