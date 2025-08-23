`timescale 1ns/1ns

// Simple read cache for SDRAM reads

module read_cache(
    input  logic         clk,
    input  logic         reset,

    // Request interface
    input  logic         cache_request,  // Have a read request
    output logic         cache_ready,    // We can accept a request
    input  logic [25:0]  cache_address,  // Address to read

    output logic         cache_rvalid,    // Read data valid
    output logic [31:0]  cache_rdata,     // Read data
    output logic [25:0]  cache_raddress,  // Address of read data

    // SDRAM interface (externally wired to always read bursts)
    output logic         sdram_request,  // Request memory
    input  logic         sdram_ready,    // SDRAM is ready for a request
    output logic [25:0]  sdram_address,  // Address

    input  logic         sdram_rvalid,   // Read data valid
    input  logic [25:0]  sdram_raddress, // Address of read data
    input  logic [31:0]  sdram_rdata,    // Read data
    input  logic         sdram_complete  // Burst complete
);

// Cache parameters
// Line size    :  16 words (64 bytes)
// Number lines :  is 32
//
// So total cache size is 512 words (2048 bytes)
//
// Address breakdown
//  [25:11]  : Tag (15 bits)
//  [10:6]   : Line index (5 bits)
//  [5:2]    : Word in line (4 bits)
//  [1:0]    : Byte in word (2 bits, ignored)


logic          request,  next_request;    // Request in progress
logic [25:0]   address,  next_address;    // Address of request in progress
logic [31:0]   ram [0:511];               // 512 word cache RAM
logic [14:0]   tags [0:31];               // 32 tags
logic [31:0]   valid, next_valid;         // Valid bits for each line
logic          prev_sdram_ready;
 
logic [31:0]   fetched_data;              // Data read from cache RAM
logic [14:0]   fetched_tag;               // Tag read from tag RAM

logic          prev_sdram_request;
logic [25:0]   prev_sdram_address;


always_comb begin
    // Defaults
    next_request = request;
    next_address = address;
    next_valid   = valid;

    // Has the SDRAM consumed a request from last cycle?
    sdram_address = prev_sdram_address;
    if (prev_sdram_ready) begin
        sdram_request = 1'b0;
    end else begin
        sdram_request = prev_sdram_request;
    end

    // Check for cache hit
    if (request && address[25:11]==fetched_tag && valid[address[10:6]]) begin
        // Cache hit
        cache_rvalid   = 1'b1;
        cache_rdata    = fetched_data;
        cache_raddress = address;
    end else if (request && sdram_rvalid && (sdram_raddress == address)) begin
        // Request completed from SDRAM
        cache_rvalid   = 1'b1;
        cache_rdata    = sdram_rdata;
        cache_raddress = sdram_raddress;
    end else begin
        // No data yet
        cache_rvalid   = 1'b0;
        cache_rdata    = 32'h0a0a0a0a;
        cache_raddress = 26'bx;
    end
    
    // Do we need to make a new request?
    if (request && !cache_rvalid && !sdram_request && sdram_address[25:6]!=address[25:6]) begin
        sdram_request = 1'b1;
        sdram_address = address;
        next_valid[sdram_address[10:6]] = 1'b0; // Invalidate line being read
    end

    // Have we completed a burst from SDRAM?
    if (sdram_rvalid && sdram_complete) begin
        next_valid[sdram_address[10:6]] = 1'b1;
    end

    // Can we accept a new request?
    cache_ready = !request || cache_rvalid;
    if (cache_ready) begin
        next_request = cache_request;
        next_address = cache_address;
    end

    // reset
    if (reset) begin
        next_valid = 32'b0;
        next_request = 1'b0;
        next_address = 26'b0;
        sdram_request = 1'b0;
        sdram_address = 26'b0;
    end
end

always_ff @(posedge clk) begin
    request <= next_request;
    address <= next_address;
    fetched_data <= ram[next_address[10:2]];
    fetched_tag  <= tags[next_address[10:6]];
    prev_sdram_request <= sdram_request;
    prev_sdram_address <= sdram_address;
    valid <= next_valid;
    prev_sdram_ready <= sdram_ready;

    // load data from SDRAM into cache RAM
    if (sdram_rvalid)
        ram[sdram_raddress[10:2]] <= sdram_rdata;
    if (sdram_complete && sdram_rvalid)
        tags[sdram_raddress[10:6]] <= sdram_raddress[25:11];
end
endmodule