`timescale 1ns/1ns

module regfile_ram (
	input	logic         clock,
	input	logic [31:0]  data,
	input	logic [4:0]   rdaddress,
	input	logic [4:0]   wraddress,
	input	logic         wren,
	output	logic [31:0]  q);
    
reg [31:0] mem[0:31];
integer i;

initial begin
    mem[0] = 32'h00000000;
	for(i=1; i<30; i=i+1) 
		mem[i] = 32'hDEADCAA2;
	mem[29] = 32'h00000000;
	mem[30] = 32'h00000000;
end

assign q = mem[rdaddress];

always_ff @(posedge clock) begin
    if (wren) begin
        mem[wraddress] <= data;
    end
end 

endmodule