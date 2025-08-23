`timescale 1ns/1ns

module cpu_ifetch(
    input logic       clock,
    input logic       reset,
    
    // Connection to the instruction cache
    output logic        ifetch_icache_request,
    input  logic        ifetch_icache_ready,
    output logic        ifetch_icache_write,
    output logic [31:0] ifetch_icache_address,
    output logic        ifetch_icache_burst,
    output logic [3:0]  ifetch_icache_wstrb,
    output logic [31:0] ifetch_icache_wdata,
    input  logic [31:0] ifetch_icache_rdata,
    input  logic [31:0] ifetch_icache_raddr,
    input  logic [8:0]  ifetch_icache_rtag,
    input  logic        ifetch_icache_rvalid,

    // requests to jump
    input  logic        p4_jump,
    input  logic [31:0] p4_jump_address,

    // Connection to the decoder
    output logic        p2_valid,
    output logic [31:0] p2_instr,
    output logic [31:0] p2_pc,
    input  logic        p2_ready
);

logic        prev_ifetch_icache_request;
logic        prev_ifetch_icache_ready;
logic        prev_ifetch_icache_write;
logic [31:0] prev_ifetch_icache_address;
logic        prev_ifetch_icache_burst;
logic [3:0]  prev_ifetch_icache_wstrb;
logic [31:0] prev_ifetch_icache_wdata;

logic        next_p2_valid;
logic [31:0] next_p2_instr;
logic [31:0] next_p2_pc;
logic        skid1_valid, next_skid1_valid;
logic [31:0] skid1_instr, next_skid1_instr;
logic [31:0] skid1_pc, next_skid1_pc;
logic        skid2_valid, next_skid2_valid;
logic [31:0] skid2_instr, next_skid2_instr;
logic [31:0] skid2_pc, next_skid2_pc;

logic [2:0]  jump_count, prev_jump_count;
logic [31:0] pc, prev_pc;
logic [2:0]  pending_count, prev_pending_count;

// Asertions
logic        error_skid_overflow;
logic        error_skid_underflow;


always_comb begin
    // Default values
    ifetch_icache_request  = prev_ifetch_icache_request;
    ifetch_icache_address  = prev_ifetch_icache_address;
    ifetch_icache_write    = prev_ifetch_icache_write;
    ifetch_icache_burst    = prev_ifetch_icache_burst;
    ifetch_icache_wstrb    = prev_ifetch_icache_wstrb;
    ifetch_icache_wdata    = prev_ifetch_icache_wdata;
    next_p2_valid          = p2_valid;
    next_p2_instr          = p2_instr;
    next_p2_pc             = p2_pc;
    next_skid1_valid       = skid1_valid;
    next_skid1_instr       = skid1_instr;
    next_skid1_pc          = skid1_pc;
    next_skid2_valid       = skid2_valid;
    next_skid2_instr       = skid2_instr;
    next_skid2_pc          = skid2_pc;
    jump_count             = prev_jump_count;
    pc                     = prev_pc;
    pending_count          = prev_pending_count;
    error_skid_overflow    = 1'b0;
    error_skid_underflow   = 1'b0;


    // Clear the request if it was consumed last cycle
    if (prev_ifetch_icache_ready) begin
        ifetch_icache_request = 1'b0;
        ifetch_icache_address = 32'bx;
        ifetch_icache_write   = 1'bx;
        ifetch_icache_burst   = 1'bx;
        ifetch_icache_wstrb   = 4'bx;
        ifetch_icache_wdata   = 32'bx;
    end

    // update the PC
    if (p4_jump) begin
        pc         = p4_jump_address;
        jump_count = prev_jump_count + 1'b1;
    end else if (prev_ifetch_icache_ready && prev_ifetch_icache_request) begin
        pc = pc + 4;
    end

    // make a request
    if (ifetch_icache_request==1'b0 && pending_count==0) begin
        ifetch_icache_request = 1'b1;
        ifetch_icache_address = pc;
        ifetch_icache_write   = 1'b0;
        ifetch_icache_burst   = 1'b0;
        ifetch_icache_wstrb   = 4'h0;
        ifetch_icache_wdata   = {29'b0, jump_count}; // Tag with jump count
        pending_count         = pending_count + 1'b1;
    end

    // Handle the decoder consuming the instruction
    if (p2_ready) begin
        next_p2_valid    = skid1_valid;
        next_p2_instr    = skid1_instr;
        next_p2_pc       = skid1_pc;
        next_skid1_valid = skid2_valid;
        next_skid1_instr = skid2_instr;
        next_skid1_pc    = skid2_pc;
        next_skid2_valid = 1'b0;
        next_skid2_instr = 32'bx;
        next_skid2_pc    = 32'bx;

        if (pending_count==0)
            error_skid_underflow = 1'b1;
        pending_count = pending_count - 1'b1;

    end

    // Flush the buffers on a jump
    if (p4_jump) begin
        next_p2_valid    = 1'b0;
        next_p2_instr    = 32'bx;
        next_p2_pc       = 32'bx;
        next_skid1_valid = 1'b0;
        next_skid1_instr = 32'bx;
        next_skid1_pc    = 32'bx;
        next_skid2_valid = 1'b0;
        next_skid2_instr = 32'bx;
        next_skid2_pc    = 32'bx;
        pending_count    = 3'b0;
    end

    // Handle response from the cache - write it into the appropriate slot in the skid buffer
    if (ifetch_icache_rvalid && ifetch_icache_rtag[2:0]==jump_count) begin
        if (next_p2_valid==0) begin
            next_p2_valid    = 1'b1;
            next_p2_instr    = ifetch_icache_rdata;
            next_p2_pc       = ifetch_icache_raddr;
        end else if (next_skid1_valid==0) begin
            next_skid1_valid = 1'b1;
            next_skid1_instr = ifetch_icache_rdata;
            next_skid1_pc    = ifetch_icache_raddr;
        end else if (next_skid2_valid==0) begin
            next_skid2_valid = 1'b1;
            next_skid2_instr = ifetch_icache_rdata;
            next_skid2_pc    = ifetch_icache_raddr;
        end else 
            error_skid_overflow = 1'b1;
    end


    // Reset
    if (reset) begin
        pc                    = 32'hffff0000;
        ifetch_icache_request = 1'b0;
        ifetch_icache_address = 32'bx;
        ifetch_icache_write   = 1'bx;
        ifetch_icache_burst   = 1'bx;
        ifetch_icache_wstrb   = 4'bx;
        ifetch_icache_wdata   = 32'bx;
        next_p2_valid         = 1'b0;
        next_p2_instr         = 32'bx;
        next_p2_pc            = 32'bx;
        next_skid1_valid      = 1'b0;
        next_skid1_instr      = 32'bx;
        next_skid1_pc         = 32'bx;
        next_skid2_valid      = 1'b0;
        next_skid2_instr      = 32'bx;
        next_skid2_pc         = 32'bx;
        pending_count         = 3'b0;
        jump_count            = 3'b0;
    end
end

always_ff @(posedge clock) begin
    prev_ifetch_icache_request <= ifetch_icache_request;
    prev_ifetch_icache_ready   <= ifetch_icache_ready;
    prev_ifetch_icache_address <= ifetch_icache_address;
    prev_ifetch_icache_write   <= ifetch_icache_write;
    prev_ifetch_icache_burst   <= ifetch_icache_burst;
    prev_ifetch_icache_wstrb   <= ifetch_icache_wstrb;
    prev_ifetch_icache_wdata   <= ifetch_icache_wdata;
    p2_valid                   <= next_p2_valid;
    p2_instr                   <= next_p2_instr;
    p2_pc                      <= next_p2_pc;
    skid1_valid                <= next_skid1_valid;
    skid1_instr                <= next_skid1_instr;
    skid1_pc                   <= next_skid1_pc;
    skid2_valid                <= next_skid2_valid;
    skid2_instr                <= next_skid2_instr;
    skid2_pc                   <= next_skid2_pc;
    prev_jump_count            <= jump_count;
    prev_pc                    <= pc;
    prev_pending_count         <= pending_count;

    if (error_skid_overflow)
        $display("ERROR %t: Skid buffer overflow in cpu_ifetch", $time);
    if (error_skid_underflow)
        $display("ERROR %t: Skid buffer underflow in cpu_ifetch", $time);
    // synthesis translate_off
    if (p4_jump && p4_jump_address==32'h0) begin
        $display("INFO %t: Simulation completed", $time);
        $finish;
    end
    // synthesis translate_on
end


wire unused_ok = &{1'b0, ifetch_icache_rtag[8:3]};

endmodule