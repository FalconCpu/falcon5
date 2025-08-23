`timescale 1ns/1ns

module pll(
	input  logic refclk,
	input  logic rst,
	output logic outclk_0,
	output logic outclk_1,
	output logic locked
);

always begin
    outclk_0 = 0;
    outclk_1 = 0;
    # 4;
    outclk_0 = 1;
    outclk_1 = 1;
    # 4;
end

initial begin
    locked = 0;
    #16;
    locked = 1;
end

endmodule
