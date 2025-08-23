`timescale 1ns/1ns

// Fifo of requests to the memory interface

module cpu_memif(
    input       clock,
    input       reset,

    // Bus to the data cache
    output logic        cpu_dcache_request,
    input  logic        cpu_dcache_ready,
    output logic        cpu_dcache_write,
    output logic [31:0] cpu_dcache_address,
    output logic [3:0]  cpu_dcache_wstrb,
    output logic [31:0] cpu_dcache_wdata,

    // Inputs from ALU
    input  logic        p3_mem_request,
    input  logic        p3_mem_write,
    input  logic [31:0] p3_mem_address,
    input  logic [31:0] p3_mem_wdata,
    input  logic [3:0]  p3_mem_wstrb,

    // Stall signal to decoder
    output logic        mem_fifo_full
);

logic        prev_cpu_dcache_request;
logic        prev_cpu_dcache_ready;
logic        prev_cpu_dcache_write;
logic [31:0] prev_cpu_dcache_address;
logic [3:0]  prev_cpu_dcache_wstrb;
logic [31:0] prev_cpu_dcache_wdata;

logic        skid1_request,  prev_skid1_request;
logic        skid1_write,    prev_skid1_write;
logic [31:0] skid1_address,  prev_skid1_address;
logic [3:0]  skid1_wstrb,    prev_skid1_wstrb;
logic [31:0] skid1_wdata,    prev_skid1_wdata;

logic        skid2_request,  prev_skid2_request;
logic        skid2_write,    prev_skid2_write;
logic [31:0] skid2_address,  prev_skid2_address;
logic [3:0]  skid2_wstrb,    prev_skid2_wstrb;
logic [31:0] skid2_wdata,    prev_skid2_wdata;

logic        error_overflow;

always_comb begin
    // defaults
    cpu_dcache_request   = prev_cpu_dcache_request;
    cpu_dcache_write     = prev_cpu_dcache_write;
    cpu_dcache_address   = prev_cpu_dcache_address;
    cpu_dcache_wstrb     = prev_cpu_dcache_wstrb;
    cpu_dcache_wdata     = prev_cpu_dcache_wdata;
    skid1_request        = prev_skid1_request;
    skid1_write          = prev_skid1_write;
    skid1_address        = prev_skid1_address;
    skid1_wstrb          = prev_skid1_wstrb;
    skid1_wdata          = prev_skid1_wdata;
    skid2_request        = prev_skid2_request;
    skid2_write          = prev_skid2_write;
    skid2_address        = prev_skid2_address;
    skid2_wstrb          = prev_skid2_wstrb;
    skid2_wdata          = prev_skid2_wdata;
    error_overflow       = 1'b0;

    // If the dcache consumed a request then shift everything along
    if (prev_cpu_dcache_ready) begin
        cpu_dcache_request   = skid1_request;
        cpu_dcache_write     = skid1_write;
        cpu_dcache_address   = skid1_address;
        cpu_dcache_wstrb     = skid1_wstrb;
        cpu_dcache_wdata     = skid1_wdata;
        skid1_request        = skid2_request;
        skid1_write          = skid2_write;
        skid1_address        = skid2_address;
        skid1_wstrb          = skid2_wstrb;
        skid1_wdata          = skid2_wdata;
        skid2_request        = 1'b0;
        skid2_write          = 1'bx;
        skid2_address        = 32'bx;
        skid2_wstrb          = 4'bx;
        skid2_wdata          = 32'bx;
    end

    // If the CPU made a request then slot it into the fifo
    if (p3_mem_request) begin
        if (cpu_dcache_request==1'b0) begin
            // If the dcache is not busy then send the request
            cpu_dcache_request = 1'b1;
            cpu_dcache_write   = p3_mem_write;
            cpu_dcache_address = p3_mem_address;
            cpu_dcache_wstrb   = p3_mem_wstrb;
            cpu_dcache_wdata   = p3_mem_wdata;
        end else if (skid1_request==1'b0) begin
            // If the dcache is busy then slot it into skid1
            skid1_request   = 1'b1;
            skid1_write     = p3_mem_write;
            skid1_address   = p3_mem_address;
            skid1_wstrb     = p3_mem_wstrb;
            skid1_wdata     = p3_mem_wdata;
        end else if (skid2_request==1'b0) begin
            // If skid1 is busy then slot it into skid2
            skid2_request   = 1'b1;
            skid2_write     = p3_mem_write;
            skid2_address   = p3_mem_address;
            skid2_wstrb     = p3_mem_wstrb;
            skid2_wdata     = p3_mem_wdata;
        end else begin
            error_overflow = 1'b1;
        end
    end

    mem_fifo_full = skid2_request;

    if (reset) begin
        cpu_dcache_request   = 1'b0;
        skid1_request        = 1'b0;
        skid2_request        = 1'b0;
    end
end



always_ff @(posedge clock) begin
    prev_cpu_dcache_request <= cpu_dcache_request;
    prev_cpu_dcache_ready <= cpu_dcache_ready;
    prev_cpu_dcache_write <= cpu_dcache_write;
    prev_cpu_dcache_address <= cpu_dcache_address;
    prev_cpu_dcache_wstrb <= cpu_dcache_wstrb;
    prev_cpu_dcache_wdata <= cpu_dcache_wdata;
    prev_skid1_request <= skid1_request;
    prev_skid1_write <= skid1_write;
    prev_skid1_address <= skid1_address;
    prev_skid1_wstrb <= skid1_wstrb;
    prev_skid1_wdata <= skid1_wdata;
    prev_skid2_request <= skid2_request;
    prev_skid2_write <= skid2_write;
    prev_skid2_address <= skid2_address;
    prev_skid2_wstrb <= skid2_wstrb;
    prev_skid2_wdata <= skid2_wdata;

    if (error_overflow) begin
        $display("ERROR %t: Memory interface overflow - requests dropped", $time);
    end
end



endmodule