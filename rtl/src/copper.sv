`timescale 1ns/1ns

// A simple Amiga-style copper implementation.
//
// The copper is a co-processor that can wait for specific
// scanline positions and then perform memory-mapped I/O
// operations, typically to change graphics hardware registers
// at precise times during the display refresh.
//
// The copper currently supports 3 instructions:
// 1. MOVE - Write a value to a specified address
// 2. WAIT - Wait for a specific scanline position
// 3. HALT - Stop execution
//
// Instruction format:
// [31:24] Opcode
// [23:0]  Operand (varies by instruction)
//
// Opcodes:
// - HALT:     8'h0:   No operands
// - WAIT_EQ:  8'h1:   [9:0] X position, [19:10] Y position
// - MOVE:     8'h2:   [23:0] Address to write to, followed by a 32-bit data word
// - WAIT_GE:  8'h3:   [9:0] X position, [19:10] Y position

module copper(
    input logic          clock,
    input logic          reset,

    // Connection to the HWREGS bus
    input logic          hwregs_copper_start,        // Signal to start the copper
    input logic [25:0]   hwregs_copper_address,      // Address of the copper program in SDRAM
    output logic         hwregs_copper_busy,         // Signal that the copper is busy

    // Connection to the SDRAM (read only)
    input  logic         copper_sdram_ready,        // SDRAM is ready to accept request
    output logic         copper_sdram_request,      // Request a read (64 byte burst)
    output logic [25:0]  copper_sdram_address,      // Address for read request
    input  logic         copper_sdram_data_valid,   // Data from SDRAM is valid
    input  logic [31:0]  copper_sdram_rdata,        // Data returned from SDRAM
    input  logic [25:0]  copper_sdram_raddress,     // Address of the data returned
    input  logic         copper_sdram_complete,     // Read burst complete

    // Connection to the AUX bus (write only)
    output logic         copper_aux_request,        // Request AUX bus access
    input logic          copper_aux_ack,            // AUX bus acknowledged the request
    output logic [23:0]  copper_aux_address,        // Address to write to on AUX bus  (has an implied 0xE0 prefix to make it 32 bits)
    output logic [31:0]  copper_aux_wdata,          // Data to write to AUX bus

    // Connection to the VGA controller
    input logic [9:0]    vga_xpos,                  // Current X position of the VGA scanner
    input logic [9:0]    vga_ypos                   // Current Y position
);

logic [25:0] pc, next_pc;                       // Program counter
logic        running, next_running;             // Is the copper running?
logic [25:0] cache_address, next_cache_address; // Cached address for SDRAM read

// SDRAM reads are always in bursts of 64bytes (16 words)
logic         next_copper_sdram_request;
logic [25:0]  next_copper_sdram_address;
logic         cache_fill_in_progress, next_cache_fill_in_progress;

logic [31:0]  cache[0:15];          // Cache for 16 words (64 bytes)
logic [31:0]  instruction;          // Current instruction
logic         instruction_valid;    // Is the current instruction valid
logic         data_cycle, next_data_cycle;
logic [23:0]  addr_reg, next_addr_reg; // Address register for MOVE instruction
logic         error;

assign hwregs_copper_busy = running;

localparam OPCODE_HALT     = 8'h00;
localparam OPCODE_WAIT_EQ  = 8'h01;
localparam OPCODE_MOVE     = 8'h02;
localparam OPCODE_WAIT_GE  = 8'h03;

wire [9:0] instr_ypos = instruction[19:10];
wire [9:0] instr_xpos = instruction[9:0];

always_comb begin
    // Default assignments
    next_pc                      = pc;
    next_running                 = running;
    next_cache_address           = cache_address;
    next_copper_sdram_request    = copper_sdram_request;
    next_copper_sdram_address    = copper_sdram_address;
    next_cache_fill_in_progress = cache_fill_in_progress;
    next_data_cycle              = data_cycle;
    copper_aux_request           = 1'b0;
    copper_aux_address           = 24'h0;
    copper_aux_wdata             = 32'h0;
    next_addr_reg                = addr_reg;
    error                        = 1'b0;

    // Clear an SDRAM request if it was accepted
    if (copper_sdram_ready)
        next_copper_sdram_request = 1'b0;

    // If a cache fill has completed, update the cache base address
    if (copper_sdram_complete) begin
        next_cache_fill_in_progress = 1'b0;
        next_cache_address = {copper_sdram_raddress[25:6],6'b0};
    end

    // If we need to fill the cache, and we're not already doing so, issue a read
    if (running && !cache_fill_in_progress && (pc[25:6] != cache_address[25:6])) begin
        next_copper_sdram_request    = 1'b1;
        next_copper_sdram_address    = {pc[25:6],6'b0};
        next_cache_fill_in_progress = 1'b1;
    end
    
    // Start the copper if requested
    if (hwregs_copper_start) begin
        next_running         = 1'b1;
        next_pc              = hwregs_copper_address;
        next_cache_address   = 26'h0;    // Invalidate cache
    
    end else if (!running || !instruction_valid) begin
        // Do nothing

    end else if (data_cycle) begin
        // Second cycle of MOVE instruction. Issue AUX write.
        copper_aux_request = 1'b1;
        copper_aux_address = addr_reg;
        copper_aux_wdata   = instruction;
        if (copper_aux_ack) begin
            next_data_cycle = 1'b0;
            next_pc = pc + 26'd4;
        end

    end else if (instruction[31:24] == OPCODE_HALT) begin
        // HALT instruction
        next_running = 1'b0;
    
    end else if (instruction[31:24] == OPCODE_WAIT_EQ) begin
        // WAIT instruction
        if ((vga_ypos == instr_ypos) && (vga_xpos == instr_xpos))
            next_pc = pc + 26'd4;

    end else if (instruction[31:24] == OPCODE_WAIT_GE) begin
        // WAIT instruction
        if ((vga_ypos > instr_ypos) || ((vga_ypos == instr_ypos) && (vga_xpos >= instr_xpos)))
            next_pc = pc + 26'd4;
    
    end else if (instruction[31:24] == OPCODE_MOVE) begin
        // MOVE instruction
        next_data_cycle = 1'b1;
        next_addr_reg   = instruction[23:0];
        next_pc         = pc + 26'd4;

    end else begin
        // Unknown instruction - halt the copper
        next_running = 1'b0;
        error = 1'b1;
    end

    

    if (reset) begin
        next_pc            = 26'h0;
        next_running       = 1'b0;
        next_cache_address = 26'h0;
        next_copper_sdram_request  = 1'b0;
        next_cache_fill_in_progress = 1'b0;
        next_data_cycle    = 1'b0;
    end
end


always_ff @(posedge clock) begin
    pc                     <= next_pc;
    running                <= next_running;
    cache_address          <= next_cache_address;
    copper_sdram_request   <= next_copper_sdram_request;
    copper_sdram_address   <= next_copper_sdram_address;
    cache_fill_in_progress <= next_cache_fill_in_progress;
    data_cycle             <= next_data_cycle;
    addr_reg               <= next_addr_reg;


    // Handle SDRAM read completion and cache filling
    if (copper_sdram_data_valid) 
        cache[copper_sdram_raddress[5:2]] <= copper_sdram_rdata;

    // Fetch instruction from cache
    instruction <= cache[next_pc[5:2]];
    instruction_valid <= (next_pc[25:6] == cache_address[25:6]) && !cache_fill_in_progress && running;

    // synthesis translate_off
    if (error) begin
        $display("Copper encountered an error at PC=%h INSTR=%h", pc, instruction);
    end
    // synthesis translate_on


end

wire unused_ok = &{ 1'b0, copper_sdram_raddress[1:0], cache_address[5:0] };

endmodule