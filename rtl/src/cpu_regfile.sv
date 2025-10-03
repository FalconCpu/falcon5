`timescale 1ns/1ns

module cpu_regfile(
    input logic         clock,

    // Main pipeline 
    input logic [4:0]  p2_reg_a,            // Which register to read for operand A
    input logic [4:0]  p2_reg_b,            // Which register to read for operand B
    input logic        p2_literal_b,        // Is operand B a literal?
    input logic [31:0] p2_literal,          // Literal value for operand B
    input logic [4:0]  p5_dest_reg,         // Which register to write the result to
    input logic [31:0] p5_result,           // Result to write to the register file

    output logic [31:0] p3_data_a,          // Data read for operand A
    output logic [31:0] p3_data_b           // Data read for operand B
);

// synthesis translate_off
integer fh;
initial begin
    fh = $fopen("rtl_reg.log", "w");
end
// synthesis translate_on

wire wren = p5_dest_reg!=5'h0;
logic [31:0] ram_a;
logic [31:0] ram_b;

regfile_ram regfile_a (
	.clock(clock),
	.data(p5_result),
	.rdaddress(p2_reg_a),
	.wraddress(p5_dest_reg),
	.wren(wren),
	.q(ram_a));

regfile_ram regfile_b (
	.clock(clock),
	.data(p5_result),
	.rdaddress(p2_reg_b),
	.wraddress(p5_dest_reg),
	.wren(wren),
	.q(ram_b));

always_ff @(posedge clock) begin
    p3_data_a <= ram_a;

    if (p2_literal_b)
        p3_data_b <= p2_literal;
    else
        p3_data_b <= ram_b;

    // synthesis translate_off
    if (p5_dest_reg!=5'h0) begin
        $fwrite(fh,"$%d = %08x\n", p5_dest_reg, p5_result);
        if ($isunknown(p5_result))
            $display("WARNING: Write of X to $%d\n", p5_dest_reg);
    end
    // synthesis translate_on
end

endmodule
