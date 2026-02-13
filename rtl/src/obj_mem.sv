`timescale 1ns/1ns

module obj_mem (
	input	         clock,
	input	[9:0]    address_a,
	input	[9:0]    address_b,
	input	[15:0]   byteena_a,
	input	[127:0]  data_a,
	input	[127:0]  data_b,
	input	         wren_a,
	input	         wren_b,
//	output	reg [127:0]  q_a,
	output	reg [127:0]  q_b
);

logic [127:0] memory [0:1023];

// verilator lint_off BLKSEQ
always_ff @(posedge clock) begin
    if (wren_a) begin
        // Write port A
        if (byteena_a[0])  memory[address_a][7:0]   = data_a[7:0];
        if (byteena_a[1])  memory[address_a][15:8]  = data_a[15:8];
        if (byteena_a[2])  memory[address_a][23:16] = data_a[23:16];
        if (byteena_a[3])  memory[address_a][31:24] = data_a[31:24];
        if (byteena_a[4])  memory[address_a][39:32] = data_a[39:32];
        if (byteena_a[5])  memory[address_a][47:40] = data_a[47:40];
        if (byteena_a[6])  memory[address_a][55:48] = data_a[55:48];
        if (byteena_a[7])  memory[address_a][63:56] = data_a[63:56];
        if (byteena_a[8])  memory[address_a][71:64] = data_a[71:64];
        if (byteena_a[9])  memory[address_a][79:72] = data_a[79:72];
        if (byteena_a[10]) memory[address_a][87:80] = data_a[87:80];
        if (byteena_a[11]) memory[address_a][95:88] = data_a[95:88];
        if (byteena_a[12]) memory[address_a][103:96] = data_a[103:96];
        if (byteena_a[13]) memory[address_a][111:104] = data_a[111:104];
        if (byteena_a[14]) memory[address_a][119:112] = data_a[119:112];
        if (byteena_a[15]) memory[address_a][127:120] = data_a[127:120];
    end
    if (wren_b) begin
        // Write port B
        memory[address_b] = data_b;
    end
    // q_a = memory[address_a];
    q_b = memory[address_b];
end
endmodule