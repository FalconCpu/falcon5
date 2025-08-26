`timescale 1ns/1ns

// Temporary - have a small ROM in place of the ICACHE

module cpu_icache (
    input logic       clock,
    
    // Connection to the instruction fetch
    input  logic        ifetch_icache_request,
    output logic        ifetch_icache_ready,
    input  logic [31:0] ifetch_icache_address,
    input  logic [31:0] ifetch_icache_wdata,
    output logic [31:0] ifetch_icache_rdata,
    output logic [31:0] ifetch_icache_raddr,
    output logic [8:0]  ifetch_icache_rtag,
    output logic        ifetch_icache_rvalid,

    // Connection to the IRAM
    output logic        ifetch_iram_request,
    input  logic        ifetch_iram_ready,
    output logic [31:0] ifetch_iram_address,
    output logic [31:0] ifetch_iram_wdata,
    input  logic [31:0] ifetch_iram_rdata,
    input  logic [31:0] ifetch_iram_raddr,
    input  logic [8:0]  ifetch_iram_rtag,
    input  logic        ifetch_iram_rvalid


);

always_comb begin
    ifetch_icache_ready = ifetch_iram_ready;
    ifetch_icache_rdata = ifetch_iram_rdata;
    ifetch_icache_raddr = ifetch_iram_raddr;
    ifetch_icache_rtag  = ifetch_iram_rtag;
    ifetch_icache_rvalid= ifetch_iram_rvalid;

    ifetch_iram_request = ifetch_icache_request;
    ifetch_iram_address = ifetch_icache_address;
    ifetch_iram_wdata   = ifetch_icache_wdata; // Copy tag from wdata
end

wire unused_ok = &{1'b0, clock};

endmodule