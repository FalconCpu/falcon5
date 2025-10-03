`timescale 1ns/1ns

// A simple data cache module
// This is a direct mapped cache with a write-back policy.
// The cache line size is 64 bytes (16 words) to match the SDRAM burst read.
// The cache size is 256 sets * 64 bytes/line = 16KB.

module data_cache(
    input logic          clock,
    input logic          reset,

    // CPU interface
    output logic         cpu_dcache_ready,      // The cache is ready to accept a new request
    input logic          cpu_dcache_request,    // The CPU is making a request to a cacheable address
    input logic [25:0]   cpu_dcache_addr,       // The address of the request
    input logic          cpu_dcache_write,      // Read (0) or write (1)
    input logic [3:0]    cpu_dcache_wstrb,      // Byte enables for a write 
    input logic [31:0]   cpu_dcache_wdata,      // The data to write. Tag for read.
    output logic         cpu_dcache_rvalid,     // Read data is availible
    output logic [31:0]  cpu_dcache_rdata,      // The read data.
    output logic [8:0]   cpu_dcache_tag,        // The tag of the read data.
    input  logic         cpu_dcache_abort,     // The memory instruction issued last cycle needs to be aborted (e.g. failed MPU check)

    // SDRAM interface
    input  logic         dcache_sdram_ready,    // The sdram is ready to accept a new request
    output logic         dcache_sdram_request,  // The cache is making a request to sdram
    output logic [25:0]  dcache_sdram_addr,     // The address of the request
    output logic         dcache_sdram_write,    // Read (0) or write (1)
    output logic         dcache_sdram_burst,    // 1=16-beat burst, 0=single
    output logic [3:0]   dcache_sdram_wstrb,    // Byte enables for a write 
    output logic [31:0]  dcache_sdram_wdata,    // The data to write. Tag for read.
    input  logic         dcache_sdram_rvalid,   // Read data is availible
    input  logic [31:0]  dcache_sdram_rdata,    // The read data.
    input  logic [25:0]  dcache_sdram_raddress, // The address of the read data.
    input  logic         dcache_sdram_complete  // Set for final beat of burst
);

// Address breakdown
// 26-bit address from CPU
// [25:14] Tag (12 bits)
// [13:6]  Index (8 bits) - 256 sets
// [5:0]   Offset (6 bits) - 16 words, 64 bytes per cache line

// The cache memory
logic [31:0]  cache_data   [0:255][0:15]; // The cache data
logic [12:0]  cache_tag    [0:255];  // valid, tag

// Latch the request from the cpu
logic          cpu_request;     // The CPU is making a request to a cacheable address
logic [25:0]   cpu_addr;        // The address of the request
logic          cpu_write;       // Read (0) or write (1)
logic [3:0]    cpu_wstrb;       // Byte enables for a write 
logic [31:0]   cpu_wdata;       // The data to write. Tag for read.

// Read interface to cache ram
logic [7:0]  next_fetch_index;  // The set of the next tag to fetch
logic [3:0]  next_fetch_offset; // The word offset within the cache line of the next tag to fetch
logic [11:0] fetch_tag;         // The tag fetched from the cache
logic [31:0] fetch_data;        // The data of the next tag to fetch
logic        fetch_valid;       // The valid bit of the next tag to fetch

// Write interface to cache ram
logic        write_enable;      // Perform a write to the cache
logic [7:0]  write_index;       // The set of the tag to write
logic [3:0]  write_offset;      // The word offset within the cache line of the tag to write
logic [31:0] write_data;        // The data to write
logic [11:0] write_tag;         // The tag to write
logic        write_valid;       // The valid bit to write

// Hold the previous values
logic         next_dcache_sdram_request;
logic [25:0]  next_dcache_sdram_addr;
logic         next_dcache_sdram_write;
logic         next_dcache_sdram_burst;
logic [3:0]   next_dcache_sdram_wstrb;
logic [31:0]  next_dcache_sdram_wdata;

// Parse the incoming CPU address
logic [11:0]  cpu_tag;
logic [7:0]   cpu_index;
logic [3:0]   cpu_word_offset;   // Word offset within the cache line
logic [1:0]   cpu_byte_offset;  // Byte offset within the word

logic [31:0]  prev_sdram_data;
logic         prev_sdram_hit, sdram_hit;    // True if the last SDRAM read was for the current fetch address

logic cache_hit;

localparam STATE_READY= 3'h0,
           STATE_WRITEBACK= 3'h1,
           STATE_REFILL= 3'h2,
           STATE_RESET= 3'h3,
           STATE_ACCEPT= 3'h4;
logic [2:0] state, next_state;
logic [7:0]   reset_index, next_reset_index;

always_comb begin
    // Default outputs
    cpu_dcache_ready      = 1'b0;
    cpu_dcache_rvalid     = 1'b0;
    cpu_dcache_rdata      = 32'bx;
    cpu_dcache_tag        = 9'bx;
    next_dcache_sdram_request  = dcache_sdram_request;
    next_dcache_sdram_addr     = dcache_sdram_addr;
    next_dcache_sdram_write    = dcache_sdram_write;
    next_dcache_sdram_burst    = dcache_sdram_burst;
    next_dcache_sdram_wstrb    = dcache_sdram_wstrb;
    next_dcache_sdram_wdata    = dcache_sdram_wdata;
    next_state            = state;
    write_enable          = 1'b0;
    write_index           = 8'bx;
    write_offset          = 4'bx;
    write_data            = 32'bx; 
    write_tag             = 12'bx;
    write_valid           = 1'bx;
	next_reset_index      = reset_index;

    // Clear the SDRAM request if it has been accepted
    if (dcache_sdram_ready) begin    // Has the SDRAM received the request?
        next_dcache_sdram_request = 1'b0; // Clear the CPU request to prevent latching a new one
        next_dcache_sdram_addr = 'x;
        next_dcache_sdram_write = 'x;
        next_dcache_sdram_wstrb = 'x;
        next_dcache_sdram_wdata = 'x;
    end

    // Break down the CPU address into its components
    {cpu_tag, cpu_index, cpu_word_offset, cpu_byte_offset} = cpu_addr;
    next_fetch_index      = cpu_index;      // Default to fetching the current index
    next_fetch_offset     = cpu_word_offset;

    // See if we have a cache hit
    cache_hit = cpu_request && cpu_tag==fetch_tag && fetch_valid;
    sdram_hit = dcache_sdram_raddress[25:2]==cpu_addr[25:2] && dcache_sdram_rvalid;

    case(state) 
        STATE_READY: begin
            if (cpu_request==1'b0 || cpu_dcache_abort) begin
                // No request, stay in ready state
                cpu_dcache_ready = 1'b1;        
                next_state = STATE_READY;

            end else if (cpu_write) begin
                // On a write - update the cache line if applicable, and send the write to SDRAM
                if (cache_hit) begin
                    write_enable = 1'b1;
                    write_index  = cpu_index;
                    write_offset = cpu_word_offset;
                    write_data[7:0]   = cpu_wstrb[0] ? cpu_wdata[7:0]   : fetch_data[7:0];
                    write_data[15:8]  = cpu_wstrb[1] ? cpu_wdata[15:8]  : fetch_data[15:8];
                    write_data[23:16] = cpu_wstrb[2] ? cpu_wdata[23:16] : fetch_data[23:16];
                    write_data[31:24] = cpu_wstrb[3] ? cpu_wdata[31:24] : fetch_data[31:24];
                    write_tag    = fetch_tag;
                    write_valid  = 1'b1;
                end
                if (dcache_sdram_request==1'b0) begin
                    // Send the write to SDRAM
                    next_dcache_sdram_request = 1'b1;
                    next_dcache_sdram_addr    = cpu_addr & 26'h3fffffc; // Align to word
                    next_dcache_sdram_write   = 1'b1; // Write operation
                    next_dcache_sdram_burst   = 1'b0; // Single write
                    next_dcache_sdram_wstrb   = cpu_wstrb; // Byte enables
                    next_dcache_sdram_wdata   = cpu_wdata; // Write data
                    cpu_dcache_ready = 1'b1;               // We are ready for the next transaction
                end

                next_state = STATE_READY;

            end else if (cache_hit) begin
                // Got a read hit, return the data and stay in ready state
                cpu_dcache_rvalid = 1'b1;            // Read data is valid
                cpu_dcache_rdata  = fetch_data;      // Return the data from the cache
                cpu_dcache_tag    = cpu_wdata[8:0];  // Return the tag from the request

                cpu_dcache_ready = 1'b1;           // We are ready for the next transaction
                next_state        = STATE_READY;

            end else if (dcache_sdram_request==1'b0) begin
                // Read miss -  go to refill
                next_dcache_sdram_addr    = {cpu_tag, cpu_index, cpu_word_offset, 2'b0}; // Calculate the address to read
                next_dcache_sdram_request = 1'b1;
                next_dcache_sdram_write   = 1'b0; // Read operation
                next_dcache_sdram_burst   = 1'b1; // 16-beat burst
                next_dcache_sdram_wstrb   = 4'b0000; // No write
                next_dcache_sdram_wdata   = 32'bx; // No write data
                next_state                = STATE_REFILL;
            end
        end

        STATE_REFILL: begin
            if (dcache_sdram_rvalid) begin    // Write the fetched data to the CPU
                write_enable     = 1'b1;
                write_index      = dcache_sdram_raddress[13:6];
                write_offset     = dcache_sdram_raddress[5:2];
                write_data       = dcache_sdram_rdata;
                write_tag        = dcache_sdram_raddress[25:14];
                write_valid      = dcache_sdram_complete;

                if (cpu_dcache_abort) begin
                    // The request has been aborted - do not return data to the CPU
                    cpu_dcache_ready  = 1'b1;       // The cache is ready for the next transaction
                end else if (prev_sdram_hit && cpu_request && !cpu_write) begin
                    // This is the word the CPU is waiting for
                    cpu_dcache_rvalid = 1'b1;       // Read data is valid
                    cpu_dcache_rdata  = prev_sdram_data; // Return the data from the sdram
                    cpu_dcache_tag    = cpu_wdata[8:0]; // Return the tag from the request
                    cpu_dcache_ready  = 1'b1;       // The cache is ready for the next transaction
                end
            end
            if (dcache_sdram_complete)
                next_state = STATE_ACCEPT;
        end


        STATE_RESET: begin
            cpu_dcache_ready = 1'b0; // Not ready to accept new requests
            write_enable = 1'b1;
            write_index  = reset_index;
            write_valid = 1'b0;
            write_data  = 32'bx;       
            write_tag   = 12'bx;
            if (reset_index==8'hff) begin
                next_state = STATE_READY;
                cpu_dcache_ready = 1'b1; // Now ready to accept new requests
            end 
            next_reset_index = reset_index + 1'b1;
        end

        STATE_ACCEPT: begin
            // This state is just to provide a one-cycle delay to accept a new transaction
            next_state = STATE_READY;
        end

        default: begin
            next_state = STATE_READY;
        end
    endcase

    // If we are loading a new transaction from the CPU - do a lookahead fetch from the cache
    if (cpu_dcache_ready) begin
        next_fetch_index  = cpu_dcache_addr[13:6];
        next_fetch_offset = cpu_dcache_addr[5:2];
    end

    // Reset
    if (reset) begin
        next_reset_index = 0;
        next_state = STATE_RESET;
        next_dcache_sdram_request = 1'b0;
        cpu_dcache_ready = 1'b0;
    end
end



always_ff @(posedge clock) begin

    // Latch the CPU request if the cache is ready
    if (reset) begin
        cpu_request <= 1'b0;
        cpu_addr    <= 26'bx;
        cpu_write   <= 1'bx;
        cpu_wstrb   <= 4'bx;
        cpu_wdata   <= 32'bx;
    end else if (cpu_dcache_ready) begin
        cpu_request <= cpu_dcache_request;
        cpu_addr    <= cpu_dcache_addr;
        cpu_write   <= cpu_dcache_write;
        cpu_wstrb   <= cpu_dcache_wstrb;
        cpu_wdata   <= cpu_dcache_wdata;
    end

    // Latch the previous SDRAM request values
    dcache_sdram_request <= next_dcache_sdram_request;
    dcache_sdram_addr    <= next_dcache_sdram_addr;
    dcache_sdram_write   <= next_dcache_sdram_write;
    dcache_sdram_burst   <= next_dcache_sdram_burst;
    dcache_sdram_wstrb   <= next_dcache_sdram_wstrb;
    dcache_sdram_wdata   <= next_dcache_sdram_wdata;

    // Write data to the cache
    if (write_enable) begin
        cache_data[write_index][write_offset] = write_data;
        cache_tag[write_index] = {write_valid, write_tag};
    end

    // Fetch data from the cache
    {fetch_valid, fetch_tag}   <= cache_tag[next_fetch_index];
    fetch_data  <= cache_data[next_fetch_index][next_fetch_offset];


    state <= next_state;
    reset_index <= next_reset_index;
    prev_sdram_data <= dcache_sdram_rdata;
    prev_sdram_hit  <= sdram_hit;

end

wire unused_ok = &{ 1'b0, dcache_sdram_raddress[1:0], cpu_byte_offset };


endmodule