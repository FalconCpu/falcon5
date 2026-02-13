`timescale 1ns/1ns

// VGA2 Pixel Source Generator Module
//
// Based on the `mode` bit, either :-
//    generates memory addresses for pixel fetches
//    Or calculate solid color values by treating the addr/stride fields as color components and interpolating between them.

module vga2_pixsrc (
    input logic         clock,
    input logic         reset, 

    // Incoming request
    output logic         pixsrc_ready,       // Ready for next pixel
    input  logic         pixsrc_valid,       // Pixel data valid
    input  logic [9:0]   pixsrc_x,           // Pixel X coordinate
    input  logic [11:0]  pixsrc_z,           // Depth value
    input  logic [11:0]  pixsrc_u,           // Texture U coordinate / color
    input  logic [11:0]  pixsrc_v,            // Texture V coordinate / unused
    input  logic [4:0]   pixsrc_mode,        // Texture mode
    input  logic [31:0]  pixsrc_src_addr, 
    input  logic [31:0]  pixsrc_src_stride,

    // Outgoing response
    input  logic         memread_ready,       // Ready for next pixel
    output logic         memread_valid,       // Pixel data valid
    output logic [9:0]   memread_x,           // Pixel X coordinate
    output logic [11:0]  memread_z,           // Depth value
    output logic [4:0]   memread_mode,        // Texture mode
    output logic [25:0]  memread_addr         // Pixel memory address, or solid color value
);

// Latched values
logic         valid;
logic [9:0]   x;
logic [11:0]  z;
logic [11:0]  u;
logic [11:0]  v;
logic [4:0]   mode;
logic [31:0]  src_addr;
logic [31:0]  src_stride;

logic [15:0] interpoated_red;
logic [15:0] interpoated_green;
logic [15:0] interpoated_blue;
logic [15:0] interpolate_a;


always_comb begin
    // Default outputs
    memread_x     = x;
    memread_z     = z;
    memread_mode  = mode;
    interpoated_red   = 8'hx;
    interpoated_green = 8'hx;
    interpoated_blue  = 8'hx;
	interpolate_a = 16'hx;

    // Determine if we need to pass on a valid transaction
    memread_valid = valid;

    // Determine if wa can accept a new transaction
    pixsrc_ready = memread_ready || !memread_valid;


    // Calculate memory address / solid color based on mode
    if (mode[0] == 1'b0) begin
        // Texture fetch mode
        memread_addr = src_addr[25:0] + (src_stride[15:0] * v) + {14'b0,u};
    end else begin
        // Solid color mode
        interpolate_a = {8'b0, u[7:0]};
        interpoated_red   = ((src_addr[23:16] * interpolate_a) + (src_stride[23:16] * ~interpolate_a));
        interpoated_green = ((src_addr[15:8]  * interpolate_a) + (src_stride[15:8]  * ~interpolate_a));
        interpoated_blue  = ((src_addr[7:0]   * interpolate_a) + (src_stride[7:0]   * ~interpolate_a));
        memread_addr = {2'b0, interpoated_red[15:8], interpoated_green[15:8], interpoated_blue[15:8]};
    end
end

always_ff @(posedge clock) begin
    // load the next transaction
    if (pixsrc_ready) begin
        valid       <= pixsrc_valid;
        x           <= pixsrc_x;
        z           <= pixsrc_z;
        u           <= pixsrc_u;
        v           <= pixsrc_v;
        mode        <= pixsrc_mode;
        src_addr    <= pixsrc_src_addr;
        src_stride  <= pixsrc_src_stride;
    end 

    if (reset) begin
        valid <= 1'b0;
    end
end

wire unused_ok = &{1'b0, src_addr[31:24], src_stride[31:24]}; // suppress unused signal warnings
endmodule