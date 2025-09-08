`timescale 1ns/1ns

module cpu_memif(
    input logic         clock,
    input logic         reset,

    // Inputs from the ALU stage
    input logic        p3_mem_request,
    input logic        p3_mem_write,
    input logic [31:0] p3_mem_addr,
    input logic [31:0] p3_mem_wdata,
    input logic [3:0]  p3_mem_wstrb,
    input logic        p4_mem_abort,  // The memory instruction issued last cycle needs to be aborted (e.g. failed MPU check)
    output logic       p4_mem_busy,

    // Output memory bus
    input  logic       cpu_dcache_ready,
    output logic       cpu_dcache_request,
    output logic       cpu_dcache_write,
    output logic [25:0] cpu_dcache_addr,
    output logic [31:0] cpu_dcache_wdata,
    output logic [3:0]  cpu_dcache_wstrb,
    output logic        cpu_dcache_abort,

    // Output memory bus
    output logic        cpu_aux_request,
    output logic        cpu_aux_write,
    output logic [31:0] cpu_aux_addr,
    output logic [31:0] cpu_aux_wdata,
    output logic [3:0]  cpu_aux_wstrb,
    output logic        cpu_aux_abort
);

logic        prev_cpu_dcache_ready;
logic        prev_cpu_dcache_request;
logic [25:0] prev_cpu_dcache_addr;
logic [31:0] prev_cpu_dcache_wdata;
logic [3:0]  prev_cpu_dcache_wstrb;
logic        prev_cpu_dcache_write;

logic        skid1_request, prev_skid1_request;
logic [25:0] skid1_addr,  prev_skid1_addr;
logic [31:0] skid1_wdata, prev_skid1_wdata;
logic [3:0]  skid1_wstrb, prev_skid1_wstrb;
logic        skid1_write, prev_skid1_write;

logic        skid2_request, prev_skid2_request;
logic [25:0] skid2_addr,  prev_skid2_addr;
logic [31:0] skid2_wdata, prev_skid2_wdata;
logic [3:0]  skid2_wstrb, prev_skid2_wstrb;
logic        skid2_write, prev_skid2_write;
logic        error_skid_overflow;
logic        prev_aux_request;


// Combinatorial logic
always_comb begin
    // Defaults
    cpu_dcache_request = prev_cpu_dcache_request;
    cpu_dcache_addr    = prev_cpu_dcache_addr;
    cpu_dcache_wdata   = prev_cpu_dcache_wdata;
    cpu_dcache_wstrb   = prev_cpu_dcache_wstrb;
    cpu_dcache_write   = prev_cpu_dcache_write;
    skid1_request      = prev_skid1_request;
    skid1_addr         = prev_skid1_addr;
    skid1_wdata        = prev_skid1_wdata;
    skid1_wstrb        = prev_skid1_wstrb;
    skid1_write        = prev_skid1_write;
    skid2_request      = prev_skid2_request;
    skid2_addr         = prev_skid2_addr;
    skid2_wdata        = prev_skid2_wdata;
    skid2_wstrb        = prev_skid2_wstrb;
    skid2_write        = prev_skid2_write;
    cpu_aux_request    = 0;
    cpu_aux_addr       = 32'bx;
    cpu_aux_wdata      = 32'bx;
    cpu_aux_write      = 1'bx;
    cpu_aux_wstrb      = 4'bx;
    error_skid_overflow = 1'b0;
    cpu_dcache_abort   = 1'b0;
    cpu_aux_abort      = 1'b0;

    // If the dcache consumed the previous request, clear it
    if (prev_cpu_dcache_ready) begin
        cpu_dcache_request = skid1_request;
        cpu_dcache_addr    = skid1_addr;
        cpu_dcache_wdata   = skid1_wdata;
        cpu_dcache_wstrb   = skid1_wstrb;
        cpu_dcache_write   = skid1_write;
        skid1_request      = skid2_request;
        skid1_addr         = skid2_addr;
        skid1_wdata        = skid2_wdata;
        skid1_wstrb        = skid2_wstrb;
        skid1_write        = skid2_write;
        skid2_request      = 0;
        skid2_addr         = 26'bx;
        skid2_wdata        = 32'bx;
        skid2_wstrb        = 4'bx;
        skid2_write        = 1'b0;
    end

    // If the memory operation issued last cycle is being aborted, clear the last request
    if (p4_mem_abort) begin
        if (prev_aux_request==1'b1)
            cpu_aux_abort = 1'b1;
        else if (skid2_request==1'b1)
            skid2_request = 1'b0;
        else if (skid1_request==1'b1)
            skid1_request = 1'b0;
        else if (cpu_dcache_request==1'b1)
            cpu_dcache_request = 1'b0;
        else 
            cpu_dcache_abort = 1'b1; // The request has already been accepted by the dcache - abort it there
    end

    p4_mem_busy =  skid1_request;

    // If the ALU stage is requesting a memory operation add it to the queue
    if (p3_mem_request) begin
        if (p3_mem_addr[31]==1'b1) begin
            // Auxiliary peripheral access
            cpu_aux_request = p3_mem_request;
            cpu_aux_addr    = p3_mem_addr;
            cpu_aux_wdata   = p3_mem_wdata;
            cpu_aux_wstrb   = p3_mem_wstrb;
            cpu_aux_write   = p3_mem_write;
        end else if (cpu_dcache_request == 0) begin
            // If the dcache is free, send the request directly
            cpu_dcache_request = p3_mem_request;
            cpu_dcache_addr    = p3_mem_addr[25:0];
            cpu_dcache_wdata   = p3_mem_wdata;
            cpu_dcache_wstrb   = p3_mem_wstrb;
            cpu_dcache_write   = p3_mem_write;
        end else if (!skid1_request) begin
            skid1_request = p3_mem_request;
            skid1_addr    = p3_mem_addr[25:0];
            skid1_wdata   = p3_mem_wdata;
            skid1_wstrb   = p3_mem_wstrb;
            skid1_write   = p3_mem_write;
        end else if (!skid2_request) begin
            skid2_request = p3_mem_request;
            skid2_addr    = p3_mem_addr[25:0];
            skid2_wdata   = p3_mem_wdata;
            skid2_wstrb   = p3_mem_wstrb;
            skid2_write   = p3_mem_write;
        end else begin
            error_skid_overflow = 1'b1;
        end
    end
    
    // If reset, clear everything
    if (reset) begin
        cpu_dcache_request = 1'b0;
        skid1_request      = 1'b0;
        skid2_request      = 1'b0;
    end
end

always_ff @(posedge clock) begin
    prev_cpu_dcache_ready   <= cpu_dcache_ready;
    prev_cpu_dcache_request <= cpu_dcache_request;
    prev_cpu_dcache_addr    <= cpu_dcache_addr;
    prev_cpu_dcache_wdata   <= cpu_dcache_wdata;
    prev_cpu_dcache_wstrb   <= cpu_dcache_wstrb;
    prev_cpu_dcache_write   <= cpu_dcache_write;
    prev_skid1_request      <= skid1_request;
    prev_skid1_addr         <= skid1_addr;
    prev_skid1_wdata        <= skid1_wdata;
    prev_skid1_wstrb        <= skid1_wstrb;
    prev_skid1_write        <= skid1_write;
    prev_skid2_request      <= skid2_request;
    prev_skid2_addr         <= skid2_addr;
    prev_skid2_wdata        <= skid2_wdata;
    prev_skid2_wstrb        <= skid2_wstrb;
    prev_skid2_write        <= skid2_write; 
    prev_aux_request        <= cpu_aux_request;
end

// synthesis translate_off
always @(posedge clock) begin
    if (error_skid_overflow) begin
        $display("ERROR: Memory interface skid buffer overflow");
    end
end
// synthesis translate_on

endmodule