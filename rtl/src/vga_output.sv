`timescale 1ns/1ps

module vga_output(
    input  logic clock,             // 125MHz system clock
    input  logic reset,             // Active high reset

    // Interface to SDRAM (externally wired to always read bursts)
    output logic         vga_sdram_request,    // Request memory
    input  logic         vga_sdram_ready,      // Access granted
    output logic [25:0]  vga_sdram_address,    // Address
    input  logic         vga_sdram_rvalid,     // Read data valid
    input  logic [31:0]  vga_sdram_rdata,      // Read data
    input  logic         vga_sdram_complete,   // Burst complete

    // Interface to VGA PINS
	output logic      	VGA_CLK,    // 25MHz pixel clock
	output logic [7:0]	VGA_R,
	output logic [7:0]	VGA_G,
	output logic [7:0]	VGA_B,
	output logic      	VGA_HS,     // Horizontal sync
	output logic      	VGA_VS,     // Vertical sync
	output logic      	VGA_SYNC_N, 
    output logic      	VGA_BLANK_N,
    
    // Mouse coordinates
    input  logic [9:0]  mouse_x,
    input  logic [9:0]  mouse_y
);


assign VGA_SYNC_N = 1'b1;     // Not used
assign VGA_BLANK_N = 1'b1;    // Not used
assign VGA_CLK = !clk_div[1]; // 25MHz pixel clock

// VGA 640x480 @ 60Hz timings
localparam H_VISIBLE  = 640;
localparam H_FRONT    = 16;
localparam H_SYNC     = 96;
localparam H_BACK     = 48;
localparam H_TOTAL    = H_VISIBLE + H_FRONT + H_SYNC + H_BACK; // 800

localparam V_VISIBLE  = 480;
localparam V_FRONT    = 10;
localparam V_SYNC     = 2;
localparam V_BACK     = 33;
localparam V_TOTAL    = V_VISIBLE + V_FRONT + V_SYNC + V_BACK; // 525

localparam FRAME_BUFFER_START = 26'h3f80000; // Start of frame buffer in SDRAM
localparam FRAME_BUFFER_END   = FRAME_BUFFER_START + (H_VISIBLE * V_VISIBLE); // End of frame buffer in SDRAM

logic [2:0]  clk_div;
logic [9:0]  pos_x;
logic [9:0]  pos_y;
logic [25:0] sdram_address;

logic         request_in_progress;
logic [31:0]  fifo [0:63]; // 64 word FIFO
logic [5:0]   write_ptr;
logic [5:0]   read_ptr;
wire  [5:0]   fifo_free = read_ptr - write_ptr -1'b1;
logic [31:0]  current_word;
logic [7:0]   current_byte;

reg [15:0] mouse_image[15:0];
initial begin
    mouse_image[0]  = 16'b1000000000000000;
    mouse_image[1]  = 16'b1100000000000000;
    mouse_image[2]  = 16'b1110000000000000;
    mouse_image[3]  = 16'b1111000000000000;
    mouse_image[4]  = 16'b1111100000000000;
    mouse_image[5]  = 16'b1111110000000000;
    mouse_image[6]  = 16'b1111111000000000;
    mouse_image[7]  = 16'b1111111100000000;
    mouse_image[8]  = 16'b1111111110000000;
    mouse_image[9]  = 16'b1111111111000000;
    mouse_image[10] = 16'b1111110000000000;
    mouse_image[11] = 16'b1110011000000000;
    mouse_image[12] = 16'b1100011000000000;
    mouse_image[13] = 16'b1000001100000000;
    mouse_image[14] = 16'b0000001100000000;
    mouse_image[15] = 16'b0000000110000000;
end
reg [15:0] mouse_image_line;
reg        mouse_image_bit;

// Horizontal and vertical counters
always_ff @(posedge clock) begin

    // Pixel counters
    clk_div <= clk_div + 1'b1;
    if (clk_div == 4) begin
        clk_div <= 0;
        pos_x <= pos_x + 1'b1;
        if (pos_x == H_TOTAL-1) begin
            pos_x <= 0;
            pos_y <= pos_y + 1'b1;
            if (pos_y == V_TOTAL-1) begin
                pos_y <= 0;
            end
        end
    end

    VGA_HS <= ~(pos_x >= (H_VISIBLE + H_FRONT) && pos_x < (H_VISIBLE + H_FRONT + H_SYNC));
    VGA_VS <= ~(pos_y >= (V_VISIBLE + V_FRONT) && pos_y < (V_VISIBLE + V_FRONT + V_SYNC));

    // Request new data from SDRAM when FIFO has space
    if (!request_in_progress && sdram_address!=FRAME_BUFFER_END && fifo_free>16) begin
        vga_sdram_request <= 1'b1;
        vga_sdram_address <= sdram_address;
        request_in_progress <= 1'b1;
    end 

    if (vga_sdram_ready) begin
        vga_sdram_request <= 1'b0;
    end

    if (vga_sdram_complete) begin
        request_in_progress <= 1'b0;
        sdram_address <= sdram_address + 26'd64; // Next burst
    end

    if (vga_sdram_rvalid) begin
        fifo[write_ptr] <= vga_sdram_rdata;
        write_ptr <= write_ptr + 1'b1;
    end

    // Read data from FIFO for display
    // verilator lint_off BLKSEQ
    current_word <= fifo[read_ptr];
    current_byte = (pos_x[1:0]==2'd3) ? current_word[31:24] :
                   (pos_x[1:0]==2'd2) ? current_word[23:16] :
                   (pos_x[1:0]==2'd1) ? current_word[15:8]  :
                                        current_word[7:0];
    if (clk_div == 0 && pos_x[1:0] == 2'd3 && pos_x<H_VISIBLE && pos_y<V_VISIBLE) begin
        read_ptr <= read_ptr + 1'b1;
    end

    // In visible area
    VGA_R <= current_byte; // Grayscale
    VGA_G <= current_byte;
    VGA_B <= current_byte;

    // Overlay mouse cursor
    mouse_image_line <= mouse_image[pos_y[3:0] - mouse_y[3:0]];
    if (pos_y>=mouse_y && pos_y<mouse_y+16 && pos_x >=mouse_x && pos_x<mouse_x+16) begin
        mouse_image_bit = mouse_image_line[15 - (pos_x - mouse_x)];
        if (mouse_image_bit) begin
            VGA_R <= 8'hff;
            VGA_G <= 8'hff;
            VGA_B <= 8'hff;
        end
    end

    // Outside visible area
    if (pos_x >= H_VISIBLE || pos_y >= V_VISIBLE) begin
        VGA_R <= 8'b0;
        VGA_G <= 8'b0;
        VGA_B <= 8'b0;
    end

    // Reset just before start of frame
    if (clk_div == 4 && pos_x == H_TOTAL-128 && pos_y == V_TOTAL-1) begin
        sdram_address <= FRAME_BUFFER_START;
        read_ptr <= 0;
        write_ptr <= 0;
    end    

    // reset
    if (reset) begin
        clk_div <= 0;
        pos_x <= 0;
        pos_y <= V_TOTAL-1; // Start just before start of frame
        vga_sdram_request <= 1'b0;
        request_in_progress <= 1'b0;
        sdram_address <= FRAME_BUFFER_START;
        write_ptr <= 0;
        read_ptr <= 0;
    end 
end

endmodule
