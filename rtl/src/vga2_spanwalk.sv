`timescale 1ns/1ns

// Decompose a scanline fragment into a series of pixels
// and send to the Z buffer for depth testing.
//
// Input fragment is defined by left and right edge X coordinates (inclusive/exclusive),
// and starting values and slopes for Z, U and V.
// Inside this module we work in 16.16 fixed point for X,Z,U,V to allow sub-pixel accuracy.
// But handoff only the integer parts to the Z buffer.

module vga2_spanwalk(
    input  logic         clock,
    input  logic         reset,
    
    // Interface to object processor
    output logic         span_ready,    // Ready for next fragment
    input  logic         span_valid,    // Fragment data valid
    input  logic [11:0]  span_x1,       // Left edge X coordinate (inclusive) (integer only)
    input  logic [11:0]  span_x2,       // Right edge X coordinate (exclusive)
    input  logic [23:0]  span_z,        // Depth value at left edge (16.16 fixed point)
    input  logic [23:0]  span_dzdx,     // Depth slope in X
    input  logic [23:0]  span_u,        // Texture U coordinate / color at left edge
    input  logic [23:0]  span_dudx,     // Texture U slope in X
    input  logic [23:0]  span_v,        // Texture V coordinate / unused at left edge
    input  logic [23:0]  span_dvdx,     // Texture V slope in X
    input  logic [4:0]   span_mode,    // Texture mode
    input  logic [31:0]  span_src_addr,
    input  logic [31:0]  span_src_stride,

    // Interface to pixel Z buffer
    input  logic         chkz_ready,       // Ready for next pixel
    output logic         chkz_valid,       // Pixel data valid
    output logic [9:0]   chkz_x,           // Pixel X coordinate
    output logic [11:0]  chkz_z,           // Depth value
    output logic [11:0]  chkz_u,           // Texture U coordinate / color
    output logic [11:0]  chkz_v,            // Texture V coordinate / unused
    output logic [4:0]   chkz_mode,        // Texture mode
    output logic [31:0]  chkz_src_addr, 
    output logic [31:0]  chkz_src_stride
);

logic         valid, next_valid;
logic [11:0]  x1,    next_x1;
logic [11:0]  x2,    next_x2;
logic [23:0]  z,     next_z;
logic [23:0]  dzdx,  next_dzdx;
logic [23:0]  u,     next_u;
logic [23:0]  dudx,  next_dudx;
logic [23:0]  v,     next_v;
logic [23:0]  dvdx,  next_dvdx;
logic [4:0]          next_mode;
logic [31:0]         next_src_addr;
logic [31:0]         next_src_stride;

assign chkz_x = x1[9:0];
assign chkz_z = z[23:12];   // Convert from 16.16 to 16.0 fixed point
assign chkz_u = u[23:12];
assign chkz_v = v[23:12];   

always_comb begin
    // defaults
    next_valid = valid;
    next_x1    = x1;
    next_x2    = x2;
    next_z     = z;
    next_dzdx  = dzdx;
    next_u     = u;
    next_dudx  = dudx;
    next_v     = v;
    next_dvdx  = dvdx;
    next_mode      = chkz_mode;
    next_src_addr  = chkz_src_addr;
    next_src_stride= chkz_src_stride;
    chkz_valid = 1'b0;
    span_ready = 1'b0;

    if (!valid) begin
        // Not currently processing a fragment
        span_ready = 1'b1;

    end else begin
        // Currently processing a fragment
        chkz_valid = 1'b1;
        if (chkz_ready) begin
            // Move onto next pixel
            next_x1   = x1 + 1'b1;
            next_z     = z + dzdx;
            next_u     = u + dudx;
            next_v     = v + dvdx;
            if (next_x1 >= x2) 
                // End of fragment
                span_ready = 1'b1;
        end
    end

    // Accept new fragment
    if (span_ready) begin
        next_valid = span_valid;
        next_x1    = span_x1;
        next_x2    = span_x2;
        next_z     = span_z;
        next_dzdx  = span_dzdx;
        next_u     = span_u;
        next_dudx  = span_dudx;
        next_v     = span_v;
        next_dvdx  = span_dvdx;
        next_mode      = span_mode;
        next_src_addr  = span_src_addr;
        next_src_stride= span_src_stride;
    end

    if (reset) begin
        next_valid = 1'b0;
    end
end

always_ff @(posedge clock) begin
    valid       <= next_valid;
    x1          <= next_x1;
    x2          <= next_x2;
    z           <= next_z;
    dzdx        <= next_dzdx;
    u           <= next_u;
    dudx        <= next_dudx;
    v           <= next_v;
    dvdx        <= next_dvdx;
    chkz_mode        <= next_mode;
    chkz_src_addr    <= next_src_addr;
    chkz_src_stride  <= next_src_stride;
end



endmodule