`timescale 1ns/1ns

// VGA 2-bit Check Z Module
// Checks the Z value for a pixel is lower then the current Z buffer value

module vga2_chkz (
    input logic         clock,
    input logic         reset, 

    // Incoming request
    output logic         chkz_ready,       // Ready for next pixel
    input  logic         chkz_valid,       // Pixel data valid
    input  logic [9:0]   chkz_x,           // Pixel X coordinate
    input  logic [11:0]  chkz_z,           // Depth value
    input  logic [11:0]  chkz_u,           // Texture U coordinate / color
    input  logic [11:0]  chkz_v,           // Texture V coordinate / unused
    input  logic [4:0]   chkz_mode,        // Texture mode
    input  logic [31:0]  chkz_src_addr, 
    input  logic [31:0]  chkz_src_stride,

    input  logic [11:0]  fetched_z,         // Current Z buffer value

    // Outgoing response
    input  logic         pixsrc_ready,       // Ready for next pixel
    output logic         pixsrc_valid,       // Pixel data valid
    output logic [9:0]   pixsrc_x,           // Pixel X coordinate
    output logic [11:0]  pixsrc_z,           // Depth value
    output logic [11:0]  pixsrc_u,           // Texture U coordinate / color
    output logic [11:0]  pixsrc_v,            // Texture V coordinate / unused
    output logic [4:0]   pixsrc_mode,        // Texture mode
    output logic [31:0]  pixsrc_src_addr, 
    output logic [31:0]  pixsrc_src_stride
);

logic         valid;

always_comb begin
    // Determine if we need to pass on a valid transaction
    pixsrc_valid = valid && (chkz_z < fetched_z);

    // Determine if wa can accept a new transaction
    chkz_ready = pixsrc_ready || !pixsrc_valid;
end

always_ff @(posedge clock) begin
    // load the next transaction
    if (chkz_ready) begin
        valid   <= chkz_valid;
        pixsrc_x       <= chkz_x;
        pixsrc_z       <= chkz_z;
        pixsrc_u       <= chkz_u;
        pixsrc_v       <= chkz_v;
        pixsrc_mode    <= chkz_mode;
        pixsrc_src_addr<= chkz_src_addr;
        pixsrc_src_stride<= chkz_src_stride;
    end 
end

wire unused_ok = &{1'b0, reset}; // suppress unused signal warnings

endmodule

