`timescale 1ns/1ns

// Draw commands
// 0 = NOP
// 1 = Draw rectangle
// 2 = Copy rectangle
// 3 = Text

`include "blit.vh"

module blit_coordinate_gen(
    input logic        clock,
    input logic        reset,   

    // Signals from register interface
    input logic          start,          // Pulsed to start operation. Registers below must then be stable until busy deasserts.
    input logic [4:0]    reg_command,    // 
    input logic signed [15:0]   reg_x1,         // Rectangle coordinates
    input logic signed [15:0]   reg_y1,
    input logic signed [15:0]   reg_x2,         // Exclusive upper bounds
    input logic signed [15:0]   reg_y2,
    input logic signed [15:0]   reg_src_x,      // Source X for copy operations
    input logic signed [15:0]   reg_src_y,      // Source Y for copy
    output logic         busy,
    output logic         ack,            // Pulsed when operation is started
    input logic          fifo_full,
    input logic [31:0]   reg_src_dx_x,   // Affine source increments
    input logic [31:0]   reg_src_dy_y,
    input logic [31:0]   reg_src_dy_x,
    input logic [31:0]   reg_src_dx_y,
    input logic [31:0]   reg_slope_x1,      // For triangle/trapezoid fills
    input logic [31:0]   reg_slope_x2,

    // Clipping rectangle
    input logic signed [15:0]   reg_clip_x1,   // Clipping rectangle
    input logic signed [15:0]   reg_clip_y1,
    input logic signed [15:0]   reg_clip_x2,
    input logic signed [15:0]   reg_clip_y2,

    // Outputs to the next stage in the pipeline
    input  logic         p2_ready,          // Stall the pipeline
    output logic [15:0]  p1_x,
    output logic [15:0]  p1_y,
    output logic [15:0]  p1_src_x,
    output logic [15:0]  p1_src_y,
    output logic [2:0]   p1_bit_index,
    output logic         p1_valid
);

wire signed [15:0] p1_x_inc = p1_x + 1'b1;
wire signed [15:0] p1_y_inc = p1_y + 1'b1;

logic [31:0] src_x, src_y;      // Current source coordinates in 16.16 fixed point
logic [31:0] src_x0, src_y0;    // Base source coordinates in 16.16 fixed point
logic signed [31:0] left_edge_x, right_edge_x; // For triangle/trapezoid fills

assign p1_src_x = src_x[31:16];
assign p1_src_y = src_y[31:16];

always_ff @(posedge clock) begin
    ack <= 1'b0;

    if (!p2_ready) begin
        // Hold current state

    end else if (fifo_full) begin
        // Hold current state, but don't assert write
        p1_valid <= 1'b0;

    end else if (busy) begin
        p1_valid <= 1'b1;
        p1_x <= p1_x_inc;
        if (reg_command==`BLIT_TEXT) begin
            p1_bit_index <= p1_bit_index + 1'b1;
            if (p1_bit_index == 3'h7)
                src_x <= src_x + 32'h00010000; // Advance source X by 1.0
        end else
            p1_bit_index <= 3'h0;
            
        if (reg_command==`BLIT_COPY) begin
            src_x <= src_x + reg_src_dx_x;
            src_y <= src_y + reg_src_dy_x;
        end

        if (p1_x_inc >= $signed(right_edge_x[31:16]) || p1_x_inc>=reg_clip_x2  || p1_y<reg_clip_y1) begin
            // Reached End of scanline - move on to next line

            // If clipped left, adjust start X. Don't do this for COPY operations as it would desync source/dest
            if (left_edge_x[31:16] < reg_clip_x1 && reg_command!=`BLIT_COPY)
                p1_x <= reg_clip_x1;
            else
                p1_x <= left_edge_x[31:16];
            p1_y <= p1_y_inc;
            src_x <= src_x0 + reg_src_dx_y;
            src_y <= src_y0 + reg_src_dy_y;
            src_x0 <= src_x0 + reg_src_dx_y;
            src_y0 <= src_y0 + reg_src_dy_y;
            left_edge_x <= left_edge_x + reg_slope_x1;
            right_edge_x <= right_edge_x + reg_slope_x2;
            if (p1_y_inc >= reg_y2 || p1_y_inc >=reg_clip_y2) begin
                // Finished
                busy <= 1'b0;
                p1_valid <= 1'b0;
                p1_x <= 16'hx;
                p1_y <= 16'hx;
            end
        end

    end else if (start) begin
        // Start new operation
        ack <= 1'b1;
        if (reg_command == `BLIT_RECT || reg_command == `BLIT_COPY || reg_command==`BLIT_TEXT) begin
            // Rectangle
            if (reg_x1 < reg_x2 && reg_y1 < reg_y2) begin
                p1_x <= reg_x1;
                p1_y <= reg_y1;
                src_x <= {reg_src_x,16'b0}; // Convert to 16.16 fixed point
                src_y <= {reg_src_y,16'b0};
                src_x0 <= {reg_src_x,16'b0};
                src_y0 <= {reg_src_y,16'b0};
                left_edge_x <= {reg_x1,16'b0}; // + reg_slope_x1/2;
                right_edge_x <= {reg_x2,16'b0}; // + reg_slope_x2/2;
                p1_bit_index <= 3'h0;
                p1_valid <= 1'b1;
                busy <= 1'b1;
            end        
        end else begin
            $display("BLIT_DRAW: Unknown command %x", reg_command);
        end
    end

    if (reset) begin
        busy <= 1'b0;
        p1_valid <= 1'b0;
    end
end

endmodule