`timescale 1ns/1ns

module vga_counter(
    input        clock,
    input        reset,
    input        new_frame,
    
    input        fifo_full,
    input        cache_ready,

    output logic [9:0]   pixel_x,       // Current pixel X coordinate
    output logic [9:0]   pixel_y,       // Current pixel Y coordinate
    output logic         next_pixel,    // Pulsed when moving to next pixel
    output logic         next_line      // Pulsed when moving to next line
);

logic      count;
localparam SIZE_X = 640;
localparam SIZE_Y = 480;

always_ff @(posedge clock) begin
    next_pixel <= 1'b0;
    next_line  <= 1'b0;
    count <= count + 1'b1;              // Divide clock by 2

    if (reset || new_frame) begin
        pixel_x <= 0;
        pixel_y <= 0;
        next_pixel <= 1'b1;
        next_line <= 1'b0;
        count <= 0;

    end else if (pixel_y < SIZE_Y && !fifo_full && cache_ready && count==0) begin
        next_pixel <= 1'b1;
        pixel_x <= pixel_x + 1'b1;
        if (pixel_x == SIZE_X-1) begin
            pixel_x <= 0;
            pixel_y <= pixel_y + 1'b1;
            next_line <= 1'b1;
        end
    end
end

endmodule