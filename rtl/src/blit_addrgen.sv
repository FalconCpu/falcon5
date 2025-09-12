`timescale 1ns/1ns

`include "blit.vh"

module blit_addrgen(
    input logic         clock,
    input logic         p2_ready,        // Stall the pipeline 

    // Signals from coordinate generator
    input logic         p1_valid,       // 1 = write
    input logic [15:0]  p1_x,
    input logic [15:0]  p1_y,
    input logic [15:0]  p1_src_x,
    input logic [15:0]  p1_src_y,
    input logic [2:0]   p1_bit_index,
    input logic [4:0]   reg_command,   // Command being executed
    input logic [7:0]   reg_color,     // Solid color for RECT operation

    // Signals from register interface
    input logic [15:0]   reg_clip_x1,   // Clipping rectangle
    input logic [15:0]   reg_clip_y1,
    input logic [15:0]   reg_clip_x2,
    input logic [15:0]   reg_clip_y2,
    input logic [25:0]   dest_base_addr, // Base address of the bitmap in memory
    input logic [15:0]   dest_stride,    // Width of the screen in
    input logic [25:0]   src_base_addr, // Base address of the bitmap in memory
    input logic [15:0]   src_stride,    // Width of the screen in

    // Outputs to the next stage in the pipeline
    output logic        p2_valid, 
    output logic        p2_is_mem,
    output logic        p2_is_text,
    output logic [25:0] p2_dst_address,
    output logic [25:0] p2_src_address,
    output logic [2:0]  p2_bit_index
);

always_ff @(posedge clock) begin
    if (!p2_ready) begin
        // Hold current state
    end else begin
        p2_valid <= p1_valid;
        p2_bit_index <= p1_bit_index;
        // check clipping
        if (p1_x < reg_clip_x1 || p1_x >= reg_clip_x2 ||
            p1_y < reg_clip_y1 || p1_y >= reg_clip_y2) 
            p2_valid   <= 1'b0;
        p2_is_mem  <= (reg_command == `BLIT_COPY || reg_command==`BLIT_TEXT);
        p2_is_text <= (reg_command == `BLIT_TEXT);
        p2_dst_address <= (dest_base_addr + {10'h0,p1_x}) + (p1_y * dest_stride);
        
        if (reg_command == `BLIT_RECT) 
            p2_src_address <= {18'b0, reg_color}; // Solid color fill
        else // COPY operation
            p2_src_address <= (src_base_addr + {10'h0,p1_src_x}) + (p1_src_y * src_stride);
    end
end

endmodule