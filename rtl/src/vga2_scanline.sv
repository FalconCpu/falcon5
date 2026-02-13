`timescale 1ns/1ns

// Decompose a scanline fragment into a series of pixels
// and send to the Z buffer for depth testing.
//
// Input fragment is defined by left and right edge X coordinates (inclusive/exclusive),
// and starting values and slopes for Z, U and V.
// Inside this module we work in 16.16 fixed point for X,Z,U,V to allow sub-pixel accuracy.
// But handoff only the integer parts to the Z buffer.

module vga2_scanline(
    input  logic         clock,
    input  logic         reset,
    
    // Interface to object processor
    output logic         scanline_ready,    // Ready for next fragment
    input  logic         scanline_valid,    // Fragment data valid
    input  logic [15:0]  scanline_x1,       // Left edge X coordinate (inclusive) (16.16 fixed point)
    input  logic [15:0]  scanline_x2,       // Right edge X coordinate (exclusive)
    input  logic [31:0]  scanline_z,        // Depth value at left edge
    input  logic [31:0]  scanline_dzdx,     // Depth slope in X
    input  logic [31:0]  scanline_u,        // Texture U coordinate / color at left edge
    input  logic [31:0]  scanline_dudx,     // Texture U slope in X
    input  logic [31:0]  scanline_v,        // Texture V coordinate / unused at left edge
    input  logic [31:0]  scanline_dvdx,     // Texture V slope in X

    // Interface to pixel Z buffer
    input  logic         zbuf_ready,       // Ready for next pixel
    output logic         zbuf_valid,       // Pixel data valid
    output logic [9:0]   zbuf_x,           // Pixel X coordinate
    output logic [15:0]  zbuf_z,           // Depth value
    output logic [15:0]  zbuf_u,           // Texture U coordinate / color
    output logic [15:0]  zbuf_v            // Texture V coordinate / unused
);

endmodule