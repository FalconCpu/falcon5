`timescale 1ns/1ns

// Simple read cache for audio channels

module audio_readmem(
    input  logic         clock,
    input  logic         reset,

    // Request interface
    input  logic         mem_request,   // A transaction is ready to be processed
    input  logic         mem_write,     // write transaction
    input  logic [25:0]  mem_address,   // Address to read
    output logic         mem_valid,     // We have completed the request
    output logic [15:0]  mem_data,      // Data read or passed through
    input  logic [2:0]   current_channel, // Current audio channel (0-7)

    // SDRAM interface (externally wired to always read bursts)
    output logic         sdram_request,  // Request memory
    output logic         sdram_write,
    input  logic         sdram_ready,    // SDRAM is ready for a request
    output logic [25:0]  sdram_address,  // Address

    input  logic         sdram_rvalid,   // Read data valid
    input  logic [25:0]  sdram_raddress, // Address of read data
    input  logic [31:0]  sdram_rdata,    // Read data
    input  logic         sdram_complete  // Burst complete
);

// Hold a buffer of 64 bytes (16 words)
logic [31:0] ram[0:15][0:7]; // 16 words of 4 bytes
logic [25:0] ram_tag[0:7]; // Address of current buffer
logic        next_mem_valid;
logic [1:0]  addr_lab, next_addr_lab;
logic        next_sdram_request;  // Request memory
logic [25:0] next_sdram_address;  // Address
logic        next_sdram_write;
logic        transaction_in_progress, next_transaction_in_progress;



logic [31:0] mem_read_data;

always_comb begin
    next_sdram_address = sdram_address;
    next_sdram_request = sdram_request;
    next_mem_valid = 1'b0;
    next_transaction_in_progress = transaction_in_progress;
    next_addr_lab = addr_lab;
    next_sdram_write = sdram_write;

    // Clear request when SDRAM is ready
    if (sdram_ready)
        next_sdram_request = 1'b0;

    // Output data read last cycle. 
    // Convert from 8-bit to 16-bit samples
    if (mem_valid)
        case (addr_lab)
            2'b00: mem_data = {mem_read_data[7:0],mem_read_data[7:0]};
            2'b01: mem_data = {mem_read_data[15:8],mem_read_data[15:8]};
            2'b10: mem_data = {mem_read_data[23:16],mem_read_data[23:16]};
            2'b11: mem_data = {mem_read_data[31:24],mem_read_data[31:24]};
            default: mem_data = 16'hx;
        endcase
    else
        mem_data = 16'hx;

    // Process the incoming request
    if (mem_request) begin
        if (mem_write) begin
            next_sdram_request = 1;
            next_sdram_address = mem_address;
            next_sdram_write = 1'b1;
        end else if (mem_address[25:6] == ram_tag[current_channel][25:6]) begin
            // Hit in cache
            next_addr_lab = mem_address[1:0];
            next_mem_valid = 1;
        end else if (transaction_in_progress==0) begin
            // Miss - request new data from SDRAM
            next_sdram_address = {mem_address[25:6], 6'b0};
            next_sdram_write = 1'b0;
            next_sdram_request = 1;
            next_transaction_in_progress = 1;
        end
    end
    
    if (sdram_complete) 
        next_transaction_in_progress = 0;

    if (reset) begin
        next_mem_valid = 0;
        next_addr_lab = 0;
        next_sdram_request = 0;
        next_sdram_address = 0;
        next_transaction_in_progress = 0;
    end
end

integer i;

always_ff @(posedge clock) begin
    mem_valid <= next_mem_valid;
    addr_lab <= next_addr_lab;
    sdram_request <= next_sdram_request;
    sdram_address <= next_sdram_address;
    sdram_write <= next_sdram_write;
    transaction_in_progress <= next_transaction_in_progress;
    if (sdram_rvalid)    // Store incoming data from SDRAM into RAM
        // verilator lint_off BLKSEQ
        ram[sdram_raddress[5:2]][current_channel] = sdram_rdata;
    if (sdram_complete)  // Completed the burst read
        ram_tag[current_channel] <= {sdram_raddress[25:6],6'b0};
    mem_read_data <= ram[mem_address[5:2]][current_channel];

    if (reset)
        for (i=0; i<8; i=i+1)
            ram_tag[i] <= 0;
end

wire unused_ok = &{ 1'b0, mem_address[0], sdram_raddress[1:0]};


endmodule