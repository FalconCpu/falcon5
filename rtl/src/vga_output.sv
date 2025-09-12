`timescale 1ns/1ps

module vga_output(
    input  logic clock,             // 125MHz system clock
    input  logic reset,             // Active high reset

    // Connection to the pixel FIFO
    output logic         new_pixel,   // New pixel available
    output logic         new_frame,   // New frame started
    input  logic [23:0]  pixel_data,  // Pixel data (rgb)

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

    new_frame <= (clk_div==0 && pos_x==H_TOTAL-128 && pos_y==V_TOTAL-1);
    mouse_image_line <= mouse_image[pos_y[3:0] - mouse_y[3:0]];

    if (clk_div==0) begin
        if (pos_x<H_VISIBLE && pos_y<V_VISIBLE) begin
            // In visible area
            VGA_R <= pixel_data[23:16]; // Grayscale
            VGA_G <= pixel_data[15:8];
            VGA_B <= pixel_data[7:0];

            // Overlay mouse cursor
            if (pos_y>=mouse_y && pos_y<mouse_y+16 && pos_x >=mouse_x && pos_x<mouse_x+16) begin
                // verilator lint_off BLKSEQ
                mouse_image_bit = mouse_image_line[15 - (pos_x - mouse_x)];
                if (mouse_image_bit) begin
                    VGA_R <= 8'hff;
                    VGA_G <= 8'hff;
                    VGA_B <= 8'hff;
                end
            end
            new_pixel <= 1'b1;
        end else begin
            // Outside visible area
            VGA_R <= 8'b0;
            VGA_G <= 8'b0;
            VGA_B <= 8'b0;
        end
        new_pixel <= (pos_x<H_VISIBLE && pos_y<V_VISIBLE);
    end else begin
        new_pixel <= 1'b0;
        
    end

    // reset
    if (reset) begin
        clk_div <= 0;
        pos_x <= 0;
        pos_y <= V_TOTAL-1; // Start just before start of frame
    end 
end

endmodule
