`timescale 1ns/1ps

module vga_layer #(
    logic [2:0]        LAYER_ID = 3'b000
)
(
    input logic        clock,          // 125MHz system clock
    input logic        reset,          // Active high reset 

    // Memory mapped registers (write only)
    input logic        hwregs_write,      // 1 = write
    input logic [7:0]  hwregs_addr,    // Register within module
    input logic [31:0] hwregs_wdata,     // Data to write

    // Connection to vga pipeline
    input logic [9:0]   pixel_x,       // Current pixel X coordinate
    input logic [9:0]   pixel_y,       // Current pixel Y coordinate
    input logic         next_pixel,    // Pulsed when moving to next pixel
    input logic         next_line,     // Pulsed when moving to next line
    input logic         new_frame,    // Pulsed when moving to next frame
    output logic [25:0] layer_address, // Address to read from SDRAM
    output logic        layer_active   // High when layer is active
);

// Registers
logic [25:0] reg_address;           // Base address of frame buffer in SDRAM
logic [9:0]  reg_x1;                // Layer top left X
logic [9:0]  reg_y1;                // Layer top left Y
logic [9:0]  reg_x2;                // Layer bottom right X (exclusive)
logic [9:0]  reg_y2;                // Layer bottom right Y (exclusive)
logic [15:0] reg_stride;            // Number of bytes per line

logic [25:0] next_line_address;       // Address at start of next line


logic        in_range_x;
logic        in_range_y;
logic [25:0] current_address;

assign in_range_x = (pixel_x >= reg_x1) && (pixel_x < reg_x2);
assign in_range_y = (pixel_y >= reg_y1) && (pixel_y < reg_y2);

always_ff @(posedge clock) begin

    if (new_frame) begin
        current_address   <= reg_address;
        next_line_address <= reg_address + {10'b0,reg_stride};
    
    end else if (next_line && in_range_y) begin
        current_address   <= next_line_address;
        next_line_address <= next_line_address + {10'b0, reg_stride};
    
    end else if (next_pixel && in_range_x && in_range_y) begin
        current_address <= current_address + 1'b1;
    end

    layer_active <= in_range_x && in_range_y;
    layer_address <= current_address;


    // Update registers on write
    // Layer 0 is fixed to cover the whole screen
    if (hwregs_write && hwregs_addr[7:5]==LAYER_ID) begin
        case (hwregs_addr[4:2])
            3'd0:                  reg_address <= hwregs_wdata[25:0]; // Base address
            3'd1: if (LAYER_ID!=0) reg_x1 <= hwregs_wdata[9:0];       // X1
            3'd2: if (LAYER_ID!=0) reg_y1 <= hwregs_wdata[9:0];       // Y1
            3'd3: if (LAYER_ID!=0) reg_x2 <= hwregs_wdata[9:0];       // X2
            3'd4: if (LAYER_ID!=0) reg_y2 <= hwregs_wdata[9:0];       // Y2
            3'd5: if (LAYER_ID!=0) reg_stride <= hwregs_wdata[15:0];   // Stride
            default: ; // Ignore others
        endcase
    end


    if (reset) begin
        if (LAYER_ID==0) begin
            reg_address <= 26'h3f80000; // Cover the whole screen
            reg_x1 <= 10'd0;
            reg_y1 <= 10'd0;
            reg_x2 <= 10'd640;
            reg_y2 <= 10'd480;
            reg_stride <= 16'd640;
        end else begin
            reg_address <= 26'h0;       // Disabled by default
            reg_x1 <= 10'd640;          // Set to invalid rectangle
            reg_y1 <= 10'd480;
            reg_x2 <= 10'd0;
            reg_y2 <= 10'd0;
            reg_stride <= 16'd0;
        end
    end 
end

wire unused_ok = &{1'b0, hwregs_wdata[31:26], hwregs_addr[1:0]}; // Ignore upper address bits

endmodule