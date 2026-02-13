`timescale 1ns/1ns

module vga2_output(
    input  logic         clock,
    input  logic         reset,

    // Connection to the scanline ram
    output logic         lineram_read,
    output logic [9:0]   lineram_addr,
    input  logic [23:0]  lineram_rdata,

    // Connections to the rendering logic
    output logic         start_of_line,
    output logic         start_of_frame,
    output logic [9:0]   scanline_y,

    // Connection to VGA pins
    output logic         VGA_CLK,
    output logic         VGA_HS,
    output logic         VGA_VS,
    output logic [7:0]   VGA_R,
    output logic [7:0]   VGA_G,
    output logic [7:0]   VGA_B
);

localparam H_VISIBLE       = 10'd640;
localparam H_FRONT_PORCH  = 10'd16;
localparam H_SYNC_PULSE   = 10'd96;
localparam H_BACK_PORCH   = 10'd48;
localparam H_TOTAL        = H_VISIBLE + H_FRONT_PORCH + H_SYNC_PULSE + H_BACK_PORCH; 
localparam V_VISIBLE       = 10'd480;
localparam V_FRONT_PORCH  = 10'd10;
localparam V_SYNC_PULSE   = 10'd2;
localparam V_BACK_PORCH   = 10'd33;
localparam V_TOTAL        = V_VISIBLE + V_FRONT_PORCH + V_SYNC_PULSE + V_BACK_PORCH;

logic [2:0] c_count, next_c_count;
logic [9:0] h_count, next_h_count;
logic [9:0] v_count, next_v_count;
logic [7:0] next_r;
logic [7:0] next_g;
logic [7:0] next_b;
logic       next_hs;
logic       next_vs;
logic       next_start_of_line;
logic       next_start_of_frame;

assign VGA_CLK = c_count[1];    // 25MHz clock
assign scanline_y = v_count;

always_comb begin
   // defaults
    next_c_count = (c_count==3'd4) ? 3'd0 : c_count + 3'd1;
    next_h_count = h_count;
    next_v_count = v_count;
    next_r  = VGA_R;
    next_g  = VGA_G;
    next_b  = VGA_B;
    next_hs = VGA_HS;
    next_vs = VGA_VS;
    lineram_read = 1'b0;
    lineram_addr = 10'dx; 

    if (c_count == 3'd4) begin
        // Advance horizontal counter
        if (h_count == H_TOTAL - 10'd1) begin
            next_h_count = 10'd0;
            // Advance vertical counter
            if (v_count == V_TOTAL - 10'd1)
                next_v_count = 10'd0;
            else 
                next_v_count = v_count + 10'd1;
        end else begin
            next_h_count = h_count + 10'd1;
        end

        // Generate Sync signals
        next_hs = (h_count >= H_VISIBLE + H_FRONT_PORCH) && (h_count < H_VISIBLE + H_FRONT_PORCH + H_SYNC_PULSE) ? 1'b0 : 1'b1;
        next_vs = (v_count >= V_VISIBLE + V_FRONT_PORCH) && (v_count < V_VISIBLE + V_FRONT_PORCH + V_SYNC_PULSE) ? 1'b0 : 1'b1;

        // Output pixel data during visible area
        if (h_count < H_VISIBLE && v_count < V_VISIBLE) begin
            next_r = lineram_rdata[23:16];
            next_g = lineram_rdata[15:8];
            next_b = lineram_rdata[7:0];
        end else begin
            next_r = 8'd0;
            next_g = 8'd0;
            next_b = 8'd0;
        end
    end

    // Read from line ram during visible area
    lineram_read = (c_count == 3'd2) && (h_count < H_VISIBLE) && (v_count < V_VISIBLE);
    lineram_addr = h_count;

    // pulse start_of_line at start of each visible line
    next_start_of_line = c_count==3'd0 && h_count==10'd0 && v_count < V_VISIBLE;
    next_start_of_frame = c_count==3'd0 && h_count==10'd0 && v_count==V_VISIBLE;

    // reset
    if (reset) begin
        next_c_count = 3'd0;
        next_h_count = 10'h278;
        next_v_count = -10'd1;
        next_r  = 8'd0;
        next_g  = 8'd0;
        next_b  = 8'd0;
        next_hs = 1'b1;
        next_vs = 1'b1;
        next_start_of_line = 1'b0;
    end
end


always_ff @(posedge clock) begin
    c_count <= next_c_count;
    h_count <= next_h_count;
    v_count <= next_v_count;
    VGA_R   <= next_r;
    VGA_G   <= next_g;
    VGA_B   <= next_b;
    VGA_HS  <= next_hs;
    VGA_VS  <= next_vs;
    start_of_line <= next_start_of_line;
    start_of_frame <= next_start_of_frame;
end


endmodule