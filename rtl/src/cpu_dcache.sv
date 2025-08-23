`timescale 1ns/1ns

module cpu_dcache(
    input logic        clock,
    input logic        reset,

    // Bus from CPU
    input  logic        cpu_dcache_request,
    output logic        cpu_dcache_ready,
    input  logic        cpu_dcache_write,
    input  logic [31:0] cpu_dcache_address,
    input  logic [3:0]  cpu_dcache_wstrb,
    input  logic [31:0] cpu_dcache_wdata,

    // Bus to address decoder
    output logic        cpu_dec_request,
    output logic        cpu_dec_write,
    output logic [31:0] cpu_dec_address,
    output logic [3:0]  cpu_dec_wstrb,
    output logic [31:0] cpu_dec_wdata,
    // (read response comes back along a different path - so not seen here)

    // Bus to SDRAM arbiter
    output logic        dcache_sdram_request,
    input  logic        dcache_sdram_ready,
    output logic        dcache_sdram_write,
    output logic [25:0] dcache_sdram_address,
    output logic [3:0]  dcache_sdram_wstrb,
    output logic [31:0] dcache_sdram_wdata,
    input logic         dcache_sdram_rvalid,
    input logic [31:0]  dcache_sdram_rdata,
    input logic [8:0]   dcache_sdram_rtag,

    output logic        cpu_dcache_rvalid,
    output logic [31:0] cpu_dcache_rdata,
    output logic [8:0]  cpu_dcache_rtag
);

// The transaction we are currently processing
logic        request;       
logic        write;
logic [31:0] address;
logic [3:0]  wstrb;
logic [31:0] wdata;

logic        prev_cpu_sdram_request;
logic        prev_cpu_sdram_write;
logic [25:0] prev_cpu_sdram_address;
logic [3:0]  prev_cpu_sdram_wstrb;
logic [31:0] prev_cpu_sdram_wdata;
logic        prev_dcache_sdram_ready;



always_comb begin
    // Default values
    cpu_dcache_ready      = 1'b0;
    cpu_dec_request       = 1'b0;    // Address decoder is always ready to accept requests
    cpu_dec_write         = 1'bx;
    cpu_dec_address       = 32'hx;
    cpu_dec_wstrb         = 4'hx;
    cpu_dec_wdata         = 32'hx;
    dcache_sdram_request  = prev_cpu_sdram_request;
    dcache_sdram_write    = prev_cpu_sdram_write;
    dcache_sdram_address  = prev_cpu_sdram_address;
    dcache_sdram_wstrb    = prev_cpu_sdram_wstrb;
    dcache_sdram_wdata    = prev_cpu_sdram_wdata;

    // If the SDRAM accepted our request then clear the request
    if (prev_dcache_sdram_ready) begin
        dcache_sdram_request  = 1'b0;
        dcache_sdram_write    = 1'bx;
        dcache_sdram_address  = 26'hx;
        dcache_sdram_wstrb    = 4'hx;
        dcache_sdram_wdata    = 32'hx;
    end

    // pass non-cacheable accesses straight to the address decoder
    if (request && address[31:26]!=0) begin
        // Address decoder is always ready to accept requests - so no need to check
        cpu_dec_request = 1'b1;
        cpu_dec_write   = write;
        cpu_dec_address = address;
        cpu_dec_wstrb   = wstrb;
        cpu_dec_wdata   = wdata;
        cpu_dcache_ready = 1'b1;
    end

    // Pass requests to SDRAM if it is not busy
    if (request && address[31:26]==0 && !dcache_sdram_request) begin
        dcache_sdram_request  = 1'b1;
        dcache_sdram_write    = write;
        dcache_sdram_address  = address[25:0];
        dcache_sdram_wstrb    = wstrb;
        dcache_sdram_wdata    = wdata;
        cpu_dcache_ready      = 1'b1;
    end

    if (!request) 
        cpu_dcache_ready = 1'b1; // We can accept a new request
        
    // Pass read data back to the CPU
    cpu_dcache_rvalid = dcache_sdram_rvalid;
    cpu_dcache_rdata  = dcache_sdram_rdata;
    cpu_dcache_rtag   = dcache_sdram_rtag;

    if(reset) begin
        dcache_sdram_request  = 1'b0;
    end

end

always_ff @(posedge clock) begin
    if (reset) begin
        request <= 1'b0;
        write   <= 1'b0;
        address <= 32'h0;
        wstrb   <= 4'h0;
        wdata   <= 32'h0;
    end else if (cpu_dcache_ready) begin
        request <= cpu_dcache_request;
        write   <= cpu_dcache_write;
        address <= cpu_dcache_address;
        wstrb   <= cpu_dcache_wstrb;
        wdata   <= cpu_dcache_wdata;
    end

    prev_cpu_sdram_request <= dcache_sdram_request;
    prev_cpu_sdram_write <= dcache_sdram_write;
    prev_cpu_sdram_address <= dcache_sdram_address;
    prev_cpu_sdram_wstrb <= dcache_sdram_wstrb;
    prev_cpu_sdram_wdata <= dcache_sdram_wdata;
    prev_dcache_sdram_ready <= dcache_sdram_ready;
end
endmodule