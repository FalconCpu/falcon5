`timescale 1ns/1ns

module cpu(
    input logic       clock,
    input logic       reset,
    
    // Instruction Memory Interface
    output logic        cpu_icache_req,      // Request signal to instruction RAM
    input  logic        cpu_icache_ready,    // Ready signal from instruction RAM
    output logic [31:0] cpu_icache_addr,
    output logic        cpu_icache_write,
    output logic [3:0]  cpu_icache_wstrb,
    output logic [31:0] cpu_icache_wdata,
    input  logic [31:0] cpu_icache_rdata,
    input  logic [8:0]  cpu_icache_rtag,
    input  logic        cpu_icache_rvalid
);

endmodule