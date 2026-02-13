`timescale 1ns/1ns

// RAM to hold Z buffer for a scanline (Allow for 1024 pixels)
// Double-buffer the RAM, so one buffer is being processed while the other is 
// being cleared for the next scanline.

module vga2_zbuffer_ram(
    input  logic         clock,
    input  logic         reset,
    
    // Control signals
    input  logic         start_of_line,    // Pulsed at start of each scanline

    // Read interface (one cycle latency)
    input  logic         read_enable,      // Read enable
    input  logic [9:0]   chkz_x,           // Pixel X coordinate
    output logic [11:0]  fetched_z,        // Current Depth value

    // Write interface
    input  logic         zbuf_write,       // Write enable
    input  logic [9:0]   zbuf_x_write,     // Pixel X coordinate
    input  logic [11:0]  zbuf_wdata        // Depth value
);

logic [11:0] zbuffer_ram_0 [0:1023];
logic [11:0] zbuffer_ram_1 [0:1023];
logic        current_buffer, next_current_buffer;
logic [9:0]  erase_x, next_erase_x;
logic        erase_active, next_erase_active;

logic [9:0]  ram0_read_addr;
logic [9:0]  ram0_write_addr;
logic [11:0] ram0_wdata;
logic        ram0_write_enable;
logic [11:0] ram0_rdata;
logic [9:0]  ram1_read_addr;
logic [9:0]  ram1_write_addr;
logic [11:0] ram1_wdata;
logic        ram1_write_enable;
logic [11:0] ram1_rdata;

integer i;
initial begin
    for(i=0; i<1024; i=i+1) begin
        zbuffer_ram_0[i] = 12'hFFF;
        zbuffer_ram_1[i] = 12'hFFF;
    end
end


always_comb begin
    next_current_buffer = current_buffer;
    next_erase_active   = erase_active;
    next_erase_x        = erase_x;

    if (start_of_line) begin
        next_current_buffer = ~current_buffer;
        next_erase_active   = 1'b1;
        next_erase_x        = 10'd0;
    end else if (erase_active) begin
        // Continue erasing
        next_erase_x = erase_x + 10'd1;
        if (erase_x == 10'd1023) 
            next_erase_active = 1'b0;
    end

    // Route write/read enables and addresses
    if (current_buffer == 1'b0) begin
        // Using buffer0, erasing buffer1
        ram0_write_enable = zbuf_write;
        ram0_write_addr   = zbuf_x_write;
        ram0_wdata        = zbuf_wdata;
        ram0_read_addr    = chkz_x;
        fetched_z         = ram0_rdata;
        ram1_write_enable = erase_active;
        ram1_write_addr   = erase_x;
        ram1_wdata        = 12'hFFF;   // Far depth
		  ram1_read_addr    = 10'bx;
    end else begin
        // Using buffer1, erasing buffer0
        ram1_write_enable = zbuf_write;
        ram1_write_addr   = zbuf_x_write;
        ram1_wdata        = zbuf_wdata;
        ram1_read_addr    = chkz_x;
        fetched_z         = ram1_rdata;
        ram0_write_enable = erase_active;
        ram0_write_addr   = erase_x;
		  ram0_read_addr    = 10'bx;
        ram0_wdata        = 12'hFFF;   // Far depth
    end
    
    if (reset) begin
        next_current_buffer = 1'b0;
        next_erase_active   = 1'b0;
        next_erase_x        = 10'd0;
    end
end


always_ff @(posedge clock) begin
    current_buffer <= next_current_buffer;
    erase_active   <= next_erase_active;
    erase_x        <= next_erase_x;

    if (ram0_write_enable)
        zbuffer_ram_0[ram0_write_addr] <= ram0_wdata;
    if (read_enable)
        ram0_rdata <= zbuffer_ram_0[ram0_read_addr];
    
    if (ram1_write_enable)
        zbuffer_ram_1[ram1_write_addr] <= ram1_wdata;
    if (read_enable)
        ram1_rdata <= zbuffer_ram_1[ram1_read_addr];
end

endmodule