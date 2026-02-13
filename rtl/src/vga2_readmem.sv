`timescale 1ns/1ns

// Simple read cache for SDRAM reads

module vga2_readmem(
    input  logic         clock,
    input  logic         reset,

    // Request interface
    input  logic         memread_valid,   // A transaction is ready to be processed
    output logic         memread_ready,   // We can accept a req_valid
    input  logic [4:0]   memread_mode,    // 1 = memory access, 0 = pass-through
    input  logic [25:0]  memread_addr,    // Address to read
    input  logic [9:0]   memread_x,       // Pixel X coordinate
    input  logic [11:0]  memread_z,       // Z value

    // Response interface (note nothing in pipeline from here onward can stall - so no ready signals)
    output logic         palette_valid,   // We have completed the req_valid
    output logic [4:0]   palette_mode,    // Mode (passed through)
    output logic [23:0]  palette_data,    // Either pixel data or pass-through address
    output logic [9:0]   palette_x,       // Destination address (passed through)
    output logic [11:0]  palette_z,       // Destination address (passed through)

    // SDRAM interface (externally wired to always read bursts)
    output logic         sdram_request,  // Request memory
    input  logic         sdram_ready,    // SDRAM is ready for a req_valid
    output logic [25:0]  sdram_address,  // Address

    input  logic         sdram_rvalid,   // Read data cache_valid
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


logic          req_valid;       // Do we have a req_valid in progress
logic [25:0]   req_address;     // Address of request in progress
logic [4:0]    req_mode;        // Mode of request in progress

logic [31:0]   ram [0:511];     // 512 word cache RAM
logic [14:0]   tags [0:31];     // 32 tags
logic [31:0]   cache_valid, next_cache_valid;         // Valid bits for each cache line
logic [8:0]    lookup_address;  // Address to lookup in cache RAM
logic          prev_sdram_ready;
 
logic [31:0]   fetched_data;    // Data read from cache RAM (one cycle latency)
logic [14:0]   fetched_tag;     // Tag read from tag RAM (one cycle latency)

logic          prev_sdram_request;
logic [25:0]   prev_sdram_address;
logic          request_in_progress, prev_request_in_progress;

assign palette_mode = req_mode;

always_comb begin
    // Defaults
    next_cache_valid   = cache_valid;
    request_in_progress = prev_request_in_progress;
    sdram_address    = prev_sdram_address;

    // Has the SDRAM consumed a req_valid from last cycle?
    if (prev_sdram_ready) 
        sdram_request = 1'b0;
    else 
        sdram_request = prev_sdram_request;

    // Check for the transaction in progress
    if (!req_valid) begin
        // No req_valid in progress
        palette_valid   = 1'b0;
        palette_data    = 24'hx;
    end else if (req_mode[0]==1'b1) begin
        // Have a solid color pixel. Just pass through
        palette_valid   = 1'b1;
        palette_data    = req_address[23:0];     // pass through address as data
    end else if (req_address[25:11]==fetched_tag && cache_valid[req_address[10:6]]) begin
        // Have a cache hit
        palette_valid   = 1'b1;
        palette_data    = req_address[1:0]==2'b00 ? {16'h0,fetched_data[7:0]} :
                          req_address[1:0]==2'b01 ? {16'h0,fetched_data[15:8]} :
                          req_address[1:0]==2'b10 ? {16'h0,fetched_data[23:16]} :
                                                    {16'h0,fetched_data[31:24]} ;
    end else if (sdram_rvalid && (sdram_raddress[25:2] == req_address[25:2])) begin
        // Request completed from SDRAM
        palette_valid   = 1'b1;
        palette_data    = req_address[1:0]==2'b00 ? {16'h0,sdram_rdata[7:0]} :
                          req_address[1:0]==2'b01 ? {16'h0,sdram_rdata[15:8]} :
                          req_address[1:0]==2'b10 ? {16'h0,sdram_rdata[23:16]} :
                                                    {16'h0,sdram_rdata[31:24]} ;
    end else if ( !request_in_progress) begin
        // Need to make a new request to the sdram
        sdram_request = 1'b1;
        sdram_address = req_address;
        next_cache_valid[sdram_address[10:6]] = 1'b0; // Invalidate line being read
        request_in_progress = 1'b1;
    end else begin
        // No data yet
        palette_valid   = 1'b0;
        palette_data    = 24'hx;
    end
    
    // Have we completed a burst from SDRAM?
    if (sdram_complete) begin
        next_cache_valid[sdram_address[10:6]] = 1'b1;
        request_in_progress = 1'b0;
    end

    // Can we accept a new req_valid?
    memread_ready = !req_valid || palette_valid;

    if (memread_ready)
        lookup_address = memread_addr[10:2];
    else
        lookup_address = req_address[10:2];


    // reset
    if (reset) begin
        next_cache_valid = 32'b0;
        sdram_request = 1'b0;
        sdram_address = 26'b0;
        request_in_progress = 1'b0;
    end
end

always_ff @(posedge clock) begin

    fetched_data <= ram[lookup_address];
    fetched_tag  <= tags[lookup_address[8:4]];
    prev_sdram_request <= sdram_request;
    prev_sdram_address <= sdram_address;
    cache_valid <= next_cache_valid;
    prev_sdram_ready <= sdram_ready;
    prev_request_in_progress <= request_in_progress;

    // Fetch new transaction
    if (memread_ready) begin
        req_valid <= memread_valid;
        req_address <= memread_addr;
        req_mode  <= memread_mode;
        palette_z <= memread_z;
        palette_x <= memread_x;
    end

    // load data from SDRAM into cache RAM
    if (sdram_rvalid)
        ram[sdram_raddress[10:2]] <= sdram_rdata;
    if (sdram_complete && sdram_rvalid)
        tags[sdram_raddress[10:6]] <= sdram_raddress[25:11];

    // Resets
    if (reset) begin
        req_valid <= 1'b0;
        cache_valid <= 32'b0;
    end
end

wire unused_ok = &{1'b0, sdram_raddress[1:0]}; // suppress unused signal warnings

endmodule