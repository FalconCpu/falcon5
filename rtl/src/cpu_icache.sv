`timescale 1ns / 1ns

// For now a simple wrapper around a single port RAM. Later this can 
// be replaced with a proper cache structure.

module cpu_icache(
    input  logic         clock,
    input  logic         reset,

    // CPU interface
    input  logic         cpu_icache_request,   // Request from CPU to ICache
    input  logic [31:0]  cpu_icache_addr,      // Address to ICache. Tag for read.
    input  logic [8:0]   cpu_icache_tag,       // Tag for read.
    output logic         cpu_icache_ready,     // ICache is ready for next request
    output logic         cpu_icache_rvalid,    // Read data is availible
    output logic [31:0]  cpu_icache_rdata,     // The read data.
    output logic [31:0]  cpu_icache_raddr,     // The address of the read data.
    output logic [8:0]   cpu_icache_rtag       // The returned tag of the read data.

    
);

logic [31:0] mem [0:16383]; // 64KB of instruction memory

logic [31:0] raddr;
logic [31:0] rdata;

initial begin
    $readmemh("asm.hex", mem);
end

assign cpu_icache_raddr = cpu_icache_rvalid ? raddr : 32'bx;
assign cpu_icache_rdata = cpu_icache_rvalid ? rdata : 32'bx;

always_ff @(posedge clock) begin
    cpu_icache_ready <= 1'b1; // Always ready
    cpu_icache_rvalid <= cpu_icache_request && !reset;
    rdata <= mem[cpu_icache_addr[15:2]];
    raddr <= cpu_icache_addr;
    cpu_icache_rtag <= cpu_icache_tag;
end

endmodule