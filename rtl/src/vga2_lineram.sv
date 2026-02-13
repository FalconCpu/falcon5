`timescale 1ns/1ns

// RAM to hold output pixel data for a scanline (Allow for 1024 pixels)
// Double-buffer the RAM, so one buffer is being processed while the other is 
// being filled with new pixel data. Erase the buffer as its is displayed ready 
// for the next scanline.

module vga2_lineram(
    input  logic         clock,
    input  logic         reset,

    input  logic         start_of_line,      // Pulsed at start of each scanline

    // Write interface
    input  logic         lineram_write,      // Write enable
    input  logic [9:0]   lineram_write_addr,       // Address to write
    input  logic [23:0]  lineram_wdata,      // Data to write

    // Read interface
    input  logic         lineram_read,       // Read enable
    input  logic [9:0]   lineram_read_addr,  // Address to read
    output logic [23:0]  lineram_rdata       // Data read
);

logic [23:0] lineram_0 [0:1023];
logic [23:0] lineram_1 [0:1023];
logic        current_buffer, next_current_buffer;
logic        prev_lineram_read;

logic        ram0_read_enable;
logic [9:0]  ram0_read_addr;
logic [23:0] ram0_rdata;
logic [9:0]  ram0_write_addr;
logic [23:0] ram0_wdata;
logic        ram0_write_enable;

logic        ram1_read_enable;
logic [9:0]  ram1_read_addr;
logic [23:0] ram1_rdata;
logic [9:0]  ram1_write_addr;
logic [23:0] ram1_wdata;
logic        ram1_write_enable;

// synthesis translate_off
integer i;
initial begin
    for (i=0; i<1024; i=i+1) begin
        lineram_0[i] = 24'd0;
        lineram_1[i] = 24'd0;
    end
end
// synthesis translate_on


always_comb begin
    next_current_buffer = current_buffer;

    // Route read/write enables and addresses
    if (current_buffer == 1'b0) begin
        // Using buffer0, writing to buffer1
        ram0_read_enable   = lineram_read;
        ram0_read_addr     = lineram_read_addr;
        ram0_write_enable  = prev_lineram_read;
        ram0_write_addr    = lineram_read_addr;
        ram0_wdata         = 24'd0;

        ram1_read_enable   = 1'b0;
        ram1_read_addr     = 10'dx;
        ram1_write_enable  = lineram_write;
        ram1_write_addr    = lineram_write_addr;
        ram1_wdata         = lineram_wdata;
    end else begin
        // Using buffer1, writing to buffer0
        ram0_read_enable   = 1'b0;
        ram0_read_addr     = 10'dx;
        ram0_write_enable  = lineram_write;
        ram0_write_addr    = lineram_write_addr;
        ram0_wdata         = lineram_wdata;

        ram1_read_enable   = lineram_read;
        ram1_read_addr     = lineram_read_addr;
        ram1_write_enable  = prev_lineram_read;
        ram1_write_addr    = lineram_read_addr;
        ram1_wdata         = 24'd0;
    end

    // Output read data
    if (current_buffer == 1'b0)
        lineram_rdata = ram0_rdata;
    else
        lineram_rdata = ram1_rdata;

    // Switch buffers at start of line
    if (start_of_line)
        next_current_buffer = ~current_buffer;

    if (reset) begin
        next_current_buffer = 1'b0;
    end
end


always_ff @(posedge clock) begin
    current_buffer <= next_current_buffer;
    prev_lineram_read <= lineram_read;

    // RAM 0 operations
    if (ram0_write_enable)
        lineram_0[ram0_write_addr] <= ram0_wdata;
    if (ram0_read_enable)
        ram0_rdata <= lineram_0[ram0_read_addr];

    // RAM 1 operations
    if (ram1_write_enable)
        lineram_1[ram1_write_addr] <= ram1_wdata;
    if (ram1_read_enable)
        ram1_rdata <= lineram_1[ram1_read_addr];
end


endmodule