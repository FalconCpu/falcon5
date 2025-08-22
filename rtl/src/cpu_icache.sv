`timescale 1ns/1ns

// Temporary - have a small ROM in place of the ICACHE

module cpu_icache (
    input logic       clock,
    
    // Connection to the instruction fetch
    input  logic        ifetch_icache_request,
    output logic        ifetch_icache_ready,
    input  logic        ifetch_icache_write,
    input  logic [31:0] ifetch_icache_address,
    input  logic        ifetch_icache_burst,
    input  logic [3:0]  ifetch_icache_wstrb,
    input  logic [31:0] ifetch_icache_wdata,
    output logic [31:0] ifetch_icache_rdata,
    output logic [31:0] ifetch_icache_raddr,
    output logic [8:0]  ifetch_icache_rtag,
    output logic        ifetch_icache_rvalid
);

logic [31:0] rom [0:16383]; // 64kB of ROM

initial begin
    $readmemh("asm.hex", rom);
end

always_ff @(posedge clock) begin
    ifetch_icache_ready <= 1'b1;

    if (ifetch_icache_request) begin
        ifetch_icache_rdata <= rom[ifetch_icache_address[15:2]]; // 4-byte aligned
        ifetch_icache_raddr <= ifetch_icache_address;
        ifetch_icache_rtag  <= ifetch_icache_wdata[8:0];         // Copy tag from wdata
        ifetch_icache_rvalid<= 1'b1;
    end else begin
        ifetch_icache_rvalid <= 1'b0;
        ifetch_icache_rdata  <= 32'bx;
        ifetch_icache_raddr  <= 32'bx;
        ifetch_icache_rtag   <= 9'bx;
    end

    if (ifetch_icache_request) begin
        if (ifetch_icache_address[31:16]!=16'hffff)
            $display("ERROR %t: Address out of range %x", $time, ifetch_icache_address);
        if (ifetch_icache_write)
            $display("ERROR %t: Write request from ifetch", $time);
        if (ifetch_icache_burst)
            $display("ERROR %t: Burst request from ifetch", $time);
        if (ifetch_icache_wstrb!=4'h0)
            $display("ERROR %t: Burst request from ifetch", $time);
    end
end

wire unused_ok = &{1'b0, ifetch_icache_wdata[31:9]};

endmodule